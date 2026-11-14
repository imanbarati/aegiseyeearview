package com.example.ui.map

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Point
import com.example.service.GeoJsonThreatPolygon
import org.osmdroid.util.GeoPoint as OsmGeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.FolderOverlay
import org.osmdroid.views.overlay.Polygon

/**
 * Custom clickable OsmDroid [Polygon] overlay built from local GeoJSON threat-zone data.
 * Uses OsmDroid's native hit-testing ([setOnClickListener]) and renders a dashed tactical
 * boundary, translucent threat fill, optional exclusion holes, and centered sector label.
 */
class ClickableThreatPolygon(
    mapView: MapView,
    val threatPolygon: GeoJsonThreatPolygon,
    private val isSelected: Boolean,
    private val onPolygonClick: (GeoJsonThreatPolygon) -> Unit
) : Polygon(mapView) {

    private val centroidPoint = Point()
    private val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(205, 11, 14, 20)
        style = Paint.Style.FILL
    }
    private val badgeBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 21f
        isFakeBoldText = true
    }

    init {
        points = threatPolygon.outerRing.map { OsmGeoPoint(it.lat, it.lng) }
        if (threatPolygon.holes.isNotEmpty()) {
            holes = threatPolygon.holes.map { holeRing ->
                holeRing.map { OsmGeoPoint(it.lat, it.lng) }
            }
        }

        val strokeBase = parseSafeColor(threatPolygon.strokeColorHex, Color.parseColor("#FF2A2A"))
        val fillBase = parseSafeColor(threatPolygon.fillColorHex, strokeBase)

        fillPaint.color = Color.argb(
            if (isSelected) 110 else 56,
            Color.red(fillBase),
            Color.green(fillBase),
            Color.blue(fillBase)
        )
        outlinePaint.color = Color.argb(
            if (isSelected) 255 else 220,
            Color.red(strokeBase),
            Color.green(strokeBase),
            Color.blue(strokeBase)
        )
        outlinePaint.strokeWidth = if (isSelected) 6f else 3.6f
        outlinePaint.pathEffect = DashPathEffect(floatArrayOf(18f, 9f), 0f)

        badgeBorderPaint.color = strokeBase
        badgeTextPaint.color = strokeBase

        title = threatPolygon.name
        snippet = "${threatPolygon.sectorCode} • ${threatPolygon.threatLevel.label}"

        setOnClickListener { _, _, _ ->
            onPolygonClick(threatPolygon)
            true
        }
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        super.draw(canvas, mapView, false)

        // Draw centered tactical sector badge at polygon centroid
        mapView.projection.toPixels(
            OsmGeoPoint(threatPolygon.centerLat, threatPolygon.centerLng),
            centroidPoint
        )
        val cx = centroidPoint.x.toFloat()
        val cy = centroidPoint.y.toFloat()
        if (cx < -100f || cx > mapView.width + 100f || cy < -100f || cy > mapView.height + 100f) {
            return
        }

        val label = threatPolygon.sectorCode
        val textWidth = badgeTextPaint.measureText(label)
        val padX = 12f
        val left = cx - textWidth / 2f - padX
        val right = cx + textWidth / 2f + padX
        val top = cy - 18f
        val bottom = cy + 12f

        canvas.drawRoundRect(left, top, right, bottom, 6f, 6f, badgeBgPaint)
        canvas.drawRoundRect(left, top, right, bottom, 6f, 6f, badgeBorderPaint)
        canvas.drawText(label, cx - textWidth / 2f, cy + 4f, badgeTextPaint)
    }

    private fun parseSafeColor(hex: String, fallback: Int): Int {
        return try {
            Color.parseColor(hex)
        } catch (_: Exception) {
            fallback
        }
    }
}

object ThreatPolygonOverlayBuilder {
    fun rebuildFolder(
        folder: FolderOverlay,
        mapView: MapView,
        polygons: List<GeoJsonThreatPolygon>,
        showPolygons: Boolean,
        selectedPolygonId: String?,
        onPolygonClick: (GeoJsonThreatPolygon) -> Unit
    ) {
        folder.items.clear()
        if (!showPolygons) return
        for (poly in polygons) {
            if (!poly.isEnabled) continue
            folder.add(
                ClickableThreatPolygon(
                    mapView = mapView,
                    threatPolygon = poly,
                    isSelected = poly.id == selectedPolygonId,
                    onPolygonClick = onPolygonClick
                )
            )
        }
    }
}
