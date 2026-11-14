package com.example.ui.map

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.example.model.MapVisualMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import kotlin.math.*

/**
 * Free, zero-API-key Web Mercator projection math and slippy-tile loader.
 * Supports CartoDB Dark Matter, Esri World Imagery Satellite, OpenStreetMap,
 * and automatic Built-in Offline Vector fallback.
 */
data class MercatorViewport(
    val centerLat: Double,
    val centerLng: Double,
    val zoom: Double,
    val widthPx: Float,
    val heightPx: Float,
    val worldSizePxOverride: Double? = null
) {
    companion object {
        const val TILE_SIZE = 256.0
        private const val EARTH_CIRCUMFERENCE_METERS = 40075016.686
    }

    private fun scaleFor(z: Double): Double =
        if (z == zoom && worldSizePxOverride != null && worldSizePxOverride > 0.0) {
            worldSizePxOverride
        } else {
            TILE_SIZE * 2.0.pow(z)
        }

    fun latLngToWorldPixel(lat: Double, lng: Double, z: Double = zoom): Pair<Double, Double> {
        val scale = scaleFor(z)
        val clampedLat = lat.coerceIn(-85.05112878, 85.05112878)
        val x = ((lng + 180.0) / 360.0) * scale
        val sinLat = sin(Math.toRadians(clampedLat))
        val y = (0.5 - ln((1.0 + sinLat) / (1.0 - sinLat)) / (4.0 * PI)) * scale
        return x to y
    }

    fun worldPixelToLatLng(wx: Double, wy: Double, z: Double = zoom): Pair<Double, Double> {
        val scale = scaleFor(z)
        val lng = (wx / scale) * 360.0 - 180.0
        val n = PI - 2.0 * PI * (wy / scale)
        val lat = Math.toDegrees(atan(sinh(n)))
        return lat.coerceIn(-85.0, 85.0) to ((lng + 540.0) % 360.0 - 180.0)
    }

    fun latLngToScreen(lat: Double, lng: Double): Offset {
        val (cx, cy) = latLngToWorldPixel(centerLat, centerLng, zoom)
        val (px, py) = latLngToWorldPixel(lat, lng, zoom)
        return Offset(
            x = (widthPx / 2.0 + (px - cx)).toFloat(),
            y = (heightPx / 2.0 + (py - cy)).toFloat()
        )
    }

    fun screenToLatLng(screenX: Float, screenY: Float): Pair<Double, Double> {
        val (cx, cy) = latLngToWorldPixel(centerLat, centerLng, zoom)
        val wx = cx + (screenX - widthPx / 2.0)
        val wy = cy + (screenY - heightPx / 2.0)
        return worldPixelToLatLng(wx, wy, zoom)
    }

    fun metersToPixels(meters: Double, lat: Double = centerLat): Float {
        val scale = scaleFor(zoom)
        val metersPerPx = (cos(Math.toRadians(lat)) * EARTH_CIRCUMFERENCE_METERS) / scale
        return if (metersPerPx > 0.0) (meters / metersPerPx).toFloat() else 0f
    }

    fun panByScreenDelta(dx: Float, dy: Float): Pair<Double, Double> {
        val (cx, cy) = latLngToWorldPixel(centerLat, centerLng, zoom)
        return worldPixelToLatLng(cx - dx, cy - dy, zoom)
    }
}

enum class FreeMapProvider(val label: String, val attribution: String) {
    CARTO_DARK("CartoDB Dark Matter", "CARTO / OpenStreetMap (Free)"),
    ESRI_SATELLITE("Esri Orbital Satellite", "Esri World Imagery (Free)"),
    OSM_STANDARD("OpenStreetMap Street", "OpenStreetMap (Free)"),
    BUILT_IN_OFFLINE("Built-In Offline Vector", "ISAC Built-In Vector Cartography")
}

data class TileCoord(val z: Int, val x: Int, val y: Int, val provider: FreeMapProvider) {
    val cacheKey: String get() = "${provider.name}_${z}_${x}_${y}"

    fun url(): String? {
        return when (provider) {
            FreeMapProvider.CARTO_DARK -> "https://basemaps.cartocdn.com/dark_all/$z/$x/$y.png"
            FreeMapProvider.ESRI_SATELLITE -> "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/$z/$y/$x"
            FreeMapProvider.OSM_STANDARD -> "https://tile.openstreetmap.org/$z/$x/$y.png"
            FreeMapProvider.BUILT_IN_OFFLINE -> null
        }
    }
}

object TacticalTileRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private val cache = LruCache<String, ImageBitmap>(140)
    private val inFlightKeys = mutableSetOf<String>()
    private val failedKeys = mutableSetOf<String>()

    fun getCachedTile(coord: TileCoord): ImageBitmap? = cache.get(coord.cacheKey)

    fun isTilePendingOrFailed(coord: TileCoord): Boolean {
        val key = coord.cacheKey
        return synchronized(inFlightKeys) { inFlightKeys.contains(key) || failedKeys.contains(key) }
    }

    suspend fun fetchTile(coord: TileCoord): ImageBitmap? {
        val key = coord.cacheKey
        cache.get(key)?.let { return it }

        val url = coord.url() ?: return null
        val shouldFetch = synchronized(inFlightKeys) {
            if (inFlightKeys.contains(key) || failedKeys.contains(key)) {
                false
            } else {
                inFlightKeys.add(key)
                true
            }
        }
        if (!shouldFetch) return null

        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "AegisEyeTactical/1.0 (Android Crisis Navigation)")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        synchronized(inFlightKeys) { failedKeys.add(key) }
                        return@withContext null
                    }
                    val bytes = response.body?.bytes() ?: return@withContext null
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    val imageBitmap = bitmap?.asImageBitmap()
                    if (imageBitmap != null) {
                        cache.put(key, imageBitmap)
                    }
                    imageBitmap
                }
            } catch (_: Exception) {
                synchronized(inFlightKeys) { failedKeys.add(key) }
                null
            } finally {
                synchronized(inFlightKeys) { inFlightKeys.remove(key) }
            }
        }
    }

    fun providerForMode(mode: MapVisualMode, offlineOnly: Boolean): FreeMapProvider {
        if (offlineOnly) return FreeMapProvider.BUILT_IN_OFFLINE
        return when (mode) {
            MapVisualMode.TACTICAL_HUD -> FreeMapProvider.CARTO_DARK
            MapVisualMode.SATELLITE_RECON -> FreeMapProvider.ESRI_SATELLITE
            MapVisualMode.FLIR_THERMAL -> FreeMapProvider.CARTO_DARK
        }
    }
}
