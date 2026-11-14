package com.example.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.CameraVulnerabilityStatus
import com.example.model.MapVisualMode
import com.example.model.SurveillanceCamera
import com.example.model.ThreatHeatmapPoint
import com.example.ui.theme.*
import kotlin.math.max

/**
 * Custom Jetpack Compose Map Overlay component layered directly atop the base tactical map.
 * Visualizes latitude/longitude threat-level heatmaps, CCTV optical FOV cones,
 * and an ISAC holographic radar sweep synchronized to the Web Mercator viewport.
 */
@Composable
fun ThreatHeatmapOverlay(
    heatmapPoints: List<ThreatHeatmapPoint>,
    cameras: List<SurveillanceCamera>,
    viewport: MercatorViewport?,
    showHeatmap: Boolean,
    showCctvOverlay: Boolean,
    showRadarSweep: Boolean,
    mapMode: MapVisualMode,
    pulseProgress: Float,
    radarAngle: Float,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .testTag("threat_heatmap_overlay")
    ) {
        val w = size.width
        val h = size.height

        drawHudViewportFrame(w, h, mapMode)

        if (viewport != null) {
            // 1. Threat-Level Heatmap Plumes projected via Web Mercator
            if (showHeatmap) {
                for (pt in heatmapPoints) {
                    val screenCenter = viewport.latLngToScreen(pt.lat, pt.lng)
                    val rawRadiusPx = viewport.metersToPixels(pt.radiusKm * 1000.0, pt.lat)
                    val radiusPx = (rawRadiusPx * (0.95f + 0.12f * pulseProgress))
                        .coerceIn(30f, max(w, h) * 0.65f)

                    if (screenCenter.x + radiusPx < 0 || screenCenter.x - radiusPx > w ||
                        screenCenter.y + radiusPx < 0 || screenCenter.y - radiusPx > h
                    ) {
                        continue
                    }

                    val colors = heatmapGradientForIntensity(pt.intensity, mapMode)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = colors,
                            center = screenCenter,
                            radius = radiusPx
                        ),
                        center = screenCenter,
                        radius = radiusPx,
                        blendMode = BlendMode.Screen
                    )

                    if (pt.intensity >= 0.75f) {
                        drawCircle(
                            color = ThreatCriticalRed.copy(alpha = 0.45f),
                            center = screenCenter,
                            radius = radiusPx * 0.38f,
                            style = Stroke(
                                width = 1.4f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                            )
                        )
                    }
                }
            }

            // 2. OSINT Public & State CCTV Optical Coverage Cones
            if (showCctvOverlay) {
                for (cam in cameras) {
                    val screenCenter = viewport.latLngToScreen(cam.lat, cam.lng)
                    val radiusPx = viewport.metersToPixels(cam.coverageRadiusMeters, cam.lat)
                        .coerceIn(14f, 260f)

                    if (screenCenter.x + radiusPx < 0 || screenCenter.x - radiusPx > w ||
                        screenCenter.y + radiusPx < 0 || screenCenter.y - radiusPx > h
                    ) {
                        continue
                    }

                    val camColor = when (cam.vulnerabilityStatus) {
                        CameraVulnerabilityStatus.VULNERABLE_PUBLIC_OSINT -> ThreatHighOrange
                        CameraVulnerabilityStatus.HARDENED_STATE_ENCRYPTED -> ThreatCriticalRed
                        CameraVulnerabilityStatus.OFFLINE_JAMMED -> ThreatLowGreen
                    }

                    if (cam.fovAngleDegrees >= 359f) {
                        drawCircle(
                            color = camColor.copy(alpha = 0.16f),
                            center = screenCenter,
                            radius = radiusPx
                        )
                        drawCircle(
                            color = camColor.copy(alpha = 0.75f),
                            center = screenCenter,
                            radius = radiusPx,
                            style = Stroke(
                                width = 1.5f,
                                pathEffect = if (cam.vulnerabilityStatus == CameraVulnerabilityStatus.OFFLINE_JAMMED) {
                                    PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f)
                                } else null
                            )
                        )
                    } else {
                        val startAngle = cam.azimuthDegrees - 90f - (cam.fovAngleDegrees / 2f)
                        drawArc(
                            color = camColor.copy(alpha = 0.22f),
                            startAngle = startAngle,
                            sweepAngle = cam.fovAngleDegrees,
                            useCenter = true,
                            topLeft = Offset(screenCenter.x - radiusPx, screenCenter.y - radiusPx),
                            size = Size(radiusPx * 2f, radiusPx * 2f)
                        )
                        drawArc(
                            color = camColor.copy(alpha = 0.85f),
                            startAngle = startAngle,
                            sweepAngle = cam.fovAngleDegrees,
                            useCenter = true,
                            topLeft = Offset(screenCenter.x - radiusPx, screenCenter.y - radiusPx),
                            size = Size(radiusPx * 2f, radiusPx * 2f),
                            style = Stroke(width = 1.5f)
                        )
                    }

                    // Crisp CCTV node dot
                    drawCircle(color = TacticalDarkBg, radius = 6f, center = screenCenter)
                    drawCircle(color = camColor, radius = 4f, center = screenCenter)
                }
            }
        }

        // 3. Synchronized ISAC Radar Sweep
        if (showRadarSweep) {
            val cx = w / 2f
            val cy = h / 2f
            val sweepRadius = max(w, h) * 0.7f
            val sweepColor = when (mapMode) {
                MapVisualMode.TACTICAL_HUD -> DivisionOrange
                MapVisualMode.SATELLITE_RECON -> TacticalCyan
                MapVisualMode.FLIR_THERMAL -> ThreatCriticalRed
            }

            rotate(radarAngle, pivot = Offset(cx, cy)) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color.Transparent,
                            sweepColor.copy(alpha = 0.08f),
                            sweepColor.copy(alpha = 0.22f)
                        ),
                        center = Offset(cx, cy)
                    ),
                    startAngle = -30f,
                    sweepAngle = 30f,
                    useCenter = true,
                    topLeft = Offset(cx - sweepRadius, cy - sweepRadius),
                    size = Size(sweepRadius * 2f, sweepRadius * 2f)
                )
                drawLine(
                    color = sweepColor.copy(alpha = 0.5f),
                    start = Offset(cx, cy),
                    end = Offset(cx + sweepRadius, cy),
                    strokeWidth = 1.5f
                )
            }
        }
    }
}

