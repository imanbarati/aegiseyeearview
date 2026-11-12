package com.example.ui.map

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.TacticalDataProvider
import com.example.model.*
import com.example.ui.theme.*
import kotlin.math.*

@OptIn(ExperimentalTextApi::class)
@Composable
fun TacticalMapView(
    threatZones: List<ThreatZone>,
    safeHavens: List<SafeHaven>,
    activeRoute: TacticalRoute?,
    mapMode: MapVisualMode,
    showDeadZones: Boolean,
    showSafeHavens: Boolean,
    showRadarSweep: Boolean,
    selectedZone: ThreatZone?,
    selectedHaven: SafeHaven?,
    onZoneSelected: (ThreatZone) -> Unit,
    onHavenSelected: (SafeHaven) -> Unit,
    modifier: Modifier = Modifier
) {
    // Geographic bounding box for Iran & neighboring escape corridors
    val minLat = 24.0
    val maxLat = 40.5
    val minLng = 43.5
    val maxLng = 64.0

    // Map transformation state
    var zoomScale by remember { mutableFloatStateOf(1.0f) }
    var panOffsetX by remember { mutableFloatStateOf(0.0f) }
    var panOffsetY by remember { mutableFloatStateOf(0.0f) }

    // Reticle selection state
    var selectedPointName by remember { mutableStateOf<String?>(null) }
    var selectedPointCoords by remember { mutableStateOf<Pair<Double, Double>?>(null) }

    // Keep reticle synced with externally selected zone or haven
    LaunchedEffect(selectedZone) {
        if (selectedZone != null) {
            selectedPointName = selectedZone.name
            selectedPointCoords = Pair(selectedZone.centerLat, selectedZone.centerLng)
        }
    }
    LaunchedEffect(selectedHaven) {
        if (selectedHaven != null) {
            selectedPointName = selectedHaven.name
            selectedPointCoords = Pair(selectedHaven.lat, selectedHaven.lng)
        }
    }

    // Auto-focus on route when activeRoute changes
    LaunchedEffect(activeRoute) {
        if (activeRoute != null && activeRoute.path.isNotEmpty()) {
            val origin = activeRoute.path.first()
            val dest = activeRoute.path.last()
            selectedPointName = "EVACUATION CORRIDOR"
            selectedPointCoords = Pair((origin.lat + dest.lat) / 2.0, (origin.lng + dest.lng) / 2.0)
            zoomScale = 1.25f
        }
    }

    // Radar sweep rotation animation
    val infiniteTransition = rememberInfiniteTransition(label = "RadarSweep")
    val radarAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "RadarAngle"
    )

    // Pulse animation for critical dead zones and path
    val pulseProgress by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseProgress"
    )

    // Animated dashed path travel offset
    val pathDashOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 60f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "DashOffset"
    )

    val textMeasurer = rememberTextMeasurer()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                when (mapMode) {
                    MapVisualMode.TACTICAL_HUD -> TacticalDarkBg
                    MapVisualMode.SATELLITE_RECON -> Color(0xFF040608)
                    MapVisualMode.FLIR_THERMAL -> Color(0xFF07000B)
                }
            )
            .pointerInput(Unit) {
                detectTapGestures { tapOffset ->
                    val w = size.width
                    val h = size.height

                    fun toScreen(lat: Double, lng: Double): Offset {
                        val normX = ((lng - minLng) / (maxLng - minLng)).toFloat()
                        val normY = (1.0f - ((lat - minLat) / (maxLat - minLat)).toFloat())
                        val cx = w / 2f
                        val cy = h / 2f
                        val mapX = (normX * w * 0.92f) + (w * 0.04f)
                        val mapY = (normY * h * 0.84f) + (h * 0.08f)
                        val screenX = cx + (mapX - cx + panOffsetX) * zoomScale
                        val screenY = cy + (mapY - cy + panOffsetY) * zoomScale
                        return Offset(screenX, screenY)
                    }

                    // Check tap on safe havens first
                    var tappedHaven: SafeHaven? = null
                    for (haven in safeHavens) {
                        val pos = toScreen(haven.lat, haven.lng)
                        val dist = (pos - tapOffset).getDistance()
                        if (dist < 44f * density) {
                            tappedHaven = haven
                            break
                        }
                    }

                    if (tappedHaven != null) {
                        selectedPointName = tappedHaven.name
                        selectedPointCoords = Pair(tappedHaven.lat, tappedHaven.lng)
                        onHavenSelected(tappedHaven)
                        return@detectTapGestures
                    }

                    // Check tap on threat zones
                    var tappedZone: ThreatZone? = null
                    for (zone in threatZones) {
                        val pos = toScreen(zone.centerLat, zone.centerLng)
                        val dist = (pos - tapOffset).getDistance()
                        if (dist < 48f * density) {
                            tappedZone = zone
                            break
                        }
                    }

                    if (tappedZone != null) {
                        selectedPointName = tappedZone.name
                        selectedPointCoords = Pair(tappedZone.centerLat, tappedZone.centerLng)
                        onZoneSelected(tappedZone)
                        return@detectTapGestures
                    }

                    // Check tap on cities
                    for (city in TacticalDataProvider.CITIES) {
                        val pos = toScreen(city.lat, city.lng)
                        val dist = (pos - tapOffset).getDistance()
                        if (dist < 36f * density) {
                            selectedPointName = city.name
                            selectedPointCoords = Pair(city.lat, city.lng)
                            return@detectTapGestures
                        }
                    }

                    // Tapping background clears reticle selection
                    selectedPointName = null
                    selectedPointCoords = null
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    panOffsetX += dragAmount.x / zoomScale
                    panOffsetY += dragAmount.y / zoomScale
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize().testTag("tactical_map_canvas")) {
            val canvasW = size.width
            val canvasH = size.height

            // Coordinate projection function
            fun geoToCanvas(lat: Double, lng: Double): Offset {
                val normX = ((lng - minLng) / (maxLng - minLng)).toFloat()
                val normY = (1.0f - ((lat - minLat) / (maxLat - minLat)).toFloat())
                val cx = canvasW / 2f
                val cy = canvasH / 2f
                val mapX = (normX * canvasW * 0.92f) + (canvasW * 0.04f)
                val mapY = (normY * canvasH * 0.84f) + (canvasH * 0.08f)
                val screenX = cx + (mapX - cx + panOffsetX) * zoomScale
                val screenY = cy + (mapY - cy + panOffsetY) * zoomScale
                return Offset(screenX, screenY)
            }

            // Draw Background Tactical Grid & Range Rings
            drawTacticalGrid(
                canvasW, canvasH, zoomScale, panOffsetX, panOffsetY,
                mapMode, textMeasurer
            )

            // Draw Iran Country Boundary Polygon Contour
            drawCountryPerimeter(TacticalDataProvider.IRAN_BORDER_POLYGON, ::geoToCanvas, mapMode)

            // Draw Active Conflict / Dead Zones
            if (showDeadZones) {
                drawThreatZones(
                    threatZones = threatZones,
                    geoToCanvas = ::geoToCanvas,
                    zoomScale = zoomScale,
                    pulseProgress = pulseProgress,
                    mapMode = mapMode,
                    textMeasurer = textMeasurer
                )
            }

            // Draw Safe Evacuation Corridors
            if (activeRoute != null && activeRoute.path.size >= 2) {
                drawEvacuationRoute(
                    route = activeRoute,
                    geoToCanvas = ::geoToCanvas,
                    dashOffset = pathDashOffset,
                    pulseProgress = pulseProgress,
                    textMeasurer = textMeasurer
                )
            }

            // Draw Major Urban Strategic Nodes (Cities)
            drawUrbanNodes(
                cities = TacticalDataProvider.CITIES,
                geoToCanvas = ::geoToCanvas,
                zoomScale = zoomScale,
                mapMode = mapMode,
                textMeasurer = textMeasurer
            )

            // Draw Safe Havens & Border Crossings
            if (showSafeHavens) {
                drawSafeHavens(
                    safeHavens = safeHavens,
                    geoToCanvas = ::geoToCanvas,
                    zoomScale = zoomScale,
                    pulseProgress = pulseProgress,
                    textMeasurer = textMeasurer
                )
            }

            // Draw Animated Radar Sweep if enabled
            if (showRadarSweep) {
                drawRadarSweep(
                    canvasW = canvasW,
                    canvasH = canvasH,
                    angleDegrees = radarAngle,
                    mapMode = mapMode
                )
            }

            // Draw Targeting Reticle if a point is selected
            if (selectedPointCoords != null) {
                val reticlePos = geoToCanvas(selectedPointCoords!!.first, selectedPointCoords!!.second)
                drawTargetingReticle(
                    center = reticlePos,
                    label = selectedPointName ?: "TARGET",
                    lat = selectedPointCoords!!.first,
                    lng = selectedPointCoords!!.second,
                    textMeasurer = textMeasurer
                )
            }
        }

        // Top Left Tactical Telemetry HUD
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 16.dp, top = 16.dp)
                .background(TacticalSurface.copy(alpha = 0.88f), RoundedCornerShape(6.dp))
                .clip(RoundedCornerShape(6.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(DivisionOrange, shape = RoundedCornerShape(4.dp))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "ISAC TACTICAL EYE",
                    style = MaterialTheme.typography.labelLarge,
                    color = DivisionOrange
                )
            }
            Text(
                text = "THEATER: IRAN CRISIS CORRIDOR",
                style = MaterialTheme.typography.bodySmall,
                color = TacticalTextSecondary
            )
            Text(
                text = "MAG: ${String.format("%.1fx", zoomScale)} // THREATS: ${threatZones.size} ACTIVE",
                style = MaterialTheme.typography.labelSmall,
                color = TacticalCyan
            )
        }

        // Floating Map Control Action Buttons
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Zoom In
            FilledIconButton(
                onClick = { zoomScale = (zoomScale * 1.25f).coerceAtMost(4.5f) },
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = TacticalSurfaceElevated.copy(alpha = 0.9f),
                    contentColor = DivisionOrange
                ),
                modifier = Modifier.size(44.dp).testTag("zoom_in_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Zoom In")
            }

            // Zoom Out
            FilledIconButton(
                onClick = { zoomScale = (zoomScale / 1.25f).coerceAtLeast(0.6f) },
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = TacticalSurfaceElevated.copy(alpha = 0.9f),
                    contentColor = DivisionOrange
                ),
                modifier = Modifier.size(44.dp).testTag("zoom_out_button")
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Zoom Out")
            }

            // Recenter Tehran
            FilledIconButton(
                onClick = {
                    zoomScale = 1.0f
                    panOffsetX = 0f
                    panOffsetY = 0f
                    selectedPointName = "Tehran"
                    selectedPointCoords = Pair(35.6892, 51.3890)
                },
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = TacticalSurfaceElevated.copy(alpha = 0.9f),
                    contentColor = TacticalCyan
                ),
                modifier = Modifier.size(44.dp).testTag("recenter_button")
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = "Center Tehran")
            }
        }
    }
}

