package com.example.ui.sheets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.SosBroadcastEntity
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Emergency SOS Broadcast & Quick Sharing sheet backed by the local Room SQLite database
 * and configurable Online Yellow Page directory HTTP publishing + Android Share Intent.
 */
@Composable
fun SosBroadcastSheet(
    isSosPulseActive: Boolean,
    currentLat: Double,
    currentLng: Double,
    yellowPageUrl: String,
    callsign: String,
    emergencyNote: String,
    savedBroadcasts: List<SosBroadcastEntity>,
    onYellowPageUrlChanged: (String) -> Unit,
    onCallsignChanged: (String) -> Unit,
    onEmergencyNoteChanged: (String) -> Unit,
    onTriggerSosBroadcast: () -> Unit,
    onStopSosPulse: () -> Unit,
    onQuickShareEntry: (SosBroadcastEntity) -> Unit,
    onClearHistory: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val timeFormatter = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp)
            .border(1.5.dp, ThreatCriticalRed.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
            .testTag("sos_broadcast_sheet"),
        colors = CardDefaults.cardColors(containerColor = TacticalSurface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(if (isSosPulseActive) ThreatCriticalRed else DivisionOrange, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "SOS BROADCAST & YELLOW PAGE",
                            style = MaterialTheme.typography.titleSmall,
                            color = ThreatCriticalRed,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "GPS: ${String.format("%.5f", currentLat)}°N, ${String.format("%.5f", currentLng)}°E",
                            style = MaterialTheme.typography.labelSmall,
                            color = TacticalCyan
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close SOS Broadcast Sheet", tint = TacticalTextSecondary)
                }
            }

            // Honest notice about cloud provisioning vs local DB + configurable online Yellow Page URL
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TacticalSurfaceElevated, RoundedCornerShape(8.dp))
                    .border(1.dp, TacticalBorder, RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                Text(
                    text = "Saves every SOS locally in Room SQLite DB for offline access & quick sharing. Optionally specify an Online Emergency Yellow Page HTTPS/Webhook URL below to publish live.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TacticalTextSecondary
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = callsign,
                    onValueChange = onCallsignChanged,
                    label = { Text("Callsign", style = MaterialTheme.typography.labelSmall) },
                    singleLine = true,
                    modifier = Modifier.weight(0.9f)
                )
                OutlinedTextField(
                    value = emergencyNote,
                    onValueChange = onEmergencyNoteChanged,
                    label = { Text("Emergency Note", style = MaterialTheme.typography.labelSmall) },
                    singleLine = true,
                    modifier = Modifier.weight(1.4f)
                )
            }

            OutlinedTextField(
                value = yellowPageUrl,
                onValueChange = onYellowPageUrlChanged,
                label = { Text("Online Yellow Page Endpoint URL (HTTPS / Webhook)", style = MaterialTheme.typography.labelSmall) },
                placeholder = { Text("https://your-emergency-directory.org/api/sos", style = MaterialTheme.typography.labelSmall) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("yellow_page_url_input")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onTriggerSosBroadcast,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ThreatCriticalRed,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.weight(1.4f).testTag("confirm_sos_broadcast_button")
                ) {
                    Icon(Icons.Default.Campaign, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("BROADCAST SOS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }

                if (isSosPulseActive) {
                    OutlinedButton(
                        onClick = onStopSosPulse,
                        border = BorderStroke(1.dp, DivisionOrange),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = DivisionOrange),
                        modifier = Modifier.weight(0.9f).testTag("stop_sos_pulse_button")
                    ) {
                        Text("STOP PULSE", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            // Saved Local DB + Yellow Page Broadcast Log
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "LOCAL DB & YELLOW PAGE LOG (${savedBroadcasts.size})",
                    style = MaterialTheme.typography.labelSmall,
                    color = TacticalCyan
                )
                if (savedBroadcasts.isNotEmpty()) {
                    TextButton(onClick = onClearHistory, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)) {
                        Text("CLEAR", style = MaterialTheme.typography.labelSmall, color = TacticalTextDim)
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.heightIn(max = 170.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (savedBroadcasts.isEmpty()) {
                    item {
                        Text(
                            text = "No SOS broadcasts recorded yet. Tap BROADCAST SOS to log coordinates & pulse map.",
                            style = MaterialTheme.typography.labelSmall,
                            color = TacticalTextDim,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    }
                } else {
                    items(savedBroadcasts, key = { it.id }) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(TacticalSurfaceElevated, RoundedCornerShape(8.dp))
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${item.callsign} • ${timeFormatter.format(Date(item.timestampMs))}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TacticalTextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${String.format("%.4f", item.lat)}°N, ${String.format("%.4f", item.lng)}°E",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TacticalCyan
                                )
                                Text(
                                    text = item.onlineSyncStatus,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if ("PUBLISHED" in item.onlineSyncStatus) ThreatLowGreen else DivisionAmber
                                )
                            }
                            IconButton(
                                onClick = { onQuickShareEntry(item) },
                                modifier = Modifier.size(36.dp).testTag("share_sos_entry_${item.id}")
                            ) {
                                Icon(Icons.Default.Share, contentDescription = "Share SOS Location", tint = TacticalCyan, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
