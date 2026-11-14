package com.example.ui.map

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.MapTileProviderArray
import org.osmdroid.tileprovider.MapTileProviderBase
import org.osmdroid.tileprovider.modules.MapTileModuleProviderBase
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.MapTileIndex
import java.io.File

/**
 * Configures OsmDroid strictly with app-private directories (filesDir / cacheDir)
 * so zero external storage permissions are required (Play Policy compliant),
 * and provides free non-API-key tile sources plus an offline vector-grid generator.
 */
object OsmDroidConfig {

    @Volatile
    private var initialized = false

    fun ensureInitialized(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val appCtx = context.applicationContext
            val config = Configuration.getInstance()
            config.userAgentValue = "AegisEyeTactical/1.0 (Android Crisis Navigation)"
            val baseDir = File(appCtx.filesDir, "osmdroid").apply { mkdirs() }
            val cacheDir = File(appCtx.cacheDir, "osmdroid/tiles").apply { mkdirs() }
            config.osmdroidBasePath = baseDir
            config.osmdroidTileCache = cacheDir
            config.load(appCtx, appCtx.getSharedPreferences("osmdroid_tactical_prefs", Context.MODE_PRIVATE))
            initialized = true
        }
    }

    val CARTO_DARK_SOURCE: OnlineTileSourceBase = object : OnlineTileSourceBase(
        "CartoDBDarkMatter",
        2,
        18,
        256,
        ".png",
        arrayOf("https://basemaps.cartocdn.com/dark_all/")
    ) {
        override fun getTileURLString(pMapTileIndex: Long): String {
            val z = MapTileIndex.getZoom(pMapTileIndex)
            val x = MapTileIndex.getX(pMapTileIndex)
            val y = MapTileIndex.getY(pMapTileIndex)
            return "$baseUrl$z/$x/$y$mImageFilenameEnding"
        }
    }

    val ESRI_SATELLITE_SOURCE: OnlineTileSourceBase = object : OnlineTileSourceBase(
        "EsriWorldImagery",
        2,
        18,
        256,
        "",
        arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/")
    ) {
        override fun getTileURLString(pMapTileIndex: Long): String {
            val z = MapTileIndex.getZoom(pMapTileIndex)
            val x = MapTileIndex.getX(pMapTileIndex)
            val y = MapTileIndex.getY(pMapTileIndex)
            return "$baseUrl$z/$y/$x"
        }
    }

    val OFFLINE_VECTOR_GRID_SOURCE: ITileSource = XYTileSource(
        "IsacOfflineVectorGrid",
        2,
        18,
        256,
        ".png",
        arrayOf()
    )

    fun tileSourceFor(provider: FreeMapProvider): ITileSource = when (provider) {
        FreeMapProvider.CARTO_DARK -> CARTO_DARK_SOURCE
        FreeMapProvider.ESRI_SATELLITE -> ESRI_SATELLITE_SOURCE
        FreeMapProvider.OSM_STANDARD -> TileSourceFactory.MAPNIK
        FreeMapProvider.BUILT_IN_OFFLINE -> OFFLINE_VECTOR_GRID_SOURCE
    }

    fun createOfflineProceduralProvider(context: Context): MapTileProviderBase {
        val module = OfflineVectorGridModuleProvider(context.resources)
        return MapTileProviderArray(
            OFFLINE_VECTOR_GRID_SOURCE,
            null,
            arrayOf(module)
        )
    }

    private class OfflineVectorGridModuleProvider(
        private val resources: Resources
    ) : MapTileModuleProviderBase(2, 32) {

        private var tileSource: ITileSource = OFFLINE_VECTOR_GRID_SOURCE

        private val bgPaint = Paint().apply {
            color = Color.parseColor("#0B0E14")
            style = Paint.Style.FILL
        }
        private val majorGridPaint = Paint().apply {
            color = Color.parseColor("#1D2A3A")
            strokeWidth = 1.5f
            style = Paint.Style.STROKE
        }
        private val minorGridPaint = Paint().apply {
            color = Color.parseColor("#121A24")
            strokeWidth = 1f
            style = Paint.Style.STROKE
        }
        private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#2A3B4F")
            textSize = 18f
        }

        override fun getUsesDataConnection(): Boolean = false
        override fun getName(): String = "ISAC Offline Vector Base"
        override fun getThreadGroupName(): String = "isac-offline-vector"
        override fun getTileLoader(): TileLoader = OfflineTileLoader()
        override fun getMinimumZoomLevel(): Int = 2
        override fun getMaximumZoomLevel(): Int = 18
        override fun setTileSource(pTileSource: ITileSource) {
            tileSource = pTileSource
        }

        private inner class OfflineTileLoader : TileLoader() {
            override fun loadTile(pMapTileIndex: Long): Drawable {
                val size = 256
                val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), bgPaint)

                // Sub-grid divisions
                for (i in 1..3) {
                    val pos = i * (size / 4f)
                    canvas.drawLine(pos, 0f, pos, size.toFloat(), minorGridPaint)
                    canvas.drawLine(0f, pos, size.toFloat(), pos, minorGridPaint)
                }
                // Tile border
                canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), majorGridPaint)

                // Center tactical reticle tick
                val half = size / 2f
                canvas.drawLine(half - 6f, half, half + 6f, half, majorGridPaint)
                canvas.drawLine(half, half - 6f, half, half + 6f, majorGridPaint)

                val z = MapTileIndex.getZoom(pMapTileIndex)
                val x = MapTileIndex.getX(pMapTileIndex)
                val y = MapTileIndex.getY(pMapTileIndex)
                canvas.drawText("Z$z/$x/$y", 10f, 22f, labelPaint)

                return BitmapDrawable(resources, bmp)
            }
        }
    }
}
