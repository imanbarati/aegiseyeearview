package com.example.ui.map

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.view.MotionEvent
import com.example.data.TacticalDataProvider
import com.example.data.local.BreadcrumbEntity
import com.example.location.UserMovementState
import com.example.model.*
import org.osmdroid.util.GeoPoint as OsmGeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Native OsmDroid [Overlay] rendering core tactical elements (country perimeter, urban nodes,
 * threat dead zones, A* safest walking corridor, safe zones, 50m temporary breadcrumb trail,
 * live agent marker, SOS broadcast map pulse, and targeting reticle) plus tap hit-testing.
 */
class TacticalOsmOverlay(
    private val onCameraTapped: (SurveillanceCamera) -> Unit,
    private val onHavenTapped: (SafeHaven) -> Unit,
    private val onZoneTapped: (ThreatZone) -> Unit,
    private val onCityTapped: (GeoPoint) -> Unit,
    private val onEmptyTapped: () -> Unit
) : Overlay() {

    var threatZones: List<ThreatZone> = emptyList()
    var safeHavens: List<SafeHaven> = emptyList()
    var cameras: List<SurveillanceCamera> = emptyList()
    var activeRoute: TacticalRoute? = null
    var breadcrumbs: List<BreadcrumbEntity> = emptyList()
    var showBreadcrumbs: Boolean = true
    var isSosPulseActive: Boolean = false
    var sosPulseCoords: Pair<Double, Double>? = null
    var userMovement: UserMovementState = UserMovementState(35.6892, 51.3890)
    var mapMode: MapVisualMode = MapVisualMode.TACTICAL_HUD
    var showDeadZones: Boolean = true
    var showSafeHavens: Boolean = true
    var showCctvOverlay: Boolean = true
    var selectedLabel: String? = null
    var selectedCoords: Pair<Double, Double>? = null
    var pulseProgress: Float = 0.5f
    var dashOffset: Float = 0f

    private val pt = Point()
    private val path = Path()

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 24f
        isFakeBoldText = true
        setShadowLayer(4f, 0f, 0f, Color.BLACK)
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val proj = mapView.projection
        val w = mapView.width.toFloat()
        val h = mapView.height.toFloat()

        // 1. Country tactical perimeter
        drawPerimeter(canvas, proj)

        // 2. Urban nodes
        drawUrbanNodes(canvas, proj, w, h)

        // 3. Dead zones
        if (showDeadZones) {
            drawDeadZones(canvas, proj, w, h)
        }

        // 4. Active A* walking / evacuation route
        activeRoute?.let { drawRoute(canvas, proj, it) }

        // 5. Safe Havens / Walking Safe Zones
        if (showSafeHavens) {
            drawSafeHavens(canvas, proj, w, h)
        }

        // 5b. Temporary 50-meter movement Breadcrumb trail
        if (showBreadcrumbs && breadcrumbs.isNotEmpty()) {
            drawBreadcrumbs(canvas, proj, w, h)
        }

        // 6. Live Agent Position & Bearing Vector
        drawAgentMarker(canvas, proj)

        // 6b. High-visibility SOS Broadcast Shockwave Pulse
        val sosCoords = sosPulseCoords
        if (isSosPulseActive && sosCoords != null) {
            drawSosPulse(canvas, proj, sosCoords.first, sosCoords.second)
        }

        // 7. Targeting Reticle if item selected
        val coords = selectedCoords
        val label = selectedLabel
        if (coords != null && label != null) {
            proj.toPixels(OsmGeoPoint(coords.first, coords.second), pt)
            drawReticle(canvas, pt.x.toFloat(), pt.y.toFloat(), label, coords.first, coords.second)
        }
    }

    private fun drawPerimeter(canvas: Canvas, proj: org.osmdroid.views.Projection) {
        val pts = TacticalDataProvider.IRAN_BORDER_POLYGON
        if (pts.size < 3) return
        path.reset()
        pts.forEachIndexed { idx, gp ->
            proj.toPixels(OsmGeoPoint(gp.lat, gp.lng), pt)
            if (idx == 0) path.moveTo(pt.x.toFloat(), pt.y.toFloat())
            else path.lineTo(pt.x.toFloat(), pt.y.toFloat())
        }
        path.close()

        strokePaint.color = when (mapMode) {
            MapVisualMode.TACTICAL_HUD -> Color.argb(160, 255, 107, 0)
            MapVisualMode.SATELLITE_RECON -> Color.argb(150, 0, 229, 255)
            MapVisualMode.FLIR_THERMAL -> Color.argb(170, 255, 42, 42)
        }
        strokePaint.strokeWidth = 2.8f
        strokePaint.pathEffect = DashPathEffect(floatArrayOf(16f, 8f), 0f)
        canvas.drawPath(path, strokePaint)
    }

    private fun drawUrbanNodes(
        canvas: Canvas,
        proj: org.osmdroid.views.Projection,
        w: Float,
        h: Float
    ) {
        for (city in TacticalDataProvider.CITIES) {
            proj.toPixels(OsmGeoPoint(city.lat, city.lng), pt)
            val x = pt.x.toFloat()
            val y = pt.y.toFloat()
            if (x < -60f || x > w + 60f || y < -60f || y > h + 60f) continue

            val isCapital = city.name == "Tehran"
            val color = if (isCapital) Color.parseColor("#FF6B00") else Color.parseColor("#DCE4EF")
            val sq = if (isCapital) 10f else 6f
            fillPaint.color = color
            canvas.drawRect(x - sq / 2f, y - sq / 2f, x + sq / 2f, y + sq / 2f, fillPaint)

            textPaint.color = color
            textPaint.textSize = if (isCapital) 28f else 22f
            canvas.drawText(city.name, x + 10f, y + 6f, textPaint)
        }
    }

    private fun drawDeadZones(
        canvas: Canvas,
        proj: org.osmdroid.views.Projection,
        w: Float,
        h: Float
    ) {
        for (zone in threatZones) {
            proj.toPixels(OsmGeoPoint(zone.centerLat, zone.centerLng), pt)
            val cx = pt.x.toFloat()
            val cy = pt.y.toFloat()
            val radiusPx = proj.metersToPixels((zone.radiusKm * 1000.0).toFloat()).coerceIn(20f, 380f)
            if (cx + radiusPx < 0 || cx - radiusPx > w || cy + radiusPx < 0 || cy - radiusPx > h) continue

            val zColor = when (zone.level) {
                ThreatLevel.CRITICAL_ALPHA -> Color.parseColor("#FF2A2A")
                ThreatLevel.HIGH_BRAVO -> Color.parseColor("#FF6B00")
                ThreatLevel.MODERATE_CHARLIE -> Color.parseColor("#FFB300")
                ThreatLevel.CAUTION_DELTA -> Color.parseColor("#00E5FF")
            }

            strokePaint.color = Color.argb(190, Color.red(zColor), Color.green(zColor), Color.blue(zColor))
            strokePaint.strokeWidth = 2.6f
            strokePaint.pathEffect = DashPathEffect(floatArrayOf(12f, 7f), 0f)
            canvas.drawCircle(cx, cy, radiusPx * (0.96f + 0.08f * pulseProgress), strokePaint)

            fillPaint.color = zColor
            canvas.drawCircle(cx, cy, 7f, fillPaint)

            textPaint.color = zColor
            textPaint.textSize = 21f
            canvas.drawText(zone.sectorCode, cx - 36f, cy + 26f, textPaint)
        }
    }

    private fun drawRoute(canvas: Canvas, proj: org.osmdroid.views.Projection, route: TacticalRoute) {
        if (route.path.size < 2) return
        val screenPts = route.path.map {
            proj.toPixels(OsmGeoPoint(it.lat, it.lng), pt)
            pt.x.toFloat() to pt.y.toFloat()
        }

        path.reset()
        screenPts.forEachIndexed { idx, (x, y) ->
            if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        val routeColor = if (route.isWalkingRoute) Color.parseColor("#00E676") else Color.parseColor("#00E5FF")
        strokePaint.pathEffect = null
        strokePaint.color = Color.argb(75, Color.red(routeColor), Color.green(routeColor), Color.blue(routeColor))
        strokePaint.strokeWidth = 18f
        strokePaint.strokeCap = Paint.Cap.ROUND
        strokePaint.strokeJoin = Paint.Join.ROUND
        canvas.drawPath(path, strokePaint)

        strokePaint.color = routeColor
        strokePaint.strokeWidth = 6f
        strokePaint.pathEffect = DashPathEffect(floatArrayOf(24f, 12f), dashOffset)
        canvas.drawPath(path, strokePaint)

        screenPts.forEachIndexed { idx, (x, y) ->
            val isTerminal = idx == 0 || idx == screenPts.lastIndex
            val wpColor = when {
                idx == 0 -> Color.parseColor("#FF6B00")
                idx == screenPts.lastIndex -> Color.parseColor("#00E676")
                else -> Color.parseColor("#00E5FF")
            }
            val r = if (isTerminal) 9f + 2.5f * pulseProgress else 5.5f
            fillPaint.color = wpColor
            canvas.drawCircle(x, y, r, fillPaint)
            fillPaint.color = Color.parseColor("#0B0E14")
            canvas.drawCircle(x, y, r * 0.42f, fillPaint)

            if (isTerminal || idx % 2 == 0) {
                textPaint.color = wpColor
                textPaint.textSize = 20f
                val tag = when {
                    idx == 0 -> "START"
                    idx == screenPts.lastIndex -> "SAFE ZONE"
                    else -> "WP-$idx"
                }
                canvas.drawText(tag, x + 12f, y - 10f, textPaint)
            }
        }
    }

    private fun drawSafeHavens(
        canvas: Canvas,
        proj: org.osmdroid.views.Projection,
        w: Float,
        h: Float
    ) {
        val green = Color.parseColor("#00E676")
        for (haven in safeHavens) {
            proj.toPixels(OsmGeoPoint(haven.lat, haven.lng), pt)
            val x = pt.x.toFloat()
            val y = pt.y.toFloat()
            if (x < -90f || x > w + 90f || y < -90f || y > h + 90f) continue

            fillPaint.color = Color.argb(56, 0, 230, 118)
            canvas.drawCircle(x, y, 20f + 7f * pulseProgress, fillPaint)

            strokePaint.pathEffect = null
            strokePaint.color = green
            strokePaint.strokeWidth = 2.2f
            canvas.drawCircle(x, y, 15f, strokePaint)

            fillPaint.color = green
            canvas.drawCircle(x, y, 6f, fillPaint)

            textPaint.color = green
            textPaint.textSize = 21f
            canvas.drawText("[+] ${haven.name.take(22)}", x - 60f, y - 24f, textPaint)
        }
    }

    private fun drawBreadcrumbs(
        canvas: Canvas,
        proj: org.osmdroid.views.Projection,
        w: Float,
        h: Float
    ) {
        val total = breadcrumbs.size
        if (total == 0) return

        val screenPoints = breadcrumbs.map { crumb ->
            proj.toPixels(OsmGeoPoint(crumb.lat, crumb.lng), pt)
            pt.x.toFloat() to pt.y.toFloat()
        }

        if (screenPoints.size >= 2) {
            path.reset()
            screenPoints.forEachIndexed { idx, (x, y) ->
                if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            strokePaint.color = Color.argb(175, 255, 179, 0)
            strokePaint.strokeWidth = 4.2f
            strokePaint.strokeCap = Paint.Cap.ROUND
            strokePaint.strokeJoin = Paint.Join.ROUND
            strokePaint.pathEffect = DashPathEffect(floatArrayOf(12f, 8f), -dashOffset * 0.5f)
            canvas.drawPath(path, strokePaint)
        }

        strokePaint.pathEffect = null
        breadcrumbs.forEachIndexed { idx, crumb ->
            val (x, y) = screenPoints[idx]
            if (x < -80f || x > w + 80f || y < -80f || y > h + 80f) return@forEachIndexed

            val recencyFraction = if (total <= 1) 1f else (idx.toFloat() / (total - 1).toFloat())
            val alpha = (115 + (recencyFraction * 140f)).toInt().coerceIn(100, 255)

            fillPaint.color = Color.argb((alpha * 0.35f).toInt(), 0, 229, 255)
            canvas.drawCircle(x, y, 13f, fillPaint)

            strokePaint.color = Color.argb(alpha, 255, 179, 0)
            strokePaint.strokeWidth = 2.4f
            canvas.drawCircle(x, y, 8.5f, strokePaint)

            fillPaint.color = Color.argb(alpha, 0, 229, 255)
            canvas.drawCircle(x, y, 4.5f, fillPaint)

            val showLabel = idx == 0 || idx == total - 1 || idx % 3 == 0
            if (showLabel) {
                textPaint.color = Color.argb(alpha, 255, 200, 64)
                textPaint.textSize = 18f
                canvas.drawText("#${crumb.sequenceNumber} (50m)", x + 11f, y + 6f, textPaint)
            }
        }
    }

    private fun drawSosPulse(
        canvas: Canvas,
        proj: org.osmdroid.views.Projection,
        lat: Double,
        lng: Double
    ) {
        proj.toPixels(OsmGeoPoint(lat, lng), pt)
        val cx = pt.x.toFloat()
        val cy = pt.y.toFloat()

        strokePaint.pathEffect = null

        // 3 staggered concentric expanding shockwaves for unmistakable SOS map visibility
        for (ringIdx in 0 until 3) {
            val phase = (pulseProgress + ringIdx * 0.33f) % 1.0f
            val radius = 26f + phase * 115f
            val alpha = ((1f - phase) * 235f).toInt().coerceIn(25, 235)

            fillPaint.color = Color.argb((alpha * 0.22f).toInt(), 255, 42, 42)
            canvas.drawCircle(cx, cy, radius, fillPaint)

            strokePaint.color = when (ringIdx) {
                0 -> Color.argb(alpha, 255, 42, 42)
                1 -> Color.argb(alpha, 255, 107, 0)
                else -> Color.argb(alpha, 255, 255, 255)
            }
            strokePaint.strokeWidth = 4.2f - ringIdx * 0.8f
            canvas.drawCircle(cx, cy, radius, strokePaint)
        }

        // Core pulsing SOS diamond beacon
        val d = 18f + 5f * pulseProgress
        path.reset()
        path.moveTo(cx, cy - d)
        path.lineTo(cx + d, cy)
        path.lineTo(cx, cy + d)
        path.lineTo(cx - d, cy)
        path.close()
        fillPaint.color = Color.argb(230, 255, 42, 42)
        canvas.drawPath(path, fillPaint)
        strokePaint.color = Color.WHITE
        strokePaint.strokeWidth = 2.6f
        canvas.drawPath(path, strokePaint)

        // High-contrast SOS label above beacon
        textPaint.color = Color.parseColor("#FF2A2A")
        textPaint.textSize = 23f
        canvas.drawText("SOS BROADCAST ACTIVE", cx - 96f, cy - 34f, textPaint)
        textPaint.color = Color.WHITE
        textPaint.textSize = 18f
        canvas.drawText("${String.format("%.4f", lat)}°N, ${String.format("%.4f", lng)}°E", cx - 76f, cy - 14f, textPaint)
    }

    private fun drawAgentMarker(canvas: Canvas, proj: org.osmdroid.views.Projection) {
        proj.toPixels(OsmGeoPoint(userMovement.lat, userMovement.lng), pt)
        val cx = pt.x.toFloat()
        val cy = pt.y.toFloat()

        fillPaint.color = Color.argb(60, 255, 107, 0)
        canvas.drawCircle(cx, cy, 26f + 8f * pulseProgress, fillPaint)

        strokePaint.pathEffect = null
        strokePaint.color = Color.parseColor("#FF6B00")
        strokePaint.strokeWidth = 3.2f
        canvas.drawCircle(cx, cy, 16f, strokePaint)

        fillPaint.color = Color.parseColor("#0B0E14")
        canvas.drawCircle(cx, cy, 10f, fillPaint)

        fillPaint.color = Color.parseColor("#00E5FF")
        canvas.drawCircle(cx, cy, 5.5f, fillPaint)

        val bearingRad = Math.toRadians(userMovement.bearingDegrees.toDouble() - 90.0)
        strokePaint.color = Color.parseColor("#00E5FF")
        strokePaint.strokeWidth = 3f
        canvas.drawLine(
            cx,
            cy,
            cx + 28f * cos(bearingRad).toFloat(),
            cy + 28f * sin(bearingRad).toFloat(),
            strokePaint
        )

        textPaint.color = Color.parseColor("#FF6B00")
        textPaint.textSize = 21f
        val speedLabel = if (userMovement.speedKmh > 0f) "${userMovement.speedKmh.toInt()} KM/H" else "AGENT"
        canvas.drawText(speedLabel, cx - 28f, cy + 32f, textPaint)
    }

    private fun drawReticle(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        label: String,
        lat: Double,
        lng: Double
    ) {
        val r = 28f
        val len = 11f
        val orange = Color.parseColor("#FF6B00")
        strokePaint.pathEffect = null
        strokePaint.color = orange
        strokePaint.strokeWidth = 2.6f

        canvas.drawLine(cx - r, cy - r, cx - r + len, cy - r, strokePaint)
        canvas.drawLine(cx - r, cy - r, cx - r, cy - r + len, strokePaint)
        canvas.drawLine(cx + r, cy - r, cx + r - len, cy - r, strokePaint)
        canvas.drawLine(cx + r, cy - r, cx + r, cy - r + len, strokePaint)
        canvas.drawLine(cx - r, cy + r, cx - r + len, cy + r, strokePaint)
        canvas.drawLine(cx - r, cy + r, cx - r, cy + r - len, strokePaint)
        canvas.drawLine(cx + r, cy + r, cx + r - len, cy + r, strokePaint)
        canvas.drawLine(cx + r, cy + r, cx + r, cy + r - len, strokePaint)

        fillPaint.color = orange
        canvas.drawCircle(cx, cy, 4f, fillPaint)

        textPaint.color = orange
        textPaint.textSize = 20f
        canvas.drawText("LOCK: ${label.take(22)}", cx + r + 8f, cy - 6f, textPaint)
        canvas.drawText("${String.format("%.3f", lat)}°N, ${String.format("%.3f", lng)}°E", cx + r + 8f, cy + 16f, textPaint)
    }

    override fun onSingleTapConfirmed(e: MotionEvent, mapView: MapView): Boolean {
        val proj = mapView.projection
        val tapX = e.x
        val tapY = e.y
        val hitPx = 48.0

        // 1. Cameras
        if (showCctvOverlay) {
            val cam = cameras.firstOrNull {
                proj.toPixels(OsmGeoPoint(it.lat, it.lng), pt)
                hypot((pt.x - tapX).toDouble(), (pt.y - tapY).toDouble()) < hitPx
            }
            if (cam != null) {
                onCameraTapped(cam)
                return true
            }
        }

        // 2. Safe Havens
        if (showSafeHavens) {
            val haven = safeHavens.firstOrNull {
                proj.toPixels(OsmGeoPoint(it.lat, it.lng), pt)
                hypot((pt.x - tapX).toDouble(), (pt.y - tapY).toDouble()) < hitPx
            }
            if (haven != null) {
                onHavenTapped(haven)
                return true
            }
        }

        // 3. Threat Zones
        if (showDeadZones) {
            val zone = threatZones.firstOrNull {
                proj.toPixels(OsmGeoPoint(it.centerLat, it.centerLng), pt)
                hypot((pt.x - tapX).toDouble(), (pt.y - tapY).toDouble()) < hitPx * 1.25
            }
            if (zone != null) {
                onZoneTapped(zone)
                return true
            }
        }

        // 4. Cities
        val city = TacticalDataProvider.CITIES.firstOrNull {
            proj.toPixels(OsmGeoPoint(it.lat, it.lng), pt)
            hypot((pt.x - tapX).toDouble(), (pt.y - tapY).toDouble()) < hitPx
        }
        if (city != null) {
            onCityTapped(city)
            return true
        }

        onEmptyTapped()
        return false
    }
}