// Draw Tactical Coordinate Grid and HUD markings
private fun DrawScope.drawTacticalGrid(
    width: Float,
    height: Float,
    zoomScale: Float,
    panX: Float,
    panY: Float,
    mode: MapVisualMode,
    textMeasurer: TextMeasurer
) {
    val gridColor = when (mode) {
        MapVisualMode.TACTICAL_HUD -> TacticalGridLines
        MapVisualMode.SATELLITE_RECON -> Color(0x1000E5FF)
        MapVisualMode.FLIR_THERMAL -> Color(0x18FF3D00)
    }

    val step = 70f * zoomScale
    val startX = (panX * zoomScale % step + step) % step
    val startY = (panY * zoomScale % step + step) % step

    var x = startX
    while (x < width) {
        drawLine(
            color = gridColor,
            start = Offset(x, 0f),
            end = Offset(x, height),
            strokeWidth = 1f
        )
        x += step
    }

    var y = startY
    while (y < height) {
        drawLine(
            color = gridColor,
            start = Offset(0f, y),
            end = Offset(width, y),
            strokeWidth = 1f
        )
        y += step
    }

    // Concentric Range Rings from Center
    val cx = width / 2f
    val cy = height / 2f
    val ringColor = when (mode) {
        MapVisualMode.TACTICAL_HUD -> TacticalGridLinesOrange
        MapVisualMode.SATELLITE_RECON -> Color(0x0E00E5FF)
        MapVisualMode.FLIR_THERMAL -> Color(0x15FF6A00)
    }

    val radii = listOf(140f, 280f, 440f, 620f)
    for (r in radii) {
        drawCircle(
            color = ringColor,
            radius = r * zoomScale,
            center = Offset(cx, cy),
            style = Stroke(
                width = 1.2f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f)
            )
        )
    }
}

