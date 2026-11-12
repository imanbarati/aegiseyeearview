package com.example.ui.intel

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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.LiveIncident
import com.example.model.ThreatLevel
import com.example.ui.theme.*

@Composable
fun LiveSitRepView(
    incidents: List<LiveIncident>,
    onSelectIncident: (LiveIncident) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedFilter by remember { mutableStateOf<ThreatLevel?>(null) }

    val filteredIncidents = remember(selectedFilter, incidents) {
        if (selectedFilter == null) incidents
        else incidents.filter { it.severity == selectedFilter }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TacticalDarkBg)
            .padding(16.dp)
            .testTag("live_sitrep_view")
    ) {
        // Telemetry Feed Header
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
                            .background(ThreatCriticalRed, RoundedCornerShape(5.dp))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ISAC RECON // LIVE SITREP",
                        style = MaterialTheme.typography.titleMedium,
                        color = DivisionOrange
                    )
                }
                Text(
                    text = "THEATER INTELLIGENCE & CRISIS TELEMETRY",
                    style = MaterialTheme.typography.bodySmall,
                    color = TacticalTextSecondary
                )
            }

            Box(
                modifier = Modifier
                    .background(TacticalSurfaceElevated, RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "LINK: ENCRYPTED",
                    style = MaterialTheme.typography.labelSmall,
                    color = TacticalCyan
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Filter Pills
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedFilter == null,
                onClick = { selectedFilter = null },
                label = { Text("ALL REPORTS (${incidents.size})", style = MaterialTheme.typography.labelSmall) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = DivisionOrange,
                    selectedLabelColor = TacticalDarkBg,
                    containerColor = TacticalSurfaceElevated,
                    labelColor = TacticalTextPrimary
                )
            )
            FilterChip(
                selected = selectedFilter == ThreatLevel.CRITICAL_ALPHA,
                onClick = { selectedFilter = if (selectedFilter == ThreatLevel.CRITICAL_ALPHA) null else ThreatLevel.CRITICAL_ALPHA },
                label = { Text("ALPHA ONLY", style = MaterialTheme.typography.labelSmall) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = ThreatCriticalRed,
                    selectedLabelColor = TacticalDarkBg,
                    containerColor = TacticalSurfaceElevated,
                    labelColor = ThreatCriticalRed
                )
            )
            FilterChip(
                selected = selectedFilter == ThreatLevel.HIGH_BRAVO,
                onClick = { selectedFilter = if (selectedFilter == ThreatLevel.HIGH_BRAVO) null else ThreatLevel.HIGH_BRAVO },
                label = { Text("BRAVO", style = MaterialTheme.typography.labelSmall) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = ThreatHighOrange,
                    selectedLabelColor = TacticalDarkBg,
                    containerColor = TacticalSurfaceElevated,
                    labelColor = ThreatHighOrange
                )
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Incidents List
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(filteredIncidents) { incident ->
                IncidentCard(incident = incident, onLocate = { onSelectIncident(incident) })
            }
        }
    }
}

@Composable
fun IncidentCard(
    incident: LiveIncident,
    onLocate: () -> Unit,
    modifier: Modifier = Modifier
) {
    val levelColor = when (incident.severity) {
        ThreatLevel.CRITICAL_ALPHA -> ThreatCriticalRed
        ThreatLevel.HIGH_BRAVO -> ThreatHighOrange
        ThreatLevel.MODERATE_CHARLIE -> ThreatModerateYellow
        ThreatLevel.CAUTION_DELTA -> TacticalCyan
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, levelColor.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
            .clickable { onLocate() }
            .testTag("incident_card_${incident.id}"),
        colors = CardDefaults.cardColors(containerColor = TacticalSurface),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .background(levelColor, RoundedCornerShape(3.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = incident.severity.code,
                            style = MaterialTheme.typography.labelSmall,
                            color = TacticalDarkBg
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = incident.sector,
                        style = MaterialTheme.typography.labelSmall,
                        color = levelColor
                    )
                }

                Text(
                    text = "${incident.minutesAgo}M AGO",
                    style = MaterialTheme.typography.labelSmall,
                    color = TacticalTextDim
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Title
            Text(
                text = incident.title,
                style = MaterialTheme.typography.titleMedium,
                color = TacticalTextPrimary
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Description
            Text(
                text = incident.description,
                style = MaterialTheme.typography.bodyMedium,
                color = TacticalTextSecondary
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Footer Agency Tag & Locate Action
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Sensors,
                        contentDescription = null,
                        tint = TacticalCyan,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = incident.sourceAgency,
                        style = MaterialTheme.typography.labelSmall,
                        color = TacticalCyan
                    )
                }

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(TacticalSurfaceElevated)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Place,
                        contentDescription = "Locate",
                        tint = DivisionOrange,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "VIEW ON MAP",
                        style = MaterialTheme.typography.labelSmall,
                        color = DivisionOrange
                    )
                }
            }
        }
    }
}
