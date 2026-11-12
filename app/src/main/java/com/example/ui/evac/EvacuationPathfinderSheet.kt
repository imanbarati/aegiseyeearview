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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.TacticalDataProvider
import com.example.model.SafeHaven
import com.example.model.TacticalRoute
import com.example.ui.theme.*

@Composable
fun EvacuationPathfinderSheet(
    activeRoute: TacticalRoute,
    originCity: String,
    destinationHavenId: String,
    standoffBufferKm: Double,
    isNavigationTrackingActive: Boolean,
    onOriginChanged: (String) -> Unit,
    onDestinationChanged: (String) -> Unit,
    onBufferChanged: (Double) -> Unit,
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
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
                                .background(TacticalCyan, RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "PATHFINDER",
                                style = MaterialTheme.typography.labelMedium,
                                color = TacticalDarkBg
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "CRISIS EVACUATION CALCULATOR",
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

            // Origin Selector
            item {
                Column {
                    Text(
                        text = "ORIGIN / CURRENT LOCATION",
                        style = MaterialTheme.typography.labelSmall,
                        color = DivisionOrange
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val popularCities = listOf("Tehran", "Isfahan", "Tabriz", "Shiraz", "Ahvaz", "Mashhad", "Kermanshah")
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

            // Destination Selector
            item {
                Column {
                    Text(
                        text = "DESTINATION SAFE HAVEN / BORDER",
                        style = MaterialTheme.typography.labelSmall,
                        color = ThreatLowGreen
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                    text = haven.name.take(20),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isSelected) TacticalDarkBg else TacticalTextPrimary
                                )
                            }
                        }
                    }
                }
            }

            // Threat Buffer Clearance Slider
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TacticalSurfaceElevated, RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "DEAD ZONE STANDOFF BUFFER",
                            style = MaterialTheme.typography.labelSmall,
                            color = TacticalCyan
                        )
                        Text(
                            text = "${standoffBufferKm.toInt()} KM",
                            style = MaterialTheme.typography.labelMedium,
                            color = TacticalCyan
                        )
                    }
                    Slider(
                        value = standoffBufferKm.toFloat(),
                        onValueChange = { onBufferChanged(it.toDouble()) },
                        valueRange = 15f..60f,
                        steps = 8,
                        colors = SliderDefaults.colors(
                            thumbColor = TacticalCyan,
                            activeTrackColor = TacticalCyan,
                            inactiveTrackColor = TacticalBorder
                        ),
                        modifier = Modifier.testTag("buffer_slider")
                    )
                    Text(
                        text = "ISAC routes maintain an extra ${standoffBufferKm.toInt()}km safety margin outside kinetic and radar kill zones.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TacticalTextDim
                    )
                }
            }

            // Route Metric Stats Cards
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
                            Text("TOTAL DISTANCE", style = MaterialTheme.typography.labelSmall, color = TacticalTextDim)
                            Text(
                                "${activeRoute.distanceKm.toInt()} KM",
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
                            Text("EST. DRIVE TIME", style = MaterialTheme.typography.labelSmall, color = TacticalTextDim)
                            Text(
                                "${activeRoute.estDriveHours} HRS",
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
                            Text("SAFETY RATING", style = MaterialTheme.typography.labelSmall, color = TacticalTextDim)
                            Text(
                                "${100 - activeRoute.riskScore}/100",
                                style = MaterialTheme.typography.titleMedium,
                                color = ThreatLowGreen
                            )
                        }
                    }
                }
            }

            // Risk Assessment Badge
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

            // Conflict Dead Zones Bypassed
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TacticalSurfaceElevated, RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "LETHAL DEAD ZONES CIRCUMNAVIGATED (${activeRoute.deadZonesAvoided.size})",
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

            // Turn-by-turn Crisis Directives
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
                            text = "TURN-BY-TURN TACTICAL EVACUATION DIRECTIVES",
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

            // Primary Call to Action Button: Activate Real-Time Navigation
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
                        text = if (isNavigationTrackingActive) "DISENGAGE CRISIS TRACKING" else "ENGAGE CRISIS NAVIGATION HUD",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}
