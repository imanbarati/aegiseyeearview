package com.example.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.CameraVulnerabilityStatus
import com.example.model.SurveillanceCamera
import com.example.model.UserSurveillanceExposure
import com.example.routing.TacticalPathfinder
import com.example.ui.theme.*

@Composable
fun NearbyCamerasSheet(
    exposure: UserSurveillanceExposure,
    cameras: List<SurveillanceCamera>,
    userLat: Double,
    userLng: Double,
    onSelectCamera: (SurveillanceCamera) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sortedCameras = cameras.map { cam ->
        cam to (TacticalPathfinder.calculateDistanceKm(userLat, userLng, cam.lat, cam.lng) * 1000.0)
    }.sortedBy { it.second }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.5.dp, TacticalCyan.copy(alpha = 0.6f), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .testTag("nearby_cameras_sheet"),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        colors = CardDefaults.cardColors(containerColor = TacticalSurface)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (exposure.isExposed) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = null,
                        tint = if (exposure.isExposed) ThreatCriticalRed else ThreatLowGreen,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "OSINT & STATE CCTV SCANNER",
                        style = MaterialTheme.typography.titleMedium,
                        color = TacticalTextPrimary
                    )
                }

                IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Close", tint = TacticalTextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Personal Vulnerability / Invulnerability Summary Banner
            val bannerColor = if (exposure.isExposed) ThreatCriticalRed else ThreatLowGreen
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bannerColor.copy(alpha = 0.14f), RoundedCornerShape(8.dp))
                    .border(1.dp, bannerColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = exposure.statusBadge,
                        style = MaterialTheme.typography.labelLarge,
                        color = bannerColor
                    )
                    Text(
                        text = exposure.statusSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = TacticalTextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "PUBLIC OSINT (<3.5km): ${exposure.vulnerablePublicNearbyCount}",
                            style = MaterialTheme.typography.labelSmall,
                            color = ThreatHighOrange
                        )
                        Text(
                            text = "STATE ENCRYPTED: ${exposure.stateEncryptedNearbyCount}",
                            style = MaterialTheme.typography.labelSmall,
                            color = ThreatCriticalRed
                        )
                        Text(
                            text = "BLIND SPOTS: ${exposure.offlineBlindSpotsNearbyCount}",
                            style = MaterialTheme.typography.labelSmall,
                            color = ThreatLowGreen
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier.fillMaxHeight(0.65f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(sortedCameras, key = { it.first.id }) { (cam, distMeters) ->
                    val statusColor = when (cam.vulnerabilityStatus) {
                        CameraVulnerabilityStatus.VULNERABLE_PUBLIC_OSINT -> ThreatHighOrange
                        CameraVulnerabilityStatus.HARDENED_STATE_ENCRYPTED -> ThreatCriticalRed
                        CameraVulnerabilityStatus.OFFLINE_JAMMED -> ThreatLowGreen
                    }
                    val inRange = cam.vulnerabilityStatus != CameraVulnerabilityStatus.OFFLINE_JAMMED &&
                        distMeters <= cam.coverageRadiusMeters

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(TacticalSurfaceElevated, RoundedCornerShape(8.dp))
                            .border(1.dp, statusColor.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                            .clickable { onSelectCamera(cam) }
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .background(statusColor, RoundedCornerShape(3.dp))
                                        .padding(horizontal = 5.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = cam.operatorType.shortTag,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TacticalDarkBg
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = cam.vulnerabilityStatus.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = statusColor
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = cam.name,
                                style = MaterialTheme.typography.titleSmall,
                                color = TacticalTextPrimary
                            )
                            Text(
                                text = cam.streamProtocol,
                                style = MaterialTheme.typography.bodySmall,
                                color = TacticalTextSecondary
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = if (distMeters < 1000) "${distMeters.toInt()}m" else "${String.format("%.1f", distMeters / 1000.0)}km",
                                style = MaterialTheme.typography.titleSmall,
                                color = TacticalCyan
                            )
                            Text(
                                text = if (inRange) "IN FOV" else "CLEAR",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (inRange) ThreatCriticalRed else ThreatLowGreen
                            )
                        }
                    }
                }
            }
        }
    }
}