// Draw Country Perimeter
private fun DrawScope.drawCountryPerimeter(
    points: List<GeoPoint>,
    geoToCanvas: (Double, Double) -> Offset,
    mode: MapVisualMode
) {
    if (points.size < 3) return

    val path = Path()
    val first = geoToCanvas(points[0].lat, points[0].lng)
    path.moveTo(first.x, first.y)

    for (i in 1 until points.size) {
        val p = geoToCanvas(points[i].lat, points[i].lng)
        path.lineTo(p.x, p.y)
    }
    path.close()

    // Faint territory fill
    val fillColor = when (mode) {
        MapVisualMode.TACTICAL_HUD -> Color(0x0CFF6A00)
        MapVisualMode.SATELLITE_RECON -> Color(0x0A00E5FF)
        MapVisualMode.FLIR_THERMAL -> Color(0x123A0042)
    }
    drawPath(path, color = fillColor)

    // Border line stroke
    val strokeColor = when (mode) {
        MapVisualMode.TACTICAL_HUD -> DivisionOrange.copy(alpha = 0.55f)
        MapVisualMode.SATELLITE_RECON -> TacticalCyan.copy(alpha = 0.45f)
        MapVisualMode.FLIR_THERMAL -> Color(0xFFFF5252).copy(alpha = 0.5f)
    }
    drawPath(
        path,
        color = strokeColor,
        style = Stroke(
            width = 2.2f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 6f), 0f)
        )
    )
}

