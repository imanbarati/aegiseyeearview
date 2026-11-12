package com.example.ui.sources

import androidx.compose.animation.*
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.model.IntelSource
import com.example.model.IntelSourceType
import com.example.ui.theme.*

@Composable
fun IntelSourceManagerView(
    sources: List<IntelSource>,
    onToggleSource: (String) -> Unit,
    onAddSource: (String, IntelSourceType, String, Double?, String) -> Unit,
    onDeleteSource: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedTypeFilter by remember { mutableStateOf<IntelSourceType?>(null) }

    val filteredSources = remember(sources, selectedTypeFilter) {
        if (selectedTypeFilter == null) sources
        else sources.filter { it.type == selectedTypeFilter }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TacticalDarkBg)
            .padding(16.dp)
            .testTag("intel_source_manager_view")
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(TacticalCyan, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "INTEL STREAMS & SDR RADAR",
                        style = MaterialTheme.typography.titleMedium,
                        color = TacticalCyan
                    )
                }
                Text(
                    text = "OSINT, FEDIVERSE, RSS & LOCAL HARDWARE SDR",
                    style = MaterialTheme.typography.bodySmall,
                    color = TacticalTextSecondary
                )
            }

            Button(
                onClick = { showAddDialog = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = DivisionOrange,
                    contentColor = TacticalDarkBg
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("add_source_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("ADD SOURCE", style = MaterialTheme.typography.labelSmall)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Spectrum / SDR Active Waterfall Banner
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = TacticalSurfaceElevated),
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Sensors,
                        contentDescription = "SDR Signal",
                        tint = DivisionAmber,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "RADIO SPECTRUM RADAR: 1090.00 MHz // 142.800 MHz",
                            style = MaterialTheme.typography.labelMedium,
                            color = DivisionAmber
                        )
                        Text(
                            text = "ACTIVE STREAMS: ${sources.count { it.isEnabled }} OF ${sources.size} // RTL-TCP READY",
                            style = MaterialTheme.typography.bodySmall,
                            color = TacticalTextSecondary
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .background(ThreatLowGreen.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "AGC LOCK",
                        style = MaterialTheme.typography.labelSmall,
                        color = ThreatLowGreen
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Filter Chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = selectedTypeFilter == null,
                onClick = { selectedTypeFilter = null },
                label = { Text("ALL (${sources.size})", style = MaterialTheme.typography.labelSmall) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = TacticalCyan,
                    selectedLabelColor = TacticalDarkBg,
                    containerColor = TacticalSurfaceElevated,
                    labelColor = TacticalTextPrimary
                )
            )
            FilterChip(
                selected = selectedTypeFilter == IntelSourceType.X_TWITTER,
                onClick = { selectedTypeFilter = if (selectedTypeFilter == IntelSourceType.X_TWITTER) null else IntelSourceType.X_TWITTER },
                label = { Text("X / OSINT", style = MaterialTheme.typography.labelSmall) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = DivisionOrange,
                    selectedLabelColor = TacticalDarkBg,
                    containerColor = TacticalSurfaceElevated,
                    labelColor = TacticalTextPrimary
                )
            )
            FilterChip(
                selected = selectedTypeFilter == IntelSourceType.HOMEMADE_SDR,
                onClick = { selectedTypeFilter = if (selectedTypeFilter == IntelSourceType.HOMEMADE_SDR) null else IntelSourceType.HOMEMADE_SDR },
                label = { Text("SDR / RADAR", style = MaterialTheme.typography.labelSmall) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = DivisionAmber,
                    selectedLabelColor = TacticalDarkBg,
                    containerColor = TacticalSurfaceElevated,
                    labelColor = TacticalTextPrimary
                )
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Sources List
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(filteredSources, key = { it.id }) { source ->
                SourceItemCard(
                    source = source,
                    onToggle = { onToggleSource(source.id) },
                    onDelete = { onDeleteSource(source.id) }
                )
            }
        }
    }

    // Add Source Dialog Modal
    if (showAddDialog) {
        AddSourceDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, type, url, freq, desc ->
                onAddSource(name, type, url, freq, desc)
                showAddDialog = false
            }
        )
    }
}

