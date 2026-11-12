package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.MapVisualMode
import com.example.model.ThreatLevel
import com.example.ui.evac.EvacuationPathfinderSheet
import com.example.ui.intel.LiveSitRepView
import com.example.ui.map.TacticalMapView
import com.example.ui.sheets.SafeHavenDetailSheet
import com.example.ui.sheets.ThreatDetailSheet
import com.example.ui.survival.SurvivalProtocolView
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainNavTab
import com.example.ui.viewmodel.TacticalViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TacticalMainScreen(
    viewModel: TacticalViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showLayersMenu by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize().testTag("tactical_main_screen"),
        containerColor = TacticalDarkBg,
        topBar = {
            TacticalTopBar(
                mapMode = uiState.mapMode,
                onSelectMapMode = { viewModel.setMapMode(it) },
                onToggleLayersMenu = { showLayersMenu = !showLayersMenu },
                isTrackingActive = uiState.isNavigationTrackingActive,
                activeRouteName = uiState.activeRoute.name
            )
        },
        bottomBar = {
            TacticalBottomNavBar(
                activeTab = uiState.activeTab,
                onTabSelected = { viewModel.setTab(it) }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (uiState.activeTab) {
                MainNavTab.MAP -> {
                    // Full Screen God's Eye Tactical Map
                    TacticalMapView(
                        threatZones = uiState.threatZones,
                        safeHavens = uiState.safeHavens,
                        activeRoute = uiState.activeRoute,
                        mapMode = uiState.mapMode,
                        showDeadZones = uiState.showDeadZones,
                        showSafeHavens = uiState.showSafeHavens,
                        showRadarSweep = uiState.showRadarSweep,
                        selectedZone = uiState.selectedZone,
                        selectedHaven = uiState.selectedHaven,
                        onZoneSelected = { viewModel.selectZone(it) },
                        onHavenSelected = { viewModel.selectHaven(it) },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Bottom Floating Quick Access Action Bar on Map
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 16.dp, start = 16.dp, end = 16.dp)
                            .background(TacticalSurface.copy(alpha = 0.92f), RoundedCornerShape(24.dp))
                            .border(1.dp, TacticalBorder, RoundedCornerShape(24.dp))
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Quick toggle dead zones
                        FilterChip(
                            selected = uiState.showDeadZones,
                            onClick = { viewModel.toggleDeadZones() },
                            label = { Text("DEAD ZONES", style = MaterialTheme.typography.labelSmall) },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(ThreatCriticalRed, CircleShape)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ThreatCriticalRed.copy(alpha = 0.25f),
                                selectedLabelColor = ThreatCriticalRed,
                                containerColor = TacticalSurfaceElevated,
                                labelColor = TacticalTextSecondary
                            )
                        )

                        // Quick toggle safe havens
                        FilterChip(
                            selected = uiState.showSafeHavens,
                            onClick = { viewModel.toggleSafeHavens() },
                            label = { Text("SAFE HAVENS", style = MaterialTheme.typography.labelSmall) },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(ThreatLowGreen, CircleShape)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ThreatLowGreen.copy(alpha = 0.25f),
                                selectedLabelColor = ThreatLowGreen,
                                containerColor = TacticalSurfaceElevated,
                                labelColor = TacticalTextSecondary
                            )
                        )

                        // Evac Route Drawer Toggle
                        IconButton(
                            onClick = { viewModel.togglePathfinderSheet() },
                            modifier = Modifier.size(36.dp).testTag("open_pathfinder_fab")
                        ) {
                            Icon(
                                Icons.Default.AltRoute,
                                contentDescription = "Pathfinder",
                                tint = TacticalCyan
                            )
                        }
                    }

                    // Threat Detail Sliding Sheet
                    AnimatedVisibility(
                        visible = uiState.selectedZone != null,
                        enter = slideInVertically(initialOffsetY = { it }),
                        exit = slideOutVertically(targetOffsetY = { it }),
                        modifier = Modifier.align(Alignment.BottomCenter)
                    ) {
                        uiState.selectedZone?.let { zone ->
                            ThreatDetailSheet(
                                zone = zone,
                                onDismiss = { viewModel.selectZone(null) },
                                onPlanBypass = { viewModel.planBypassForZone(it) },
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    // Safe Haven Detail Sliding Sheet
                    AnimatedVisibility(
                        visible = uiState.selectedHaven != null,
                        enter = slideInVertically(initialOffsetY = { it }),
                        exit = slideOutVertically(targetOffsetY = { it }),
                        modifier = Modifier.align(Alignment.BottomCenter)
                    ) {
                        uiState.selectedHaven?.let { haven ->
                            SafeHavenDetailSheet(
                                haven = haven,
                                onDismiss = { viewModel.selectHaven(null) },
                                onSetDestination = { viewModel.setDestinationAndCalculate(it) },
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    // Pathfinder Bottom Drawer (when opened on map)
                    AnimatedVisibility(
                        visible = uiState.isPathfinderExpanded,
                        enter = slideInVertically(initialOffsetY = { it }),
                        exit = slideOutVertically(targetOffsetY = { it }),
                        modifier = Modifier.align(Alignment.BottomCenter)
                    ) {
                        EvacuationPathfinderSheet(
                            activeRoute = uiState.activeRoute,
                            originCity = uiState.originCity,
                            destinationHavenId = uiState.destinationHavenId,
                            standoffBufferKm = uiState.standoffBufferKm,
                            isNavigationTrackingActive = uiState.isNavigationTrackingActive,
                            onOriginChanged = { viewModel.setOriginCity(it) },
                            onDestinationChanged = { viewModel.setDestinationHaven(it) },
                            onBufferChanged = { viewModel.setStandoffBuffer(it) },
                            onToggleTracking = { viewModel.toggleNavigationTracking() },
                            onClose = { viewModel.togglePathfinderSheet() },
                            modifier = Modifier.fillMaxHeight(0.85f)
                        )
                    }
                }

                MainNavTab.PATHFINDER -> {
                    EvacuationPathfinderSheet(
                        activeRoute = uiState.activeRoute,
                        originCity = uiState.originCity,
                        destinationHavenId = uiState.destinationHavenId,
                        standoffBufferKm = uiState.standoffBufferKm,
                        isNavigationTrackingActive = uiState.isNavigationTrackingActive,
                        onOriginChanged = { viewModel.setOriginCity(it) },
                        onDestinationChanged = { viewModel.setDestinationHaven(it) },
                        onBufferChanged = { viewModel.setStandoffBuffer(it) },
                        onToggleTracking = { viewModel.toggleNavigationTracking() },
                        onClose = { viewModel.setTab(MainNavTab.MAP) },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                MainNavTab.SITREP -> {
                    LiveSitRepView(
                        incidents = uiState.liveIncidents,
                        onSelectIncident = { viewModel.focusIncident(it) },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                MainNavTab.SURVIVAL -> {
                    SurvivalProtocolView(
                        isBeaconActive = uiState.isEmergencyBeaconActive,
                        onToggleBeacon = { viewModel.toggleEmergencyBeacon() },
                        currentCoords = uiState.userCoordinates,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Quick Map Layers Dropdown / Dialog
            if (showLayersMenu) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 8.dp, end = 12.dp)
                        .background(TacticalSurface, RoundedCornerShape(8.dp))
                        .border(1.dp, TacticalBorder, RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "MAP VISUAL OPTICS",
                            style = MaterialTheme.typography.labelSmall,
                            color = DivisionOrange
                        )
                        MapVisualMode.values().forEach { mode ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.setMapMode(mode)
                                        showLayersMenu = false
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = uiState.mapMode == mode,
                                    onClick = {
                                        viewModel.setMapMode(mode)
                                        showLayersMenu = false
                                    },
                                    colors = RadioButtonDefaults.colors(selectedColor = DivisionOrange)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(mode.displayName, style = MaterialTheme.typography.bodyMedium, color = TacticalTextPrimary)
                                    Text(mode.subtitle, style = MaterialTheme.typography.labelSmall, color = TacticalTextDim)
                                }
                            }
                        }

                        Divider(color = TacticalBorder)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.toggleRadarSweep() }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("RADAR SWEEP", style = MaterialTheme.typography.bodyMedium, color = TacticalTextPrimary)
                            Switch(
                                checked = uiState.showRadarSweep,
                                onCheckedChange = { viewModel.toggleRadarSweep() },
                                colors = SwitchDefaults.colors(checkedThumbColor = DivisionOrange, checkedTrackColor = DivisionOrangeGlow)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TacticalTopBar(
    mapMode: MapVisualMode,
    onSelectMapMode: (MapVisualMode) -> Unit,
    onToggleLayersMenu: () -> Unit,
    isTrackingActive: Boolean,
    activeRouteName: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(TacticalSurface)
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(DivisionOrange, CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "AEGIS EYE // GOD'S EYE VIEW",
                        style = MaterialTheme.typography.titleMedium,
                        color = DivisionOrange
                    )
                    Text(
                        text = "CONFLICT EVASION & CRISIS CORRIDORS",
                        style = MaterialTheme.typography.labelSmall,
                        color = TacticalTextSecondary
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Map Mode / Layers button
                IconButton(
                    onClick = onToggleLayersMenu,
                    modifier = Modifier.testTag("map_layers_button")
                ) {
                    Icon(
                        Icons.Default.Layers,
                        contentDescription = "Map Layers",
                        tint = TacticalCyan
                    )
                }
            }
        }

        // Crisis Tracking Active Top Banner
        AnimatedVisibility(visible = isTrackingActive) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .background(TacticalCyan.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                    .border(1.dp, TacticalCyan, RoundedCornerShape(6.dp))
                    .padding(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Navigation,
                            contentDescription = null,
                            tint = TacticalCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "CRISIS HUD: $activeRouteName",
                            style = MaterialTheme.typography.labelSmall,
                            color = TacticalCyan
                        )
                    }
                    Text(
                        text = "ONLINE",
                        style = MaterialTheme.typography.labelSmall,
                        color = ThreatLowGreen
                    )
                }
            }
        }
    }
}

@Composable
fun TacticalBottomNavBar(
    activeTab: MainNavTab,
    onTabSelected: (MainNavTab) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .testTag("tactical_bottom_nav"),
        containerColor = TacticalSurface,
        tonalElevation = 8.dp
    ) {
        val items = listOf(
            Triple(MainNavTab.MAP, "GOD'S EYE", Icons.Default.Map),
            Triple(MainNavTab.PATHFINDER, "EVAC ROUTE", Icons.Default.AltRoute),
            Triple(MainNavTab.SITREP, "SITREP", Icons.Default.Sensors),
            Triple(MainNavTab.SURVIVAL, "PROTOCOLS", Icons.Default.Security)
        )

        for ((tab, label, icon) in items) {
            val isSelected = activeTab == tab
            NavigationBarItem(
                selected = isSelected,
                onClick = { onTabSelected(tab) },
                icon = {
                    Icon(
                        icon,
                        contentDescription = label,
                        modifier = Modifier.size(22.dp)
                    )
                },
                label = {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) DivisionOrange else TacticalTextDim
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = DivisionOrange,
                    unselectedIconColor = TacticalTextDim,
                    indicatorColor = DivisionOrange.copy(alpha = 0.2f)
                ),
                modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
            )
        }
    }
}
