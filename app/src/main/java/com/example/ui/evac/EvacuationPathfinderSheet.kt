package com.example.ui.evac

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.TacticalDataProvider
import com.example.model.TacticalRoute
import com.example.ui.theme.*

@Composable
fun EvacuationPathfinderSheet(
    activeRoute: TacticalRoute,
    originCity: String,
    destinationHavenId: String?,
    standoffBufferKm: Double,
    avoidCctvCameras: Boolean,
    isNavigationTrackingActive: Boolean,
    onOriginChanged: (String) -> Unit,
    onDestinationChanged: (String?) -> Unit,
    onBufferChanged: (Double) -> Unit,
    onToggleAvoidCctv: () -> Unit,
    onCalculateNearestSafeWalk: () -> Unit,
    onToggleTracking: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isDirectivesExpanded by remember { mutableStateOf(true) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, TacticalCyan.copy(alpha = 0.5f), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .testTag("evac_pathfinder_sheet"),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        colors = CardDefaults.cardColors(containerColor = TacticalSurface)
    ) {
        LazyColumn(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .background(ThreatLowGreen, RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "A* WALKING",
                                style = MaterialTheme.typography.labelMedium,
                                color = TacticalDarkBg
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "HEATMAP SAFE PATHFINDER",
                            style = MaterialTheme.typography.titleMedium,
                            color = TacticalTextPrimary
                        )
                    }

                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(36.dp).testTag("close_pathfinder_button")
                    ) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Minimize", tint = TacticalTextSecondary)
                    }
                }
            }

            // Primary Auto-Route to Nearest Safe Zone Button
            item {
                Button(
                    onClick = onCalculateNearestSafeWalk,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("nearest_safe_zone_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ThreatLowGreen,
                        contentColor = TacticalDarkBg
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.DirectionsWalk, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ROUTE TO NEAREST SAFE ZONE (AUTO A*)",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            // Origin Sector / GPS Anchor
            item {
                Column {
                    Text(
                        text = "START LOCATION / SIMULATED SECTOR",
                        style = MaterialTheme.typography.labelSmall,
                        color = DivisionOrange
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val popularCities = listOf("Live GPS", "Tehran", "Isfahan", "Tabriz", "Shiraz", "Ahvaz", "Mashhad")
                        items(popularCities) { city ->
                            val isSelected = city.equals(originCity, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) DivisionOrange else TacticalSurfaceElevated)
                                    .clickable { onOriginChanged(city) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                                    .testTag("origin_chip_$city")
                            ) {
                                Text(
                                    text = city,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isSelected) TacticalDarkBg else TacticalTextPrimary
                                )
                            }
                        }
                    }
                }
            }

            // Destination Safe Zone Selector
            item {
                Column {
                    Text(
                        text = "DESIGNATED SAFE ZONES (AUTO = NEAREST)",
                        style = MaterialTheme.typography.labelSmall,
                        color = ThreatLowGreen
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            val isAuto = destinationHavenId == null
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isAuto) ThreatLowGreen else TacticalSurfaceElevated)
                                    .clickable { onDestinationChanged(null) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                                    .testTag("destination_chip_auto")
                            ) {
                                Text(
                                    text = "NEAREST AUTO",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isAuto) TacticalDarkBg else TacticalTextPrimary
                                )
                            }
                        }
                        items(TacticalDataProvider.SAFE_HAVENS) { haven ->
                            val isSelected = haven.id == destinationHavenId
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) ThreatLowGreen else TacticalSurfaceElevated)
                                    .clickable { onDestinationChanged(haven.id) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                                    .testTag("destination_chip_${haven.id}")
                            ) {
                                Text(
                                    text = haven.name.take(22),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isSelected) TacticalDarkBg else TacticalTextPrimary
                                )
                            }
                        }
                    }
                }
            }

            // CCTV Stealth Routing + Heatmap Weighting Controls
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TacticalSurfaceElevated, RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "AVOID PUBLIC OSINT & STATE CCTV",
                                style = MaterialTheme.typography.labelSmall,
                                color = TacticalCyan
                            )
                            Text(
                                text = "Detours A* walking path around active camera optical cones",
                                style = MaterialTheme.typography.bodySmall,
                                color = TacticalTextDim
                            )
                        }
                        Switch(
                            checked = avoidCctvCameras,
                            onCheckedChange = { onToggleAvoidCctv() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = TacticalCyan,
                                checkedTrackColor = TacticalCyanGlow
                            ),
                            modifier = Modifier.testTag("avoid_cctv_switch")
                        )
                    }

                    Divider(color = TacticalBorder)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "HEATMAP PENALTY WEIGHT",
                            style = MaterialTheme.typography.labelSmall,
                            color = DivisionOrange
                        )
                        Text(
                            text = "${standoffBufferKm.toInt()}x",
                            style = MaterialTheme.typography.labelMedium,
                            color = DivisionOrange
                        )
                    }
                    Slider(
                        value = standoffBufferKm.toFloat(),
                        onValueChange = { onBufferChanged(it.toDouble()) },
                        valueRange = 10f..50f,
                        steps = 7,
                        colors = SliderDefaults.colors(
                            thumbColor = DivisionOrange,
                            activeTrackColor = DivisionOrange,
                            inactiveTrackColor = TacticalBorder
                        ),
                        modifier = Modifier.testTag("buffer_slider")
                    )
                }
            }

            // Walking Route Telemetry Stats
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = TacticalSurfaceElevated),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("WALK DIST", style = MaterialTheme.typography.labelSmall, color = TacticalTextDim)
                            Text(
                                "${activeRoute.distanceKm} KM",
                                style = MaterialTheme.typography.titleMedium,
                                color = TacticalTextPrimary
                            )
                        }
                    }

                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = TacticalSurfaceElevated),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("EST. WALK", style = MaterialTheme.typography.labelSmall, color = TacticalTextDim)
                            Text(
                                if (activeRoute.estWalkMinutes < 120) "${activeRoute.estWalkMinutes} MIN"
                                else "${activeRoute.estWalkMinutes / 60}H ${activeRoute.estWalkMinutes % 60}M",
                                style = MaterialTheme.typography.titleMedium,
                                color = DivisionOrange
                            )
                        }
                    }

                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = TacticalSurfaceElevated),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("SAFETY", style = MaterialTheme.typography.labelSmall, color = TacticalTextDim)
                            Text(
                                "${100 - activeRoute.riskScore}/100",
                                style = MaterialTheme.typography.titleMedium,
                                color = ThreatLowGreen
                            )
                        }
                    }
                }
            }

            // Peak Heatmap Exposure & Cameras Avoided Banner
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TacticalSurfaceElevated, RoundedCornerShape(8.dp))
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "PEAK HEATMAP: ${(activeRoute.peakHeatmapIntensity * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (activeRoute.peakHeatmapIntensity > 0.6f) ThreatCriticalRed else ThreatLowGreen
                    )
                    Text(
                        text = "CAMERAS BYPASSED: ${activeRoute.cctvCamerasAvoided}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TacticalCyan
                    )
                }
            }

            // Risk Classification
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (activeRoute.riskScore < 30) ThreatLowGreen.copy(alpha = 0.12f)
                            else ThreatHighOrange.copy(alpha = 0.12f),
                            RoundedCornerShape(6.dp)
                        )
                        .border(
                            1.dp,
                            if (activeRoute.riskScore < 30) ThreatLowGreen.copy(alpha = 0.4f)
                            else ThreatHighOrange.copy(alpha = 0.4f),
                            RoundedCornerShape(6.dp)
                        )
                        .padding(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (activeRoute.riskScore < 30) Icons.Default.Shield else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (activeRoute.riskScore < 30) ThreatLowGreen else ThreatHighOrange,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = activeRoute.riskClassification,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (activeRoute.riskScore < 30) ThreatLowGreen else ThreatHighOrange
                        )
                    }
                }
            }

            // Heatmap Hotspots Bypassed
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TacticalSurfaceElevated, RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "THREAT HEATMAP CLUSTERS AVOIDED (${activeRoute.deadZonesAvoided.size})",
                        style = MaterialTheme.typography.labelSmall,
                        color = ThreatCriticalRed
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    for (hazard in activeRoute.deadZonesAvoided) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = ThreatLowGreen,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = hazard,
                                style = MaterialTheme.typography.bodySmall,
                                color = TacticalTextSecondary
                            )
                        }
                    }
                }
            }

            // Turn-by-turn Directives
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TacticalSurfaceElevated, RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isDirectivesExpanded = !isDirectivesExpanded },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "A* WALKING CORRIDOR DIRECTIVES",
                            style = MaterialTheme.typography.labelSmall,
                            color = TacticalCyan
                        )
                        Icon(
                            if (isDirectivesExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = TacticalCyan
                        )
                    }

                    AnimatedVisibility(visible = isDirectivesExpanded) {
                        Column(
                            modifier = Modifier.padding(top = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            activeRoute.tacticalDirectives.forEachIndexed { idx, directive ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .background(TacticalCyan.copy(alpha = 0.2f), RoundedCornerShape(4.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "${idx + 1}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TacticalCyan
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = directive,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TacticalTextPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Engage HUD Button
            item {
                Button(
                    onClick = onToggleTracking,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("toggle_tracking_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isNavigationTrackingActive) ThreatCriticalRed else TacticalCyan,
                        contentColor = TacticalDarkBg
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        if (isNavigationTrackingActive) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isNavigationTrackingActive) "DISENGAGE WALKING HUD" else "ENGAGE CRISIS WALKING HUD",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}