// Draw Threat & Dead Zones with pulsing holographic danger rings
private fun DrawScope.drawThreatZones(
    threatZones: List<ThreatZone>,
    geoToCanvas: (Double, Double) -> Offset,
    zoomScale: Float,
    pulseProgress: Float,
    mapMode: MapVisualMode,
    textMeasurer: TextMeasurer
) {
    for (zone in threatZones) {
        val center = geoToCanvas(zone.centerLat, zone.centerLng)
        // Convert radiusKm into approximate screen radius
        val baseRadiusPx = (zone.radiusKm * 1.6f * zoomScale).toFloat().coerceIn(24f, 220f)
        val pulsingRadiusPx = baseRadiusPx * pulseProgress

        val zoneColor = when (zone.level) {
            ThreatLevel.CRITICAL_ALPHA -> ThreatCriticalRed
            ThreatLevel.HIGH_BRAVO -> ThreatHighOrange
            ThreatLevel.MODERATE_CHARLIE -> ThreatModerateYellow
            ThreatLevel.CAUTION_DELTA -> TacticalCyan
        }

        // Radial hazard fill
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    zoneColor.copy(alpha = 0.38f),
                    zoneColor.copy(alpha = 0.14f),
                    Color.Transparent
                ),
                center = center,
                radius = pulsingRadiusPx
            ),
            center = center,
            radius = pulsingRadiusPx
        )

        // Pulsing hazard outer ring
        drawCircle(
            color = zoneColor.copy(alpha = 0.75f),
            center = center,
            radius = pulsingRadiusPx,
            style = Stroke(
                width = 1.8f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f)
            )
        )

        // Solid core engagement circle
        drawCircle(
            color = zoneColor,
            center = center,
            radius = baseRadiusPx * 0.45f,
            style = Stroke(width = 2f)
        )

        // Center threat node marker
        drawCircle(color = zoneColor, radius = 5f, center = center)

        // Sector label
        val labelResult = textMeasurer.measure(
            text = AnnotatedString("${zone.sectorCode}\n${zone.name.take(18)}"),
            style = TextStyle(
                color = zoneColor,
                fontSize = 9.sp,
                fontFamily = ChakraPetchFontFamily
            )
        )
        drawText(
            textLayoutResult = labelResult,
            topLeft = Offset(center.x - labelResult.size.width / 2f, center.y + 8f)
        )
    }
}

