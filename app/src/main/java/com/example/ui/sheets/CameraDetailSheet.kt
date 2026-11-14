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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.CameraVulnerabilityStatus
import com.example.model.SurveillanceCamera
import com.example.routing.TacticalPathfinder
import com.example.ui.theme.*

@Composable
fun CameraDetailSheet(
    camera: SurveillanceCamera,
    userLat: Double,
    userLng: Double,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusColor = when (camera.vulnerabilityStatus) {
        CameraVulnerabilityStatus.VULNERABLE_PUBLIC_OSINT -> ThreatHighOrange
        CameraVulnerabilityStatus.HARDENED_STATE_ENCRYPTED -> ThreatCriticalRed
        CameraVulnerabilityStatus.OFFLINE_JAMMED -> ThreatLowGreen
    }

    val distanceMeters = TacticalPathfinder.calculateDistanceKm(userLat, userLng, camera.lat, camera.lng) * 1000.0
    val userInsideRange = camera.vulnerabilityStatus != CameraVulnerabilityStatus.OFFLINE_JAMMED &&
        distanceMeters <= camera.coverageRadiusMeters

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.5.dp, statusColor.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
            .testTag("camera_detail_sheet"),
        shape = RoundedCornerShape(12.dp),
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
                    Box(
                        modifier = Modifier
                            .background(statusColor, RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = camera.operatorType.shortTag,
                            style = MaterialTheme.typography.labelMedium,
                            color = TacticalDarkBg
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = camera.vulnerabilityStatus.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(36.dp).testTag("close_camera_sheet")
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = TacticalTextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = camera.name,
                style = MaterialTheme.typography.titleLarge,
                color = TacticalTextPrimary
            )
            Text(
                text = "OPERATOR: ${camera.operatorType.displayName.uppercase()}",
                style = MaterialTheme.typography.bodySmall,
                color = TacticalCyan
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TacticalSurfaceElevated, RoundedCornerShape(8.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("DISTANCE TO YOU", style = MaterialTheme.typography.labelSmall, color = TacticalTextDim)
                    Text(
                        if (distanceMeters < 1000) "${distanceMeters.toInt()} M" else "${String.format("%.1f", distanceMeters / 1000.0)} KM",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TacticalTextPrimary
                    )
                }
                Column {
                    Text("OPTICAL FOV", style = MaterialTheme.typography.labelSmall, color = TacticalTextDim)
                    Text(
                        "${camera.coverageRadiusMeters.toInt()}M / ${camera.fovAngleDegrees.toInt()}°",
                        style = MaterialTheme.typography.bodyMedium,
                        color = statusColor
                    )
                }
                Column {
                    Text("YOUR EXPOSURE", style = MaterialTheme.typography.labelSmall, color = TacticalTextDim)
                    Text(
                        if (userInsideRange) "VULNERABLE" else "INVULNERABLE",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (userInsideRange) ThreatCriticalRed else ThreatLowGreen
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(statusColor.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                    .border(1.dp, statusColor.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                    .padding(10.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "STREAM / PROTOCOL: ${camera.streamProtocol}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TacticalCyan
                    )
                    Text(
                        text = camera.vulnerabilityStatus.securityAssessment,
                        style = MaterialTheme.typography.bodySmall,
                        color = TacticalTextPrimary
                    )
                    Text(
                        text = "OSINT RECON: ${camera.osintNote}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TacticalTextSecondary
                    )
                }
            }
        }
    }
}
