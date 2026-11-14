package com.example.ui.map

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.modules.SqlTileWriter
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.MapTileIndex
import java.io.ByteArrayInputStream
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.*

data class TileCacheStats(
    val usedBytes: Long,
    val maxBytes: Long,
    val cachedFileCount: Int,
    val estimatedTileCount: Long,
    val cachePath: String,
    val retentionDays: Int = 30
) {
    val usedMb: Double get() = usedBytes / (1024.0 * 1024.0)
    val maxMb: Double get() = maxBytes / (1024.0 * 1024.0)
    val isOfflineReady: Boolean get() = usedBytes > 0L
}

data class TilePrefetchProgress(
    val downloadedCount: Int = 0,
    val skippedCachedCount: Int = 0,
    val failedCount: Int = 0,
    val totalCount: Int = 0,
    val isRunning: Boolean = false,
    val isComplete: Boolean = false,
    val statusMessage: String = "IDLE"
) {
    val completedCount: Int get() = downloadedCount + skippedCachedCount + failedCount
    val progressFraction: Float
        get() = if (totalCount > 0) (completedCount.toFloat() / totalCount.toFloat()).coerceIn(0f, 1f) else 0f
}

/**
 * Manages OsmDroid's local SQLite disk cache (`SqlTileWriter` + `osmdroidTileCache`),
 * monitors device network connectivity for automatic offline failover, and supports
 * pre-caching tactical map sectors so views stay accessible without connectivity.
 */
object OsmTileDiskCacheManager {

    private const val RETENTION_DAYS = 30
    private const val RETENTION_MS = RETENTION_DAYS * 24L * 60L * 60L * 1000L
    private const val DEFAULT_MAX_CACHE_MB = 250L
    private const val MAX_PREFETCH_TILES_PER_RUN = 280

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    fun configureCachePolicy(context: Context, maxCacheMb: Long = DEFAULT_MAX_CACHE_MB) {
        OsmDroidConfig.ensureInitialized(context)
        val config = Configuration.getInstance()
        val maxBytes = maxCacheMb * 1024L * 1024L
        config.tileFileSystemCacheMaxBytes = maxBytes
        config.tileFileSystemCacheTrimBytes = (maxBytes * 85L) / 100L
        config.expirationOverrideDuration = RETENTION_MS
        config.tileDownloadThreads = 4
        config.tileFileSystemThreads = 4
    }

