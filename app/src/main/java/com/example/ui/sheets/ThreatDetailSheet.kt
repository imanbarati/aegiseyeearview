package com.example.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.ThreatLevel
import com.example.model.ThreatZone
import com.example.ui.theme.*

@Composable
fun ThreatDetailSheet(
    zone: ThreatZone,
    onDismiss: () -> Unit,
    onPlanBypass: (ThreatZone) -> Unit,
    modifier: Modifier = Modifier
) {
    val levelColor = when (zone.level) {
        ThreatLevel.CRITICAL_ALPHA -> ThreatCriticalRed
        ThreatLevel.HIGH_BRAVO -> ThreatHighOrange
        ThreatLevel.MODERATE_CHARLIE -> ThreatModerateYellow
        ThreatLevel.CAUTION_DELTA -> TacticalCyan
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.5.dp, levelColor.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
            .testTag("threat_detail_sheet"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = TacticalSurface)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            // Header: Sector Code & Close
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .background(levelColor, RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = zone.level.code,
                            style = MaterialTheme.typography.labelMedium,
                            color = TacticalDarkBg
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = zone.sectorCode,
                        style = MaterialTheme.typography.labelMedium,
                        color = levelColor
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(36.dp).testTag("close_threat_sheet")
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = TacticalTextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Title
            Text(
                text = zone.name,
                style = MaterialTheme.typography.titleLarge,
                color = TacticalTextPrimary
            )

            Text(
                text = "TYPE: ${zone.type.displayName.uppercase()}",
                style = MaterialTheme.typography.bodySmall,
                color = TacticalCyan
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Coordinates & Exclusion Radius Metrics
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TacticalSurfaceElevated, RoundedCornerShape(8.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("COORDINATES", style = MaterialTheme.typography.labelSmall, color = TacticalTextDim)
                    Text(
                        "${String.format("%.3f", zone.centerLat)}°N, ${String.format("%.3f", zone.centerLng)}°E",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TacticalTextPrimary
                    )
                }
                Column {
                    Text("DEAD ZONE RADIUS", style = MaterialTheme.typography.labelSmall, color = TacticalTextDim)
                    Text(
                        "${zone.radiusKm.toInt()} KM",
                        style = MaterialTheme.typography.bodyMedium,
                        color = levelColor
                    )
                }
                Column {
                    Text("MIN BYPASS BUFFER", style = MaterialTheme.typography.labelSmall, color = TacticalTextDim)
                    Text(
                        "+${zone.bypassBufferKm.toInt()} KM",
                        style = MaterialTheme.typography.bodyMedium,
                        color = DivisionOrange
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Tactical Description
            Text(
                text = "ISAC RECON DOSSIER",
                style = MaterialTheme.typography.labelSmall,
                color = DivisionOrange
            )
            Text(
                text = zone.description,
                style = MaterialTheme.typography.bodyMedium,
                color = TacticalTextSecondary
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Tactical SitRep
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(levelColor.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                    .border(1.dp, levelColor.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                    .padding(10.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = "Alert",
                            tint = levelColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "LIVE SITREP // ${zone.lastPingMinutesAgo}M AGO",
                            style = MaterialTheme.typography.labelSmall,
                            color = levelColor
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = zone.tacticalSitRep,
                        style = MaterialTheme.typography.bodySmall,
                        color = TacticalTextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "ESTIMATED CASUALTY FACTOR: ${zone.casualtyRisk}",
                        style = MaterialTheme.typography.labelSmall,
                        color = levelColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Button
            Button(
                onClick = { onPlanBypass(zone) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("plan_bypass_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DivisionOrange,
                    contentColor = TacticalDarkBg
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.AltRoute, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("CALCULATE SAFE EVACUATION BYPASS", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
