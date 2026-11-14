package com.example.ui.map.mvt

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.view.MotionEvent
import org.osmdroid.util.GeoPoint as OsmGeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import kotlin.math.hypot

data class MvtFeatureSelection(
    val layerName: String,
    val sourceFileName: String,
    val feature: MvtFeature,
    val tileZ: Int,
    val tileX: Int,
    val tileY: Int
) {
    val displayTitle: String
        get() = (feature.properties["name"] as? String)
            ?: (feature.properties["sector_code"] as? String)
            ?: "MVT Feature #${feature.id}"
}

/**
 * Custom OsmDroid [Overlay] that renders decoded Mapbox Vector Tile ([MvtTile]) layers
 * compiled from local GeoJSON tactical files directly onto the OsmDroid [MapView] Canvas.
 */
class MvtVectorTileOverlay(
    private val onFeatureTapped: (MvtFeatureSelection) -> Unit
) : Overlay() {

    private var activeOverlays: List<TacticalGeoJsonOverlay> = emptyList()
    private var selectedFeatureKey: String? = null
    private var renderPolygons: Boolean = true

    private val reusePoint = Point()
    private val polyPath = Path().apply { fillType = Path.FillType.EVEN_ODD }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val pointBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0B0E14")
        style = Paint.Style.FILL
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 24f
        isFakeBoldText = true
        setShadowLayer(4f, 0f, 0f, Color.BLACK)
    }

    fun updateOverlays(
        overlays: List<TacticalGeoJsonOverlay>,
        selected: MvtFeatureSelection?,
        renderPolygons: Boolean = true
    ) {
        activeOverlays = overlays.filter { it.isEnabled }
        selectedFeatureKey = selected?.let { "${it.sourceFileName}:${it.layerName}:${it.feature.id}" }
        this.renderPolygons = renderPolygons
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow || activeOverlays.isEmpty()) return
        val projection = mapView.projection

        for (overlay in activeOverlays) {
            val tile = overlay.mvtTile
            for (layer in tile.layers) {
                for (feature in layer.features) {
                    val isSelected = selectedFeatureKey == "${overlay.fileName}:${layer.name}:${feature.id}"
                    val baseColor = resolveFeatureColor(feature)

                    when (feature.type) {
                        MvtGeometryType.POLYGON -> {
                            if (!renderPolygons) continue
                            polyPath.reset()
                            var centroidX = 0f
                            var centroidY = 0f
                            var count = 0

                            for (ring in feature.geoGeometries) {
                                if (ring.size < 3) continue
                                ring.forEachIndexed { idx, pt ->
                                    projection.toPixels(OsmGeoPoint(pt.lat, pt.lng), reusePoint)
                                    val px = reusePoint.x.toFloat()
                                    val py = reusePoint.y.toFloat()
                                    if (idx == 0) polyPath.moveTo(px, py) else polyPath.lineTo(px, py)
                                    centroidX += px
                                    centroidY += py
                                    count++
                                }
                                polyPath.close()
                            }

                            if (count > 0) {
                                fillPaint.color = withAlpha(baseColor, if (isSelected) 95 else 42)
                                canvas.drawPath(polyPath, fillPaint)

                                strokePaint.color = withAlpha(baseColor, if (isSelected) 255 else 210)
                                strokePaint.strokeWidth = if (isSelected) 5.5f else 3.2f
                                strokePaint.pathEffect = DashPathEffect(floatArrayOf(16f, 8f), 0f)
                                canvas.drawPath(polyPath, strokePaint)

                                val code = (feature.properties["sector_code"] as? String)
                                    ?: (feature.properties["name"] as? String)
                                if (!code.isNullOrBlank()) {
                                    labelPaint.color = baseColor
                                    canvas.drawText(
                                        code.take(18),
                                        (centroidX / count) - 42f,
                                        centroidY / count,
                                        labelPaint
                                    )
                                }
                            }
                        }

                        MvtGeometryType.LINESTRING -> {
                            for (line in feature.geoGeometries) {
                                if (line.size < 2) continue
                                polyPath.reset()
                                var midX = 0f
                                var midY = 0f
                                line.forEachIndexed { idx, pt ->
                                    projection.toPixels(OsmGeoPoint(pt.lat, pt.lng), reusePoint)
                                    val px = reusePoint.x.toFloat()
                                    val py = reusePoint.y.toFloat()
                                    if (idx == 0) polyPath.moveTo(px, py) else polyPath.lineTo(px, py)
                                    if (idx == line.size / 2) {
                                        midX = px
                                        midY = py
                                    }
                                }

                                glowPaint.color = withAlpha(baseColor, if (isSelected) 120 else 65)
                                glowPaint.strokeWidth = if (isSelected) 16f else 11f
                                glowPaint.pathEffect = null
                                canvas.drawPath(polyPath, glowPaint)

                                strokePaint.color = baseColor
                                strokePaint.strokeWidth = if (isSelected) 6f else 4f
                                strokePaint.pathEffect = DashPathEffect(floatArrayOf(20f, 10f), 0f)
                                canvas.drawPath(polyPath, strokePaint)

                                val tag = (feature.properties["sector_code"] as? String)
                                    ?: (feature.properties["name"] as? String)
                                if (!tag.isNullOrBlank()) {
                                    labelPaint.color = baseColor
                                    canvas.drawText(tag.take(18), midX + 10f, midY - 10f, labelPaint)
                                }
                            }
                        }

                        MvtGeometryType.POINT -> {
                            for (group in feature.geoGeometries) {
                                for (pt in group) {
                                    projection.toPixels(OsmGeoPoint(pt.lat, pt.lng), reusePoint)
                                    val px = reusePoint.x.toFloat()
                                    val py = reusePoint.y.toFloat()

                                    strokePaint.pathEffect = null
                                    strokePaint.color = baseColor
                                    strokePaint.strokeWidth = if (isSelected) 4.5f else 3f
                                    canvas.drawCircle(px, py, if (isSelected) 18f else 13f, strokePaint)
                                    canvas.drawCircle(px, py, 8f, pointBgPaint)
                                    fillPaint.color = baseColor
                                    canvas.drawCircle(px, py, 5f, fillPaint)

                                    val tag = (feature.properties["sector_code"] as? String)
                                        ?: (feature.properties["name"] as? String)
                                    if (!tag.isNullOrBlank()) {
                                        labelPaint.color = baseColor
                                        canvas.drawText(tag.take(20), px + 18f, py + 7f, labelPaint)
                                    }
                                }
                            }
                        }

                        MvtGeometryType.UNKNOWN -> Unit
                    }
                }
            }
        }
    }

    override fun onSingleTapConfirmed(e: MotionEvent, mapView: MapView): Boolean {
        if (activeOverlays.isEmpty()) return false
        val projection = mapView.projection
        val tapX = e.x
        val tapY = e.y

        // Check Points first, then LineStrings, then Polygons (when MVT owns polygon drawing)
        val targetTypes = if (renderPolygons) {
            listOf(MvtGeometryType.POINT, MvtGeometryType.LINESTRING, MvtGeometryType.POLYGON)
        } else {
            listOf(MvtGeometryType.POINT, MvtGeometryType.LINESTRING)
        }
        for (targetType in targetTypes) {
            for (overlay in activeOverlays) {
                val tile = overlay.mvtTile
                for (layer in tile.layers) {
                    for (feature in layer.features) {
                        if (feature.type != targetType) continue
                        val hit = when (targetType) {
                            MvtGeometryType.POINT -> feature.geoGeometries.flatten().any { pt ->
                                projection.toPixels(OsmGeoPoint(pt.lat, pt.lng), reusePoint)
                                hypot((reusePoint.x - tapX).toDouble(), (reusePoint.y - tapY).toDouble()) < 48.0
                            }
                            MvtGeometryType.LINESTRING -> feature.geoGeometries.any { line ->
                                isNearPolyline(line, projection, tapX, tapY, 36.0)
                            }
                            MvtGeometryType.POLYGON -> feature.geoGeometries.any { ring ->
                                isPointInScreenPolygon(ring, projection, tapX, tapY)
                            }
                            MvtGeometryType.UNKNOWN -> false
                        }
                        if (hit) {
                            onFeatureTapped(
                                MvtFeatureSelection(
                                    layerName = layer.name,
                                    sourceFileName = overlay.fileName,
                                    feature = feature,
                                    tileZ = tile.z,
                                    tileX = tile.x,
                                    tileY = tile.y
                                )
                            )
                            return true
                        }
                    }
                }
            }
        }
        return false
    }

    private fun isNearPolyline(
        line: List<com.example.model.GeoPoint>,
        projection: org.osmdroid.views.Projection,
        tapX: Float,
        tapY: Float,
        thresholdPx: Double
    ): Boolean {
        if (line.size < 2) return false
        val screenPts = line.map {
            projection.toPixels(OsmGeoPoint(it.lat, it.lng), reusePoint)
            reusePoint.x.toFloat() to reusePoint.y.toFloat()
        }
        for (i in 0 until screenPts.lastIndex) {
            val (ax, ay) = screenPts[i]
            val (bx, by) = screenPts[i + 1]
            val dx = bx - ax
            val dy = by - ay
            val lenSq = dx * dx + dy * dy
            val t = if (lenSq == 0f) 0f else (((tapX - ax) * dx + (tapY - ay) * dy) / lenSq).coerceIn(0f, 1f)
            val projX = ax + t * dx
            val projY = ay + t * dy
            if (hypot((tapX - projX).toDouble(), (tapY - projY).toDouble()) <= thresholdPx) {
                return true
            }
        }
        return false
    }

    private fun isPointInScreenPolygon(
        ring: List<com.example.model.GeoPoint>,
        projection: org.osmdroid.views.Projection,
        tapX: Float,
        tapY: Float
    ): Boolean {
        if (ring.size < 3) return false
        val pts = ring.map {
            projection.toPixels(OsmGeoPoint(it.lat, it.lng), reusePoint)
            reusePoint.x.toFloat() to reusePoint.y.toFloat()
        }
        var inside = false
        var j = pts.lastIndex
        for (i in pts.indices) {
            val (xi, yi) = pts[i]
            val (xj, yj) = pts[j]
            val intersect = ((yi > tapY) != (yj > tapY)) &&
                (tapX < (xj - xi) * (tapY - yi) / ((yj - yi).takeIf { it != 0f } ?: 0.0001f) + xi)
            if (intersect) inside = !inside
            j = i
        }
        return inside
    }

    private fun resolveFeatureColor(feature: MvtFeature): Int {
        val hex = (feature.properties["stroke"] as? String)
            ?: (feature.properties["fill"] as? String)
        if (!hex.isNullOrBlank()) {
            try {
                return Color.parseColor(hex)
            } catch (_: Exception) {
                // fall back to threat-level palette
            }
        }
        val level = (feature.properties["threat_level"] as? String)?.uppercase() ?: ""
        return when {
            "CRITICAL" in level || "LETHAL" in level -> Color.parseColor("#FF2A2A")
            "HIGH" in level -> Color.parseColor("#FF6B00")
            "CAUTION" in level -> Color.parseColor("#FFB300")
            "LOW" in level || "SAFE" in level -> Color.parseColor("#00E676")
            else -> Color.parseColor("#00E5FF")
        }
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))
}
