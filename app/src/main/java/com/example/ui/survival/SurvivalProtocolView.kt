package com.example.ui.survival

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.TacticalDataProvider
import com.example.model.SurvivalProtocol
import com.example.ui.theme.*

@Composable
fun SurvivalProtocolView(
    isBeaconActive: Boolean,
    onToggleBeacon: () -> Unit,
    currentCoords: Pair<Double, Double>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var expandedProtocolId by remember { mutableStateOf<String?>("prot_airstrike") }

    // Pulsing beacon animation
    val infiniteTransition = rememberInfiniteTransition(label = "BeaconPulse")
    val beaconPulse by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Pulse"
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(TacticalDarkBg)
            .padding(16.dp)
            .testTag("survival_protocol_view"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // SOS Emergency Beacon Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.5.dp,
                        if (isBeaconActive) ThreatCriticalRed else TacticalBorder,
                        RoundedCornerShape(12.dp)
                    )
                    .testTag("emergency_beacon_card"),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isBeaconActive) Color(0xFF1E0A0E) else TacticalSurface
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "EMERGENCY DISTRESS BEACON",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isBeaconActive) ThreatCriticalRed else TacticalTextPrimary
                        )
                        Box(
                            modifier = Modifier
                                .background(
                                    if (isBeaconActive) ThreatCriticalRed else TacticalSurfaceElevated,
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (isBeaconActive) "TRANSMITTING" else "STANDBY",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isBeaconActive) TacticalDarkBg else TacticalTextDim
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Pulse Beacon Graphic
                    Box(
                        modifier = Modifier
                            .size((64 * if (isBeaconActive) beaconPulse else 1f).dp)
                            .clip(CircleShape)
                            .background(
                                if (isBeaconActive) ThreatCriticalRed.copy(alpha = 0.25f)
                                else TacticalSurfaceElevated
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(if (isBeaconActive) ThreatCriticalRed else DivisionOrange),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (isBeaconActive) Icons.Default.Sensors else Icons.Default.SensorsOff,
                                contentDescription = null,
                                tint = TacticalDarkBg,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "COORDINATES: ${String.format("%.4f", currentCoords.first)}°N, ${String.format("%.4f", currentCoords.second)}°E",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TacticalCyan
                    )

                    Text(
                        text = if (isBeaconActive)
                            "Broadcasting encrypted SOS packet on emergency frequency 446.006 MHz..."
                        else "Beacon is armed. Activate only in imminent danger or medical crisis.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TacticalTextSecondary,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onToggleBeacon,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("toggle_beacon_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isBeaconActive) TacticalSurfaceElevated else ThreatCriticalRed,
                                contentColor = if (isBeaconActive) ThreatCriticalRed else TacticalDarkBg
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = if (isBeaconActive) "DEACTIVATE SOS" else "TRIGGER DISTRESS PULSE",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val text = "SOS EMERGENCY SIGNAL [AEGIS EYE]: Agent Coordinates: ${currentCoords.first}°N, ${currentCoords.second}°E. Requesting humanitarian evacuation extraction."
                                clipboard.setPrimaryClip(ClipData.newPlainText("SOS Coordinates", text))
                                Toast.makeText(context, "Distress Telemetry Copied to Clipboard", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TacticalCyan),
                            border = androidx.compose.foundation.BorderStroke(1.dp, TacticalCyan),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("copy_coords_button")
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                        }
                    }
                }
            }
        }

        // Section Title: Crisis Protocols
        item {
            Text(
                text = "STANDARD CRISIS PROTOCOLS",
                style = MaterialTheme.typography.labelLarge,
                color = DivisionOrange
            )
        }

        // List of Protocols
        items(TacticalDataProvider.SURVIVAL_PROTOCOLS) { protocol ->
            val isExpanded = expandedProtocolId == protocol.id

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, TacticalBorder, RoundedCornerShape(8.dp))
                    .clickable {
                        expandedProtocolId = if (isExpanded) null else protocol.id
                    }
                    .testTag("protocol_card_${protocol.id}"),
                colors = CardDefaults.cardColors(containerColor = TacticalSurface),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = protocol.code,
                                style = MaterialTheme.typography.labelSmall,
                                color = DivisionOrange
                            )
                            Text(
                                text = protocol.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = TacticalTextPrimary
                            )
                        }
                        Icon(
                            if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = TacticalCyan
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = protocol.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = TacticalTextSecondary
                    )

                    AnimatedVisibility(visible = isExpanded) {
                        Column(
                            modifier = Modifier.padding(top = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            protocol.directives.forEachIndexed { i, dir ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(18.dp)
                                            .background(DivisionOrange.copy(alpha = 0.2f), RoundedCornerShape(4.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "${i + 1}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = DivisionOrange
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = dir,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TacticalTextPrimary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(TacticalSurfaceElevated, RoundedCornerShape(6.dp))
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Radio, contentDescription = null, tint = TacticalCyan, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "EMERGENCY FREQUENCY: ${protocol.emergencyFrequency}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TacticalCyan
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
