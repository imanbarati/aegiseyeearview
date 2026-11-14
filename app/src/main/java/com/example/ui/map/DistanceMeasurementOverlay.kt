package com.example.ui.map

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Point
import android.view.MotionEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.GeoPoint
import com.example.sensor.CompassMath
import com.example.ui.theme.*
import org.osmdroid.util.GeoPoint as OsmGeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import kotlin.math.*

/**
 * Pure-Kotlin real-world WGS84 distance, bearing, and walking-time utilities for the
 * interactive two-point rangefinder overlay.
 */
object TacticalRangefinderMath {
    private const val EARTH_RADIUS_METERS = 6_371_000.0
    private const val WALK_SPEED_METERS_PER_MIN = 80.0 // 4.8 km/h

    fun calculateDistanceMeters(a: GeoPoint, b: GeoPoint): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLng = Math.toRadians(b.lng - a.lng)
        val lat1 = Math.toRadians(a.lat)
        val lat2 = Math.toRadians(b.lat)
        val sinLat = sin(dLat / 2.0)
        val sinLng = sin(dLng / 2.0)
        val h = sinLat * sinLat + cos(lat1) * cos(lat2) * sinLng * sinLng
        val c = 2.0 * atan2(sqrt(h), sqrt((1.0 - h).coerceAtLeast(0.0)))
        return EARTH_RADIUS_METERS * c
    }

    fun formatPrimaryDistance(meters: Double): String {
        return if (meters >= 1000.0) {
            "${String.format("%.2f", meters / 1000.0)} KM"
        } else {
            "${meters.roundToInt()} M"
        }
    }

    fun formatSecondaryDistance(meters: Double): String {
        return if (meters >= 1000.0) {
            "${meters.roundToInt()} M"
        } else {
            "${String.format("%.3f", meters / 1000.0)} KM"
        }
    }

    fun estimateWalkMinutes(meters: Double): Int {
        if (meters <= 0.0) return 0
        return ceil(meters / WALK_SPEED_METERS_PER_MIN).toInt().coerceAtLeast(1)
    }
}

data class DistanceMeasurementState(
    val isActive: Boolean = false,
    val pointA: GeoPoint? = null,
    val pointB: GeoPoint? = null
) {
    val distanceMeters: Double?
        get() = if (pointA != null && pointB != null) {
            TacticalRangefinderMath.calculateDistanceMeters(pointA, pointB)
        } else null

    val bearingDegrees: Float?
        get() = if (pointA != null && pointB != null) {
            CompassMath.bearingBetweenDegrees(pointA.lat, pointA.lng, pointB.lat, pointB.lng)
        } else null

    val walkMinutes: Int?
        get() = distanceMeters?.let { TacticalRangefinderMath.estimateWalkMinutes(it) }
}

/**
 * Native OsmDroid [Overlay] that captures two map taps while Rangefinder mode is active
 * and renders the geodesic baseline, endpoint crosshairs, and centered distance callout.
 */
class DistanceMeasurementOsmOverlay(
    private val onPointTapped: (GeoPoint) -> Unit
) : Overlay() {

    var measurementState: DistanceMeasurementState = DistanceMeasurementState()
    var pulseProgress: Float = 0.5f

    private val reuseA = Point()
    private val reuseB = Point()

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(90, 0, 229, 255)
        style = Paint.Style.STROKE
        strokeWidth = 12f
        strokeCap = Paint.Cap.ROUND
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E5FF")
        style = Paint.Style.STROKE
        strokeWidth = 4.5f
        pathEffect = DashPathEffect(floatArrayOf(18f, 10f), 0f)
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(228, 11, 14, 20)
        style = Paint.Style.FILL
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 23f
        isFakeBoldText = true
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val a = measurementState.pointA ?: return
        val b = measurementState.pointB
        val proj = mapView.projection

        proj.toPixels(OsmGeoPoint(a.lat, a.lng), reuseA)
        val ax = reuseA.x.toFloat()
        val ay = reuseA.y.toFloat()

        if (b != null) {
            proj.toPixels(OsmGeoPoint(b.lat, b.lng), reuseB)
            val bx = reuseB.x.toFloat()
            val by = reuseB.y.toFloat()

            canvas.drawLine(ax, ay, bx, by, glowPaint)
            canvas.drawLine(ax, ay, bx, by, linePaint)

            // Quarter/midpoint notches along baseline
            for (frac in listOf(0.25f, 0.5f, 0.75f)) {
                val mx = ax + (bx - ax) * frac
                val my = ay + (by - ay) * frac
                fillPaint.color = Color.parseColor("#00E5FF")
                canvas.drawCircle(mx, my, if (frac == 0.5f) 5f else 3.5f, fillPaint)
            }

            // Midpoint HUD pill directly on canvas
            val meters = TacticalRangefinderMath.calculateDistanceMeters(a, b)
            val bearing = CompassMath.bearingBetweenDegrees(a.lat, a.lng, b.lat, b.lng).roundToInt() % 360
            val label = "${TacticalRangefinderMath.formatPrimaryDistance(meters)} • ${bearing.toString().padStart(3, '0')}°"
            val textW = textPaint.measureText(label)
            val midX = (ax + bx) / 2f
            val midY = ((ay + by) / 2f) - 24f

            canvas.drawRoundRect(midX - textW / 2f - 14f, midY - 22f, midX + textW / 2f + 14f, midY + 10f, 8f, 8f, badgeBgPaint)
            strokePaint.color = Color.parseColor("#00E5FF")
            strokePaint.strokeWidth = 2f
            canvas.drawRoundRect(midX - textW / 2f - 14f, midY - 22f, midX + textW / 2f + 14f, midY + 10f, 8f, 8f, strokePaint)
            textPaint.color = Color.parseColor("#00E5FF")
            canvas.drawText(label, midX - textW / 2f, midY, textPaint)

            drawPin(canvas, bx, by, "B", Color.parseColor("#FF6B00"))
        }

        drawPin(canvas, ax, ay, "A", Color.parseColor("#00E5FF"))
    }

    private fun drawPin(canvas: Canvas, x: Float, y: Float, code: String, color: Int) {
        fillPaint.color = Color.argb(65, Color.red(color), Color.green(color), Color.blue(color))
        canvas.drawCircle(x, y, 22f + 6f * pulseProgress, fillPaint)

        strokePaint.color = color
        strokePaint.strokeWidth = 3f
        canvas.drawCircle(x, y, 14f, strokePaint)

        fillPaint.color = Color.parseColor("#0B0E14")
        canvas.drawCircle(x, y, 9f, fillPaint)

        textPaint.color = color
        textPaint.textSize = 22f
        canvas.drawText(code, x - 7f, y + 7f, textPaint)
    }

    override fun onSingleTapConfirmed(e: MotionEvent, mapView: MapView): Boolean {
        if (!measurementState.isActive) return false
        val geo = mapView.projection.fromPixels(e.x.toInt(), e.y.toInt())
        onPointTapped(GeoPoint(lat = geo.latitude, lng = geo.longitude))
        return true
    }
}

