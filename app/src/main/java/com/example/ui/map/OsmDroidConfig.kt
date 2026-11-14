package com.example.ui.map

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import com.example.model.MapVisualMode
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.MapTileProviderArray
import org.osmdroid.tileprovider.MapTileProviderBase
import org.osmdroid.tileprovider.modules.MapTileModuleProviderBase
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.overlay.TilesOverlay
import java.io.File

/**
 * Configures OsmDroid strictly with app-private directories (filesDir / cacheDir)
 * so zero external storage permissions are required (Play Policy compliant),
 * provides free non-API-key tile sources, an offline vector-grid generator,
 * and custom God's Eye night-mode tile styling filters.
 */
object OsmDroidConfig {

    @Volatile
    private var initializedFilesDir: String? = null

    fun ensureInitialized(context: Context) {
        val appCtx = context.applicationContext
        val targetFilesDir = appCtx.filesDir.absolutePath
        if (initializedFilesDir == targetFilesDir) return
        synchronized(this) {
            if (initializedFilesDir == targetFilesDir) return
            val config = Configuration.getInstance()
            val prefs = appCtx.getSharedPreferences("osmdroid_tactical_prefs", Context.MODE_PRIVATE)
            config.load(appCtx, prefs)
            config.userAgentValue = "AegisEyeTactical/1.0 (Android Crisis Navigation)"
            val baseDir = File(appCtx.filesDir, "osmdroid").apply { mkdirs() }
            val cacheDir = File(appCtx.cacheDir, "osmdroid/tiles").apply { mkdirs() }
            config.osmdroidBasePath = baseDir
            config.osmdroidTileCache = cacheDir
            initializedFilesDir = targetFilesDir
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

    /**
     * Builds the custom God's Eye night-mode [ColorMatrix] for the active [mapMode] & [provider]:
     * - Daylight OSM_STANDARD tiles are inverted into deep obsidian-navy with crisp slate-cyan roads.
     * - CARTO_DARK tiles receive deep midnight-cyan contrast grading aligned with the ISAC palette.
     * - ESRI_SATELLITE tiles receive darkened night-recon grading to preserve tactical HUD legibility.
     * - FLIR_THERMAL applies a high-contrast thermal imaging signature.
     */
    fun createNightModeColorMatrix(
        mapMode: MapVisualMode,
        provider: FreeMapProvider
    ): ColorMatrix? {
        return when (mapMode) {
            MapVisualMode.FLIR_THERMAL -> ColorMatrix(
                floatArrayOf(
                    1.45f, 0f, 0f, 0f, 20f,
                    0f, 0.35f, 0.2f, 0f, 0f,
                    0.4f, 0f, 1.1f, 0f, 25f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            MapVisualMode.SATELLITE_RECON -> ColorMatrix(
                floatArrayOf(
                    0.62f, 0f, 0f, 0f, -6f,
                    0f, 0.68f, 0f, 0f, 2f,
                    0f, 0f, 0.78f, 0f, 14f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            MapVisualMode.TACTICAL_HUD -> when (provider) {
                FreeMapProvider.OSM_STANDARD -> ColorMatrix(
                    floatArrayOf(
                        -0.88f, 0f, 0f, 0f, 235f,
                        0f, -0.84f, 0f, 0f, 232f,
                        0f, 0f, -0.78f, 0f, 225f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                FreeMapProvider.CARTO_DARK -> ColorMatrix(
                    floatArrayOf(
                        0.88f, 0f, 0f, 0f, -6f,
                        0f, 1.04f, 0f, 0f, 3f,
                        0f, 0f, 1.20f, 0f, 12f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                FreeMapProvider.ESRI_SATELLITE -> ColorMatrix(
                    floatArrayOf(
                        0.55f, 0f, 0f, 0f, -8f,
                        0f, 0.65f, 0f, 0f, 2f,
                        0f, 0f, 0.78f, 0f, 14f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                FreeMapProvider.BUILT_IN_OFFLINE -> null
            }
        }
    }

    /**
     * Applies God's Eye night-mode styling to OsmDroid's [TilesOverlay], including dark obsidian
     * tile-loading background/grid colors so uncached regions never flash white.
     */
    fun configureNightModeTilesOverlay(
        tilesOverlay: TilesOverlay,
        mapMode: MapVisualMode,
        provider: FreeMapProvider
    ) {
        tilesOverlay.loadingBackgroundColor = Color.parseColor("#0B0E14")
        tilesOverlay.loadingLineColor = Color.parseColor("#182534")
        val matrix = createNightModeColorMatrix(mapMode, provider)
        tilesOverlay.setColorFilter(matrix?.let { ColorMatrixColorFilter(it) })
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