// Draw Safe Evacuation Corridor
private fun DrawScope.drawEvacuationRoute(
    route: TacticalRoute,
    geoToCanvas: (Double, Double) -> Offset,
    dashOffset: Float,
    pulseProgress: Float,
    textMeasurer: TextMeasurer
) {
    if (route.path.size < 2) return

    val path = Path()
    val screenPoints = route.path.map { geoToCanvas(it.lat, it.lng) }

    path.moveTo(screenPoints[0].x, screenPoints[0].y)
    for (i in 1 until screenPoints.size) {
        path.lineTo(screenPoints[i].x, screenPoints[i].y)
    }

    // Outer cyan glow
    drawPath(
        path = path,
        color = TacticalCyan.copy(alpha = 0.35f),
        style = Stroke(width = 10f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )

    // Animated directional pulse path
    drawPath(
        path = path,
        color = TacticalCyan,
        style = Stroke(
            width = 4.5f,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(24f, 16f), dashOffset)
        )
    )

    // Checkpoint markers along the route
    screenPoints.forEachIndexed { index, pt ->
        val isTerminal = index == 0 || index == screenPoints.size - 1
        val color = if (index == 0) DivisionOrange else if (index == screenPoints.size - 1) ThreatLowGreen else TacticalCyan
        val radius = if (isTerminal) 8f * pulseProgress else 5.5f

        drawCircle(color = color, radius = radius, center = pt)
        drawCircle(color = TacticalDarkBg, radius = radius * 0.4f, center = pt)

        val cpLabel = if (index == 0) "ORIGIN" else if (index == screenPoints.size - 1) "SAFE HAVEN" else "CP-$index"
        val labelResult = textMeasurer.measure(
            text = AnnotatedString(cpLabel),
            style = TextStyle(
                color = color,
                fontSize = 8.sp,
                fontFamily = ShareTechMonoFontFamily
            )
        )
        drawText(
            textLayoutResult = labelResult,
            topLeft = Offset(pt.x + 8f, pt.y - 12f)
        )
    }
}

// Draw Urban Nodes (Cities)
private fun DrawScope.drawUrbanNodes(
    cities: List<GeoPoint>,
    geoToCanvas: (Double, Double) -> Offset,
    zoomScale: Float,
    mapMode: MapVisualMode,
    textMeasurer: TextMeasurer
) {
    for (city in cities) {
        val pt = geoToCanvas(city.lat, city.lng)
        val isCapital = city.name == "Tehran"
        val nodeColor = if (isCapital) DivisionOrange else TacticalTextPrimary.copy(alpha = 0.85f)
        val size = if (isCapital) 6f else 4f

        // Draw tactical square diamond
        drawRect(
            color = nodeColor,
            topLeft = Offset(pt.x - size / 2, pt.y - size / 2),
            size = Size(size, size)
        )

        // Draw city name if zoom allows or if major
        if (zoomScale > 0.85f || isCapital) {
            val nameResult = textMeasurer.measure(
                text = AnnotatedString(city.name),
                style = TextStyle(
                    color = nodeColor,
                    fontSize = if (isCapital) 11.sp else 9.sp,
                    fontFamily = ChakraPetchFontFamily
                )
            )
            drawText(
                textLayoutResult = nameResult,
                topLeft = Offset(pt.x + 6f, pt.y - 7f)
            )
        }
    }
}

// Draw Safe Havens & Border Crossings
private fun DrawScope.drawSafeHavens(
    safeHavens: List<SafeHaven>,
    geoToCanvas: (Double, Double) -> Offset,
    zoomScale: Float,
    pulseProgress: Float,
    textMeasurer: TextMeasurer
) {
    for (haven in safeHavens) {
        val pt = geoToCanvas(haven.lat, haven.lng)
        val havenColor = ThreatLowGreen

        // Concentric beacon pulse
        drawCircle(
            color = havenColor.copy(alpha = 0.22f),
            radius = 24f * pulseProgress,
            center = pt
        )
        drawCircle(
            color = havenColor,
            radius = 16f,
            center = pt,
            style = Stroke(width = 1.5f)
        )
        drawCircle(color = havenColor, radius = 5f, center = pt)

        val nameResult = textMeasurer.measure(
            text = AnnotatedString("[+] ${haven.name}"),
            style = TextStyle(
                color = havenColor,
                fontSize = 10.sp,
                fontFamily = ChakraPetchFontFamily
            )
        )
        drawText(
            textLayoutResult = nameResult,
            topLeft = Offset(pt.x - nameResult.size.width / 2f, pt.y - 28f)
        )
    }
}