@Composable
fun SourceItemCard(
    source: IntelSource,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val typeColor = when (source.type) {
        IntelSourceType.X_TWITTER -> DivisionOrange
        IntelSourceType.FEDIVERSE_MASTODON -> Color(0xFF6364FF)
        IntelSourceType.RSS_FEED -> Color(0xFFFF9800)
        IntelSourceType.HOMEMADE_SDR -> DivisionAmber
        IntelSourceType.RADIO_RADAR -> TacticalCyan
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (source.isEnabled) typeColor.copy(alpha = 0.5f) else TacticalBorder,
                RoundedCornerShape(8.dp)
            )
            .testTag("source_card_${source.id}"),
        colors = CardDefaults.cardColors(containerColor = TacticalSurface),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: Badge, Name, Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .background(typeColor.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                            .border(1.dp, typeColor, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = source.type.badge,
                            style = MaterialTheme.typography.labelSmall,
                            color = typeColor
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = source.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = TacticalTextPrimary,
                        maxLines = 1
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = source.isEnabled,
                        onCheckedChange = { onToggle() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = typeColor,
                            checkedTrackColor = typeColor.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.testTag("toggle_source_${source.id}")
                    )

                    if (source.isCustom) {
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = "Delete",
                                tint = ThreatCriticalRed,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Endpoint or Frequency
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (source.frequencyMhz != null) Icons.Default.Radio else Icons.Default.Link,
                    contentDescription = null,
                    tint = TacticalTextDim,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (source.frequencyMhz != null)
                        "FREQ: ${source.frequencyMhz} MHz (${source.endpointUrl})"
                    else source.endpointUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = TacticalCyan,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Description
            Text(
                text = source.description,
                style = MaterialTheme.typography.bodySmall,
                color = TacticalTextSecondary
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Footer Telemetry: Signal RSSI & Items Detected
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TacticalSurfaceElevated, RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(if (source.isEnabled) ThreatLowGreen else TacticalTextDim, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (source.isEnabled) source.status else "OFFLINE // DISABLED",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (source.isEnabled) ThreatLowGreen else TacticalTextDim
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "RSSI: ${source.signalStrengthDbm} dBm",
                        style = MaterialTheme.typography.labelSmall,
                        color = TacticalTextDim
                    )
                    Text(
                        text = "DETECTIONS: ${source.itemsDetected}",
                        style = MaterialTheme.typography.labelSmall,
                        color = DivisionAmber
                    )
                }
            }
        }
    }
}