/**
 * Floating Compose HUD card displaying real-time two-point distance, bearing,
 * and estimated walking time alongside quick snap-to-GPS and reset controls.
 */
@Composable
fun DistanceMeasurementHudCard(
    state: DistanceMeasurementState,
    onSnapPointAToGps: () -> Unit,
    onClearPoints: () -> Unit,
    onCloseMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    val meters = state.distanceMeters
    val bearing = state.bearingDegrees
    val walkMin = state.walkMinutes

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.5.dp, TacticalCyan.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
            .testTag("distance_measurement_hud_card"),
        colors = CardDefaults.cardColors(containerColor = TacticalSurface.copy(alpha = 0.96f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Straighten,
                        contentDescription = null,
                        tint = TacticalCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "TACTICAL RANGEFINDER",
                        style = MaterialTheme.typography.labelLarge,
                        color = TacticalCyan,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(
                    onClick = onCloseMode,
                    modifier = Modifier.size(32.dp).testTag("close_measure_mode_button")
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Exit Rangefinder Mode", tint = TacticalTextSecondary, modifier = Modifier.size(18.dp))
                }
            }

            if (meters != null && bearing != null && walkMin != null) {
                val bearingInt = bearing.roundToInt() % 360
                val cardinal = CompassMath.headingToCardinal(bearing)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TacticalSurfaceElevated, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("DISTANCE", style = MaterialTheme.typography.labelSmall, color = TacticalTextDim)
                        Text(
                            text = TacticalRangefinderMath.formatPrimaryDistance(meters),
                            style = MaterialTheme.typography.titleMedium,
                            color = TacticalCyan,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("measured_distance_value")
                        )
                        Text(
                            text = TacticalRangefinderMath.formatSecondaryDistance(meters),
                            style = MaterialTheme.typography.labelSmall,
                            color = TacticalTextSecondary
                        )
                    }
                    Column {
                        Text("AZIMUTH", style = MaterialTheme.typography.labelSmall, color = TacticalTextDim)
                        Text(
                            text = "${bearingInt.toString().padStart(3, '0')}° $cardinal",
                            style = MaterialTheme.typography.titleMedium,
                            color = DivisionOrange,
                            fontWeight = FontWeight.Bold
                        )
                        Text("TRUE NORTH", style = MaterialTheme.typography.labelSmall, color = TacticalTextSecondary)
                    }
                    Column {
                        Text("EST. WALK", style = MaterialTheme.typography.labelSmall, color = TacticalTextDim)
                        Text(
                            text = "~$walkMin MIN",
                            style = MaterialTheme.typography.titleMedium,
                            color = ThreatLowGreen,
                            fontWeight = FontWeight.Bold
                        )
                        Text("4.8 KM/H", style = MaterialTheme.typography.labelSmall, color = TacticalTextSecondary)
                    }
                }
            } else {
                val prompt = if (state.pointA == null) {
                    "TAP FIRST POINT (A) ANYWHERE ON THE MAP"
                } else {
                    "POINT A LOCKED (${String.format("%.4f", state.pointA.lat)}°, ${String.format("%.4f", state.pointA.lng)}°) — TAP POINT B"
                }
                Text(
                    text = prompt,
                    style = MaterialTheme.typography.labelMedium,
                    color = TacticalTextPrimary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onSnapPointAToGps,
                    border = BorderStroke(1.dp, TacticalCyan.copy(alpha = 0.6f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TacticalCyan),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.weight(1f).height(38.dp).testTag("measure_use_gps_button")
                ) {
                    Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("SET A TO MY GPS", style = MaterialTheme.typography.labelSmall)
                }

                if (state.pointA != null) {
                    OutlinedButton(
                        onClick = onClearPoints,
                        border = BorderStroke(1.dp, DivisionOrange.copy(alpha = 0.7f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = DivisionOrange),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(38.dp).testTag("measure_clear_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("RESET", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