// Draw Animated Radar Sweep
private fun DrawScope.drawRadarSweep(
    canvasW: Float,
    canvasH: Float,
    angleDegrees: Float,
    mapMode: MapVisualMode
) {
    val cx = canvasW / 2f
    val cy = canvasH / 2f
    val radius = max(canvasW, canvasH) * 0.75f

    val sweepColor = when (mapMode) {
        MapVisualMode.TACTICAL_HUD -> DivisionOrange
        MapVisualMode.SATELLITE_RECON -> TacticalCyan
        MapVisualMode.FLIR_THERMAL -> Color(0xFFFF3D00)
    }

    rotate(angleDegrees, pivot = Offset(cx, cy)) {
        // Sweep trail fan
        drawArc(
            brush = Brush.sweepGradient(
                colors = listOf(
                    Color.Transparent,
                    sweepColor.copy(alpha = 0.18f),
                    sweepColor.copy(alpha = 0.35f)
                ),
                center = Offset(cx, cy)
            ),
            startAngle = -35f,
            sweepAngle = 35f,
            useCenter = true,
            topLeft = Offset(cx - radius, cy - radius),
            size = Size(radius * 2, radius * 2)
        )

        // Leading sweep laser line
        drawLine(
            color = sweepColor.copy(alpha = 0.85f),
            start = Offset(cx, cy),
            end = Offset(cx + radius, cy),
            strokeWidth = 2f
        )
    }
}

// Draw Targeting Reticle (Division ISAC style)
private fun DrawScope.drawTargetingReticle(
    center: Offset,
    label: String,
    lat: Double,
    lng: Double,
    textMeasurer: TextMeasurer
) {
    val r = 26f
    val reticleColor = DivisionOrange

    // Corner brackets
    val bracketLen = 10f
    // Top-Left
    drawLine(reticleColor, Offset(center.x - r, center.y - r), Offset(center.x - r + bracketLen, center.y - r), 2f)
    drawLine(reticleColor, Offset(center.x - r, center.y - r), Offset(center.x - r, center.y - r + bracketLen), 2f)
    // Top-Right
    drawLine(reticleColor, Offset(center.x + r, center.y - r), Offset(center.x + r - bracketLen, center.y - r), 2f)
    drawLine(reticleColor, Offset(center.x + r, center.y - r), Offset(center.x + r, center.y - r + bracketLen), 2f)
    // Bottom-Left
    drawLine(reticleColor, Offset(center.x - r, center.y + r), Offset(center.x - r + bracketLen, center.y + r), 2f)
    drawLine(reticleColor, Offset(center.x - r, center.y + r), Offset(center.x - r, center.y + r - bracketLen), 2f)
    // Bottom-Right
    drawLine(reticleColor, Offset(center.x + r, center.y + r), Offset(center.x + r - bracketLen, center.y + r), 2f)
    drawLine(reticleColor, Offset(center.x + r, center.y + r), Offset(center.x + r, center.y + r - bracketLen), 2f)

    // Center crosshair
    drawCircle(color = reticleColor, radius = 3f, center = center)

    // Coordinate readout
    val readout = "${String.format("%.3f", lat)}°N, ${String.format("%.3f", lng)}°E"
    val textResult = textMeasurer.measure(
        text = AnnotatedString("LOCK: $label\n$readout"),
        style = TextStyle(
            color = reticleColor,
            fontSize = 9.sp,
            fontFamily = ShareTechMonoFontFamily
        )
    )
    drawText(
        textLayoutResult = textResult,
        topLeft = Offset(center.x + r + 6f, center.y - 14f)
    )
}
