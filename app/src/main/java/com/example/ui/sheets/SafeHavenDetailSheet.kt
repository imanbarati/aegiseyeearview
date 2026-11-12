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
import com.example.model.SafeHaven
import com.example.ui.theme.*

@Composable
fun SafeHavenDetailSheet(
    haven: SafeHaven,
    onDismiss: () -> Unit,
    onSetDestination: (SafeHaven) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.5.dp, ThreatLowGreen.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
            .testTag("safe_haven_detail_sheet"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = TacticalSurface)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            // Header
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
                            text = "SECURE TERMINAL",
                            style = MaterialTheme.typography.labelMedium,
                            color = TacticalDarkBg
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = haven.type.title.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = TacticalCyan
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(36.dp).testTag("close_haven_sheet")
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = TacticalTextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Title
            Text(
                text = haven.name,
                style = MaterialTheme.typography.titleLarge,
                color = TacticalTextPrimary
            )
            Text(
                text = "DESTINATION CORRIDOR: ${haven.countryOrRegion}",
                style = MaterialTheme.typography.bodySmall,
                color = TacticalTextSecondary
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Status Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ThreatLowGreen.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                    .border(1.dp, ThreatLowGreen.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                    .padding(10.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Status",
                            tint = ThreatLowGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = haven.borderStatus,
                            style = MaterialTheme.typography.labelMedium,
                            color = ThreatLowGreen
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = haven.clearanceLevel,
                        style = MaterialTheme.typography.bodySmall,
                        color = TacticalTextPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Supplies Available
            Text(
                text = "HUMANITARIAN RELIEF AVAILABLE",
                style = MaterialTheme.typography.labelSmall,
                color = TacticalCyan
            )
            Spacer(modifier = Modifier.height(4.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (supply in haven.supplies) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Done,
                            contentDescription = null,
                            tint = TacticalCyan,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = supply,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TacticalTextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Radio Channel
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TacticalSurfaceElevated, RoundedCornerShape(6.dp))
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Radio, contentDescription = "Radio", tint = DivisionAmber, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text("EMERGENCY EVAC COMMS FREQUENCY", style = MaterialTheme.typography.labelSmall, color = TacticalTextDim)
                    Text(haven.contactFreq, style = MaterialTheme.typography.bodyMedium, color = DivisionAmber)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Advisory Notes
            Text(
                text = "TACTICAL ADVISORY:",
                style = MaterialTheme.typography.labelSmall,
                color = DivisionOrange
            )
            Text(
                text = haven.advisories,
                style = MaterialTheme.typography.bodySmall,
                color = TacticalTextSecondary
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Action Button
            Button(
                onClick = { onSetDestination(haven) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("set_destination_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ThreatLowGreen,
                    contentColor = TacticalDarkBg
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("PLOT SAFE ROUTE TO THIS DESTINATION", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