private fun heatmapGradientForIntensity(intensity: Float, mode: MapVisualMode): List<Color> {
    val alphaBoost = if (mode == MapVisualMode.FLIR_THERMAL) 0.74f else 0.60f
    return when {
        intensity >= 0.85f -> listOf(
            Color(0xFFFFD6D6).copy(alpha = alphaBoost),
            ThreatCriticalRed.copy(alpha = alphaBoost * 0.85f),
            DivisionOrange.copy(alpha = alphaBoost * 0.45f),
            ThreatModerateYellow.copy(alpha = alphaBoost * 0.18f),
            Color.Transparent
        )
        intensity >= 0.60f -> listOf(
            ThreatCriticalRed.copy(alpha = alphaBoost * 0.8f),
            DivisionOrange.copy(alpha = alphaBoost * 0.55f),
            ThreatModerateYellow.copy(alpha = alphaBoost * 0.22f),
            Color.Transparent
        )
        intensity >= 0.35f -> listOf(
            DivisionOrange.copy(alpha = alphaBoost * 0.65f),
            ThreatModerateYellow.copy(alpha = alphaBoost * 0.35f),
            Color.Transparent
        )
        else -> listOf(
            TacticalCyan.copy(alpha = alphaBoost * 0.55f),
            TacticalCyanDim.copy(alpha = alphaBoost * 0.22f),
            Color.Transparent
        )
    }
}

private fun DrawScope.drawHudViewportFrame(width: Float, height: Float, mode: MapVisualMode) {
    val bracketColor = when (mode) {
        MapVisualMode.TACTICAL_HUD -> DivisionOrange.copy(alpha = 0.35f)
        MapVisualMode.SATELLITE_RECON -> TacticalCyan.copy(alpha = 0.35f)
        MapVisualMode.FLIR_THERMAL -> ThreatCriticalRed.copy(alpha = 0.4f)
    }
    val margin = 20f
    val len = 28f
    drawLine(bracketColor, Offset(margin, margin), Offset(margin + len, margin), 2f)
    drawLine(bracketColor, Offset(margin, margin), Offset(margin, margin + len), 2f)

    drawLine(bracketColor, Offset(width - margin, margin), Offset(width - margin - len, margin), 2f)
    drawLine(bracketColor, Offset(width - margin, margin), Offset(width - margin, margin + len), 2f)

    drawLine(bracketColor, Offset(margin, height - margin), Offset(margin + len, height - margin), 2f)
    drawLine(bracketColor, Offset(margin, height - margin), Offset(margin, height - margin - len), 2f)

    drawLine(bracketColor, Offset(width - margin, height - margin), Offset(width - margin - len, height - margin), 2f)
    drawLine(bracketColor, Offset(width - margin, height - margin), Offset(width - margin, height - margin - len), 2f)
}

@Composable
fun HeatmapLegendBar(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(TacticalSurface.copy(alpha = 0.9f), RoundedCornerShape(6.dp))
            .border(1.dp, TacticalBorder, RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("HEATMAP:", style = MaterialTheme.typography.labelSmall, color = TacticalTextSecondary)
        Box(
            modifier = Modifier
                .width(72.dp)
                .height(8.dp)
                .background(
                    brush = Brush.horizontalGradient(
                        listOf(TacticalCyan, ThreatModerateYellow, DivisionOrange, ThreatCriticalRed)
                    ),
                    shape = RoundedCornerShape(4.dp)
                )
        )
        Text("LOW -> LETHAL", style = MaterialTheme.typography.labelSmall, color = ThreatCriticalRed)
    }
}