    fun isNetworkCurrentlyAvailable(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun observeNetworkConnectivity(context: Context): Flow<Boolean> = callbackFlow {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (cm == null) {
            trySend(true)
            close()
            return@callbackFlow
        }

        trySend(isNetworkCurrentlyAvailable(context))

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(true)
            }

            override fun onLost(network: Network) {
                trySend(isNetworkCurrentlyAvailable(context))
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                trySend(capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        try {
            cm.registerNetworkCallback(request, callback)
        } catch (_: Exception) {
            trySend(isNetworkCurrentlyAvailable(context))
        }

        awaitClose {
            try {
                cm.unregisterNetworkCallback(callback)
            } catch (_: Exception) {
            }
        }
    }.distinctUntilChanged()

    suspend fun inspectCache(context: Context): TileCacheStats = withContext(Dispatchers.IO) {
        configureCachePolicy(context)
        val config = Configuration.getInstance()
        val cacheDir = config.osmdroidTileCache ?: File(context.cacheDir, "osmdroid/tiles")

        var totalBytes = 0L
        var fileCount = 0
        if (cacheDir.exists()) {
            cacheDir.walkTopDown().filter { it.isFile }.forEach { file ->
                totalBytes += file.length()
                fileCount++
            }
        }

        val estimatedTiles = querySqlTileCount(cacheDir) ?: (totalBytes / 14_000L).coerceAtLeast(0L)

        TileCacheStats(
            usedBytes = totalBytes,
            maxBytes = config.tileFileSystemCacheMaxBytes,
            cachedFileCount = fileCount,
            estimatedTileCount = estimatedTiles,
            cachePath = cacheDir.absolutePath,
            retentionDays = RETENTION_DAYS
        )
    }

    private fun querySqlTileCount(cacheDir: File): Long? {
        val dbFile = File(cacheDir, "cache.db")
        if (!dbFile.exists() || dbFile.length() == 0L) return null
        return try {
            val db = android.database.sqlite.SQLiteDatabase.openDatabase(
                dbFile.absolutePath,
                null,
                android.database.sqlite.SQLiteDatabase.OPEN_READONLY
            )
            db.use {
                it.rawQuery("SELECT COUNT(*) FROM tiles", null).use { cursor ->
                    if (cursor.moveToFirst()) cursor.getLong(0) else 0L
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Computes exact OsmDroid [MapTileIndex] keys covering [bounds] across [minZoom..maxZoom].
     * Pure coordinate math capped at [maxTiles] so pre-caching remains responsive and bounded.
     */
    fun calculateTileIndicesForBounds(
        bounds: BoundingBox,
        minZoom: Int,
        maxZoom: Int,
        maxTiles: Int = MAX_PREFETCH_TILES_PER_RUN
    ): List<Long> {
        val indices = mutableListOf<Long>()
        val safeMinZ = minZoom.coerceIn(2, 17)
        val safeMaxZ = maxZoom.coerceIn(safeMinZ, 17)

        for (z in safeMinZ..safeMaxZ) {
            val (x1, y1) = latLngToTileXY(bounds.latNorth, bounds.lonWest, z)
            val (x2, y2) = latLngToTileXY(bounds.latSouth, bounds.lonEast, z)
            val startX = min(x1, x2)
            val endX = max(x1, x2)
            val startY = min(y1, y2)
            val endY = max(y1, y2)

            for (x in startX..endX) {
                for (y in startY..endY) {
                    indices.add(MapTileIndex.getTileIndex(z, x, y))
                    if (indices.size >= maxTiles) return indices
                }
            }
        }
        return indices
    }

    private fun latLngToTileXY(lat: Double, lng: Double, zoom: Int): Pair<Int, Int> {
        val n = 2.0.pow(zoom)
        val clampedLat = lat.coerceIn(-85.05112878, 85.05112878)
        val latRad = Math.toRadians(clampedLat)
        val x = floor(((lng + 180.0) / 360.0) * n).toInt().coerceIn(0, n.toInt() - 1)
        val y = floor(((1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0) * n)
            .toInt().coerceIn(0, n.toInt() - 1)
        return x to y
    }

    suspend fun prefetchRegionToDisk(
        context: Context,
        tileSource: OnlineTileSourceBase,
        bounds: BoundingBox,
        minZoom: Int,
        maxZoom: Int,
        onProgress: (TilePrefetchProgress) -> Unit
    ): TilePrefetchProgress = withContext(Dispatchers.IO) {
        configureCachePolicy(context)

        val tileIndices = calculateTileIndicesForBounds(bounds, minZoom, maxZoom)
        if (tileIndices.isEmpty()) {
            return@withContext TilePrefetchProgress(
                isRunning = false,
                isComplete = true,
                statusMessage = "NO TILES IN REGION"
            )
        }

        val writer = SqlTileWriter()
        var downloaded = 0
        var skipped = 0
        var failed = 0
        val total = tileIndices.size
        val expirationMs = System.currentTimeMillis() + RETENTION_MS

        onProgress(
            TilePrefetchProgress(
                totalCount = total,
                isRunning = true,
                statusMessage = "CACHING $total TILES TO DISK..."
            )
        )

        try {
            for ((idx, tileIndex) in tileIndices.withIndex()) {
                val alreadyExists = try {
                    writer.exists(tileSource, tileIndex)
                } catch (_: Exception) {
                    false
                }

                if (alreadyExists) {
                    skipped++
                } else {
                    val url = tileSource.getTileURLString(tileIndex)
                    val saved = downloadAndWriteTile(writer, tileSource, tileIndex, url, expirationMs)
                    if (saved) downloaded++ else failed++
                }

                if ((idx + 1) % 4 == 0 || idx == tileIndices.lastIndex) {
                    onProgress(
                        TilePrefetchProgress(
                            downloadedCount = downloaded,
                            skippedCachedCount = skipped,
                            failedCount = failed,
                            totalCount = total,
                            isRunning = idx < tileIndices.lastIndex,
                            isComplete = idx == tileIndices.lastIndex,
                            statusMessage = if (idx == tileIndices.lastIndex) {
                                "OFFLINE CACHE READY: $downloaded NEW, $skipped EXISTING"
                            } else {
                                "CACHING ${idx + 1}/$total..."
                            }
                        )
                    )
                }
            }
        } finally {
            try {
                writer.onDetach()
            } catch (_: Exception) {
            }
        }

        TilePrefetchProgress(
            downloadedCount = downloaded,
            skippedCachedCount = skipped,
            failedCount = failed,
            totalCount = total,
            isRunning = false,
            isComplete = true,
            statusMessage = "OFFLINE CACHE READY: $downloaded NEW, $skipped EXISTING"
        )
    }

    private fun downloadAndWriteTile(
        writer: SqlTileWriter,
        tileSource: OnlineTileSourceBase,
        tileIndex: Long,
        url: String,
        expirationMs: Long
    ): Boolean {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "AegisEyeTactical/1.0 (Android Crisis Navigation)")
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return false
                val bytes = response.body?.bytes() ?: return false
                if (bytes.isEmpty()) return false
                ByteArrayInputStream(bytes).use { stream ->
                    writer.saveFile(tileSource, tileIndex, stream, expirationMs)
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    suspend fun clearDiskCache(context: Context): Boolean = withContext(Dispatchers.IO) {
        configureCachePolicy(context)
        var ok = true
        try {
            val writer = SqlTileWriter()
            try {
                writer.purgeCache()
            } finally {
                writer.onDetach()
            }
        } catch (_: Exception) {
            ok = false
        }

        val cacheDir = Configuration.getInstance().osmdroidTileCache
        if (cacheDir != null && cacheDir.exists()) {
            cacheDir.listFiles()?.forEach { child ->
                if (child.name != "cache.db") {
                    child.deleteRecursively()
                }
            }
        }
        ok
    }
}