@Composable
fun AddSourceDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, type: IntelSourceType, endpoint: String, freq: Double?, desc: String) -> Unit
) {
    var sourceName by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(IntelSourceType.X_TWITTER) }
    var endpointUrl by remember { mutableStateOf("") }
    var frequencyText by remember { mutableStateOf("") }
    var descriptionText by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.5.dp, DivisionOrange, RoundedCornerShape(12.dp))
                .testTag("add_source_dialog"),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = TacticalSurface)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "ADD TACTICAL INTEL STREAM",
                    style = MaterialTheme.typography.titleLarge,
                    color = DivisionOrange
                )

                Text(
                    text = "Connect OSINT profiles (e.g. X/Twitter, Fediverse Mastodon, RSS) or homemade SDR & radio radar endpoints.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TacticalTextSecondary
                )

                // Source Type Selector
                Column {
                    Text("SOURCE TYPE", style = MaterialTheme.typography.labelSmall, color = TacticalCyan)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IntelSourceType.values().forEach { type ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (selectedType == type) DivisionOrange else TacticalSurfaceElevated)
                                    .clickable {
                                        selectedType = type
                                        if (endpointUrl.isEmpty()) {
                                            when (type) {
                                                IntelSourceType.X_TWITTER -> endpointUrl = "https://x.com/hey_itsmyturn?lang=en"
                                                IntelSourceType.FEDIVERSE_MASTODON -> endpointUrl = "https://mastodon.social/tags/irancrisis.rss"
                                                IntelSourceType.RSS_FEED -> endpointUrl = "https://reliefweb.int/updates/rss.xml"
                                                IntelSourceType.HOMEMADE_SDR -> {
                                                    endpointUrl = "rtl_tcp://192.168.1.150:1234"
                                                    frequencyText = "1090.00"
                                                }
                                                IntelSourceType.RADIO_RADAR -> {
                                                    endpointUrl = "sdr://local-radio/scanner"
                                                    frequencyText = "142.80"
                                                }
                                            }
                                        }
                                    }
                                    .padding(horizontal = 6.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = type.badge,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (selectedType == type) TacticalDarkBg else TacticalTextPrimary
                                )
                            }
                        }
                    }
                }

                // Name Input
                OutlinedTextField(
                    value = sourceName,
                    onValueChange = { sourceName = it },
                    label = { Text("Source Title (e.g. @hey_itsmyturn X Monitor)") },
                    modifier = Modifier.fillMaxWidth().testTag("input_source_name"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DivisionOrange,
                        unfocusedBorderColor = TacticalBorder,
                        focusedTextColor = TacticalTextPrimary,
                        unfocusedTextColor = TacticalTextPrimary
                    ),
                    singleLine = true
                )

                // Endpoint / URL Input
                OutlinedTextField(
                    value = endpointUrl,
                    onValueChange = { endpointUrl = it },
                    label = { Text("Endpoint URL / Host / Handle") },
                    modifier = Modifier.fillMaxWidth().testTag("input_source_endpoint"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TacticalCyan,
                        unfocusedBorderColor = TacticalBorder,
                        focusedTextColor = TacticalTextPrimary,
                        unfocusedTextColor = TacticalTextPrimary
                    ),
                    singleLine = true
                )

                // Frequency Input (for SDR & Radio)
                if (selectedType == IntelSourceType.HOMEMADE_SDR || selectedType == IntelSourceType.RADIO_RADAR) {
                    OutlinedTextField(
                        value = frequencyText,
                        onValueChange = { frequencyText = it },
                        label = { Text("Center Frequency (MHz, e.g. 1090.00, 142.80)") },
                        modifier = Modifier.fillMaxWidth().testTag("input_source_frequency"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DivisionAmber,
                            unfocusedBorderColor = TacticalBorder,
                            focusedTextColor = TacticalTextPrimary,
                            unfocusedTextColor = TacticalTextPrimary
                        ),
                        singleLine = true
                    )
                }

                // Description Input
                OutlinedTextField(
                    value = descriptionText,
                    onValueChange = { descriptionText = it },
                    label = { Text("Description / Notes") },
                    modifier = Modifier.fillMaxWidth().testTag("input_source_desc"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TacticalCyan,
                        unfocusedBorderColor = TacticalBorder,
                        focusedTextColor = TacticalTextPrimary,
                        unfocusedTextColor = TacticalTextPrimary
                    )
                )

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("CANCEL", color = TacticalTextSecondary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val name = sourceName.ifBlank {
                                when (selectedType) {
                                    IntelSourceType.X_TWITTER -> "@hey_itsmyturn OSINT Feed"
                                    IntelSourceType.FEDIVERSE_MASTODON -> "Fediverse Crisis Stream"
                                    IntelSourceType.RSS_FEED -> "Crisis News RSS"
                                    IntelSourceType.HOMEMADE_SDR -> "Homemade RTL-SDR Node"
                                    IntelSourceType.RADIO_RADAR -> "Radio Radar Scanner"
                                }
                            }
                            val url = endpointUrl.ifBlank { "https://x.com/hey_itsmyturn?lang=en" }
                            val freq = frequencyText.toDoubleOrNull()
                            val desc = descriptionText.ifBlank { "Active user-configured tactical intelligence stream." }
                            onConfirm(name, selectedType, url, freq, desc)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DivisionOrange,
                            contentColor = TacticalDarkBg
                        ),
                        modifier = Modifier.testTag("confirm_add_source_button")
                    ) {
                        Text("ESTABLISH LINK")
                    }
                }
            }
        }
    }
}
