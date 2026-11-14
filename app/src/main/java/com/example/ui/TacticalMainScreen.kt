package com.example.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.MapVisualMode
import com.example.ui.evac.EvacuationPathfinderSheet
import com.example.ui.intel.LiveSitRepView
import com.example.ui.map.FreeMapProvider
import com.example.ui.map.TacticalMapView
import com.example.ui.sheets.CameraDetailSheet
import com.example.ui.sheets.GeoJsonMvtLayersSheet
import com.example.ui.sheets.GeoJsonThreatPolygonDetailSheet
import com.example.ui.sheets.MvtFeatureDetailSheet
import com.example.ui.sheets.NearbyCamerasSheet
import com.example.ui.sheets.OfflineTileCacheSheet
import com.example.ui.sheets.SafeHavenDetailSheet
import com.example.ui.sheets.SosBroadcastSheet
import com.example.ui.sheets.ThreatDetailSheet
import com.example.ui.sources.IntelSourceManagerView
import com.example.ui.survival.SurvivalProtocolView
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainNavTab
import com.example.ui.viewmodel.TacticalViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TacticalMainScreen(
    viewModel: TacticalViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showLayersMenu by remember { mutableStateOf(false) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if (fineGranted || coarseGranted) {
            viewModel.startGpsTracking(context)
            viewModel.startThreatProximitySentinel(context)
        }
    }

    LaunchedEffect(Unit) {
        val perms = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
        locationPermissionLauncher.launch(perms)
        viewModel.initPersistence(context)
        viewModel.startGpsTracking(context)
        viewModel.loadBundledGeoJsonOverlays(context)
        viewModel.startConnectivityAndCacheMonitoring(context)
        viewModel.startOrientationTracking(context)
        viewModel.startThreatProximitySentinel(context)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().testTag("tactical_main_screen"),
        containerColor = TacticalDarkBg,
        topBar = {
            TacticalTopBar(
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
                    TacticalMapView(
                        threatZones = uiState.threatZones,
                        heatmapPoints = uiState.heatmapPoints,
                        safeHavens = uiState.safeHavens,
                        cameras = uiState.cameras,
                        geoJsonOverlays = uiState.geoJsonOverlays,
                        geoJsonThreatPolygons = uiState.geoJsonThreatPolygons,
                        activeRoute = uiState.activeRoute,
                        mapMode = uiState.mapMode,
                        freeProvider = uiState.freeMapProvider,
                        showDeadZones = uiState.showDeadZones,
                        showHeatmap = uiState.showHeatmap,
                        showSafeHavens = uiState.showSafeHavens,
                        showCctvOverlay = uiState.showCctvOverlay,
                        showRadarSweep = uiState.showRadarSweep,
                        showMvtGeoJsonOverlay = uiState.showMvtGeoJsonOverlay,
                        showThreatPolygons = uiState.showThreatPolygons,
                        isNetworkOnline = uiState.isNetworkOnline,
                        tileCacheStats = uiState.tileCacheStats,
                        selectedZone = uiState.selectedZone,
                        selectedHaven = uiState.selectedHaven,
                        selectedCamera = uiState.selectedCamera,
                        selectedMvtFeature = uiState.selectedMvtFeature,
                        selectedThreatPolygon = uiState.selectedThreatPolygon,
                        userMovement = uiState.userMovement,
                        surveillanceExposure = uiState.surveillanceExposure,
                        followAgent = uiState.followAgentOnMap,
                        onToggleFollowAgent = { viewModel.toggleFollowAgent() },
                        compassReading = uiState.compassReading,
                        isHeadingLockEnabled = uiState.isCompassHeadingLockEnabled,
                        geoJsonIntrusionAlert = uiState.geoJsonIntrusionAlert,
                        isBackgroundSentinelRunning = uiState.isBackgroundSentinelRunning,
                        isBackgroundSentinelArmed = uiState.isBackgroundSentinelArmed,
                        distanceMeasurement = uiState.distanceMeasurement,
                        breadcrumbs = uiState.breadcrumbs,
                        showBreadcrumbs = uiState.showBreadcrumbs,
                        isSosPulseActive = uiState.isEmergencyBeaconActive,
                        sosPulseCoords = uiState.sosPulseCoords,
                        onToggleHeadingLock = { viewModel.toggleCompassHeadingLock() },
                        onPlanBypassFromAlert = { viewModel.planBypassForIntrusionAlert() },
                        onDismissIntrusionAlert = { viewModel.dismissGeoJsonIntrusionAlert() },
                        onToggleSentinel = { viewModel.toggleBackgroundSentinel(context) },
                        onMeasurePointTapped = { viewModel.onMeasureMapPointTapped(it) },
                        onToggleMeasureMode = { viewModel.toggleMeasureDistanceMode() },
                        onSnapMeasurePointAToGps = { viewModel.snapMeasurePointAToGps() },
                        onClearMeasurePoints = { viewModel.clearDistanceMeasurement() },
                        onCloseMeasureMode = { viewModel.closeDistanceMeasurementMode() },
                        onOpenSosBroadcast = { viewModel.toggleSosSheet(true) },
                        threatWarning = uiState.threatWarning,
                        onZoneSelected = { viewModel.selectZone(it) },
                        onHavenSelected = { viewModel.selectHaven(it) },
                        onCameraSelected = { viewModel.selectCamera(it) },
                        onMvtFeatureSelected = { viewModel.selectMvtFeature(it) },
                        onThreatPolygonSelected = { viewModel.selectThreatPolygon(it) },
                        onBoundsChanged = { viewModel.updateMapBoundingBox(it) },
                        onOpenCctvScanner = { viewModel.toggleCctvScanner(true) },
                        onOpenGeoJsonManager = { viewModel.toggleGeoJsonSheet(true) },
                        onOpenCacheManager = {
                            viewModel.refreshTileCacheStats(context)
                            viewModel.toggleCacheManagerSheet(true)
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Bottom Floating Quick Layer Bar on Map
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 12.dp, start = 12.dp, end = 12.dp)
                            .background(TacticalSurface.copy(alpha = 0.94f), RoundedCornerShape(24.dp))
                            .border(1.dp, TacticalBorder, RoundedCornerShape(24.dp))
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = uiState.showHeatmap,
                            onClick = { viewModel.toggleHeatmap() },
                            label = { Text("HEATMAP", style = MaterialTheme.typography.labelSmall) },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(DivisionOrange, CircleShape)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = DivisionOrange.copy(alpha = 0.25f),
                                selectedLabelColor = DivisionOrange,
                                containerColor = TacticalSurfaceElevated,
                                labelColor = TacticalTextSecondary
                            )
                        )

                        FilterChip(
                            selected = uiState.showCctvOverlay,
                            onClick = { viewModel.toggleCctvOverlay() },
                            label = { Text("CCTV", style = MaterialTheme.typography.labelSmall) },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(DivisionAmber, CircleShape)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = DivisionAmber.copy(alpha = 0.25f),
                                selectedLabelColor = DivisionAmber,
                                containerColor = TacticalSurfaceElevated,
                                labelColor = TacticalTextSecondary
                            )
                        )

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

                        FilterChip(
                            selected = uiState.showSafeHavens,
                            onClick = { viewModel.toggleSafeHavens() },
                            label = { Text("SAFE ZONES", style = MaterialTheme.typography.labelSmall) },
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

                        FilterChip(
                            selected = uiState.showThreatPolygons,
                            onClick = { viewModel.toggleThreatPolygons() },
                            label = { Text("THREAT POLYS", style = MaterialTheme.typography.labelSmall) },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(ThreatHighOrange, CircleShape)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ThreatHighOrange.copy(alpha = 0.25f),
                                selectedLabelColor = ThreatHighOrange,
                                containerColor = TacticalSurfaceElevated,
                                labelColor = TacticalTextSecondary
                            )
                        )

                        FilterChip(
                            selected = uiState.showMvtGeoJsonOverlay,
                            onClick = { viewModel.toggleMvtGeoJsonOverlay() },
                            label = { Text("MVT / GEOJSON", style = MaterialTheme.typography.labelSmall) },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(TacticalCyan, CircleShape)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = TacticalCyan.copy(alpha = 0.25f),
                                selectedLabelColor = TacticalCyan,
                                containerColor = TacticalSurfaceElevated,
                                labelColor = TacticalTextSecondary
                            )
                        )

                        FilterChip(
                            selected = uiState.distanceMeasurement.isActive,
                            onClick = { viewModel.toggleMeasureDistanceMode() },
                            label = { Text("MEASURE", style = MaterialTheme.typography.labelSmall) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Straighten,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = if (uiState.distanceMeasurement.isActive) TacticalCyan else TacticalTextSecondary
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = TacticalCyan.copy(alpha = 0.25f),
                                selectedLabelColor = TacticalCyan,
                                containerColor = TacticalSurfaceElevated,
                                labelColor = TacticalTextSecondary
                            ),
                            modifier = Modifier.testTag("filter_chip_measure")
                        )

                        FilterChip(
                            selected = uiState.showBreadcrumbs,
                            onClick = { viewModel.toggleBreadcrumbs() },
                            label = {
                                Text(
                                    "TRAIL (${uiState.breadcrumbs.size})",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Timeline,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = if (uiState.showBreadcrumbs) DivisionAmber else TacticalTextSecondary
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = DivisionAmber.copy(alpha = 0.25f),
                                selectedLabelColor = DivisionAmber,
                                containerColor = TacticalSurfaceElevated,
                                labelColor = TacticalTextSecondary
                            ),
                            modifier = Modifier.testTag("filter_chip_breadcrumbs")
                        )

                        if (uiState.breadcrumbs.isNotEmpty()) {
                            IconButton(
                                onClick = { viewModel.clearBreadcrumbs() },
                                modifier = Modifier.size(36.dp).testTag("clear_breadcrumbs_button")
                            ) {
                                Icon(
                                    Icons.Default.DeleteSweep,
                                    contentDescription = "Clear 50m Breadcrumbs",
                                    tint = DivisionAmber
                                )
                            }
                        }

                        IconButton(
                            onClick = { viewModel.toggleGeoJsonSheet(true) },
                            modifier = Modifier.size(36.dp).testTag("open_geojson_manager_button")
                        ) {
                            Icon(
                                Icons.Default.UploadFile,
                                contentDescription = "GeoJSON & MVT Overlays",
                                tint = TacticalCyan
                            )
                        }

                        IconButton(
                            onClick = {
                                viewModel.refreshTileCacheStats(context)
                                viewModel.toggleCacheManagerSheet(true)
                            },
                            modifier = Modifier.size(36.dp).testTag("open_disk_cache_button")
                        ) {
                            Icon(
                                Icons.Default.DownloadForOffline,
                                contentDescription = "OsmDroid Offline Tile Cache",
                                tint = if (uiState.isNetworkOnline) TacticalCyan else DivisionAmber
                            )
                        }

                        IconButton(
                            onClick = { viewModel.togglePathfinderSheet() },
                            modifier = Modifier.size(36.dp).testTag("open_pathfinder_fab")
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.DirectionsWalk,
                                contentDescription = "Safe Walking Pathfinder",
                                tint = ThreatLowGreen
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

                    // Surveillance Camera Detail Sheet
                    AnimatedVisibility(
                        visible = uiState.selectedCamera != null,
                        enter = slideInVertically(initialOffsetY = { it }),
                        exit = slideOutVertically(targetOffsetY = { it }),
                        modifier = Modifier.align(Alignment.BottomCenter)
                    ) {
                        uiState.selectedCamera?.let { cam ->
                            CameraDetailSheet(
                                camera = cam,
                                userLat = uiState.userMovement.lat,
                                userLng = uiState.userMovement.lng,
                                onDismiss = { viewModel.selectCamera(null) },
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    // Nearby OSINT & State Cameras Vulnerability Scanner Sheet
                    AnimatedVisibility(
                        visible = uiState.isCctvScannerOpen,
                        enter = slideInVertically(initialOffsetY = { it }),
                        exit = slideOutVertically(targetOffsetY = { it }),
                        modifier = Modifier.align(Alignment.BottomCenter)
                    ) {
                        NearbyCamerasSheet(
                            exposure = uiState.surveillanceExposure,
                            cameras = uiState.cameras,
                            userLat = uiState.userMovement.lat,
                            userLng = uiState.userMovement.lng,
                            onSelectCamera = { viewModel.selectCamera(it) },
                            onDismiss = { viewModel.toggleCctvScanner(false) }
                        )
                    }

                    // Selected MVT Vector / GeoJSON Feature Inspector Sheet
                    AnimatedVisibility(
                        visible = uiState.selectedMvtFeature != null,
                        enter = slideInVertically(initialOffsetY = { it }),
                        exit = slideOutVertically(targetOffsetY = { it }),
                        modifier = Modifier.align(Alignment.BottomCenter)
                    ) {
                        uiState.selectedMvtFeature?.let { selection ->
                            MvtFeatureDetailSheet(
                                selection = selection,
                                onDismiss = { viewModel.selectMvtFeature(null) },
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    // Local GeoJSON -> MVT Vector Overlay Manager Sheet
                    AnimatedVisibility(
                        visible = uiState.isGeoJsonSheetOpen,
                        enter = slideInVertically(initialOffsetY = { it }),
                        exit = slideOutVertically(targetOffsetY = { it }),
                        modifier = Modifier.align(Alignment.BottomCenter)
                    ) {
                        GeoJsonMvtLayersSheet(
                            overlays = uiState.geoJsonOverlays,
                            onToggleOverlay = { viewModel.toggleGeoJsonLayer(it) },
                            onImportGeoJson = { name, raw -> viewModel.importCustomGeoJson(name, raw) },
                            onDeleteCustomOverlay = { viewModel.deleteCustomGeoJsonOverlay(it) },
                            onDismiss = { viewModel.toggleGeoJsonSheet(false) }
                        )
                    }

                    // Selected Clickable GeoJSON Threat Polygon Detail Sheet
                    AnimatedVisibility(
                        visible = uiState.selectedThreatPolygon != null,
                        enter = slideInVertically(initialOffsetY = { it }),
                        exit = slideOutVertically(targetOffsetY = { it }),
                        modifier = Modifier.align(Alignment.BottomCenter)
                    ) {
                        uiState.selectedThreatPolygon?.let { poly ->
                            GeoJsonThreatPolygonDetailSheet(
                                polygon = poly,
                                onDismiss = { viewModel.selectThreatPolygon(null) },
                                onPlanBypass = { viewModel.planBypassForGeoJsonPolygon(it) },
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    // OsmDroid Offline Tile Disk Cache Manager Sheet
                    AnimatedVisibility(
                        visible = uiState.isCacheManagerOpen,
                        enter = slideInVertically(initialOffsetY = { it }),
                        exit = slideOutVertically(targetOffsetY = { it }),
                        modifier = Modifier.align(Alignment.BottomCenter)
                    ) {
                        val inventorySummary = "${uiState.geoJsonThreatPolygons.size} Polys • ${uiState.geoJsonOverlays.size} MVT • ${uiState.breadcrumbs.size} Trail • ${uiState.sosBroadcasts.size} SOS"
                        OfflineTileCacheSheet(
                            isNetworkOnline = uiState.isNetworkOnline,
                            cacheStats = uiState.tileCacheStats,
                            prefetchProgress = uiState.tilePrefetchProgress,
                            canPrefetchOnlineTiles = uiState.freeMapProvider != FreeMapProvider.BUILT_IN_OFFLINE,
                            backupExportProgress = uiState.backupExportProgress,
                            savedLocalBackups = uiState.savedLocalBackups,
                            overlayInventorySummary = inventorySummary,
                            onPrefetchCurrentSector = { viewModel.prefetchCurrentSectorToDisk(context) },
                            onRefreshStats = { viewModel.refreshTileCacheStats(context) },
                            onClearCache = { viewModel.clearOsmTileCache(context) },
                            onExportBackupToLocalFile = { viewModel.exportOfflineBackupToLocalFile(context) },
                            onExportBackupToUri = { uri, archiveName ->
                                viewModel.exportOfflineBackupToUri(context, uri, archiveName)
                            },
                            onDeleteLocalBackup = { filePath -> viewModel.deleteLocalBackup(context, filePath) },
                            onDismiss = { viewModel.toggleCacheManagerSheet(false) }
                        )
                    }

                    // SOS Broadcast & Online Yellow Page Sheet
                    AnimatedVisibility(
                        visible = uiState.isSosSheetOpen,
                        enter = slideInVertically(initialOffsetY = { it }),
                        exit = slideOutVertically(targetOffsetY = { it }),
                        modifier = Modifier.align(Alignment.BottomCenter)
                    ) {
                        SosBroadcastSheet(
                            isSosPulseActive = uiState.isEmergencyBeaconActive,
                            currentLat = uiState.userMovement.lat,
                            currentLng = uiState.userMovement.lng,
                            yellowPageUrl = uiState.yellowPageUrl,
                            callsign = uiState.sosCallsign,
                            emergencyNote = uiState.sosEmergencyNote,
                            savedBroadcasts = uiState.sosBroadcasts,
                            onYellowPageUrlChanged = { viewModel.updateYellowPageUrl(it) },
                            onCallsignChanged = { viewModel.updateSosCallsign(it) },
                            onEmergencyNoteChanged = { viewModel.updateSosNote(it) },
                            onTriggerSosBroadcast = { viewModel.triggerSosBroadcast(context) },
                            onStopSosPulse = { viewModel.stopSosBroadcastPulse() },
                            onQuickShareEntry = { viewModel.shareSosEntry(context, it) },
                            onClearHistory = { viewModel.clearSosHistory() },
                            onDismiss = { viewModel.toggleSosSheet(false) }
                        )
                    }

                    // Pathfinder Bottom Drawer
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
                            avoidCctvCameras = uiState.avoidCctvCameras,
                            isNavigationTrackingActive = uiState.isNavigationTrackingActive,
                            onOriginChanged = { viewModel.setOriginCity(it) },
                            onDestinationChanged = { viewModel.setDestinationHaven(it) },
                            onBufferChanged = { viewModel.setStandoffBuffer(it) },
                            onToggleAvoidCctv = { viewModel.toggleAvoidCctv() },
                            onCalculateNearestSafeWalk = { viewModel.calculateNearestSafeWalk() },
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
                        avoidCctvCameras = uiState.avoidCctvCameras,
                        isNavigationTrackingActive = uiState.isNavigationTrackingActive,
                        onOriginChanged = { viewModel.setOriginCity(it) },
                        onDestinationChanged = { viewModel.setDestinationHaven(it) },
                        onBufferChanged = { viewModel.setStandoffBuffer(it) },
                        onToggleAvoidCctv = { viewModel.toggleAvoidCctv() },
                        onCalculateNearestSafeWalk = { viewModel.calculateNearestSafeWalk() },
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

                MainNavTab.SOURCES -> {
                    IntelSourceManagerView(
                        sources = uiState.intelSources,
                        onToggleSource = { viewModel.toggleIntelSource(it) },
                        onAddSource = { name, type, endpoint, freq, desc ->
                            viewModel.addCustomSource(name, type, endpoint, freq, desc)
                        },
                        onDeleteSource = { viewModel.deleteCustomSource(it) },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                MainNavTab.SURVIVAL -> {
                    SurvivalProtocolView(
                        isBeaconActive = uiState.isEmergencyBeaconActive,
                        onToggleBeacon = {
                            if (uiState.isEmergencyBeaconActive) {
                                viewModel.stopSosBroadcastPulse()
                            } else {
                                viewModel.triggerSosBroadcast(context)
                            }
                        },
                        currentCoords = uiState.userCoordinates,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Quick Map Layers Dropdown
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
                            text = "FREE MAP PROVIDER (NO KEY)",
                            style = MaterialTheme.typography.labelSmall,
                            color = TacticalCyan
                        )
                        FreeMapProvider.values().forEach { provider ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.setFreeMapProvider(provider)
                                        showLayersMenu = false
                                    }
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = uiState.freeMapProvider == provider,
                                    onClick = {
                                        viewModel.setFreeMapProvider(provider)
                                        showLayersMenu = false
                                    },
                                    colors = RadioButtonDefaults.colors(selectedColor = TacticalCyan)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(provider.label, style = MaterialTheme.typography.bodySmall, color = TacticalTextPrimary)
                            }
                        }

                        HorizontalDivider(color = TacticalBorder)

                        Text(
                            text = "TACTICAL OPTICS MODE",
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
                                    .padding(vertical = 2.dp),
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

                        HorizontalDivider(color = TacticalBorder)

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
                        text = "OSMDROID OFFLINE // MVT GEOJSON & HEATMAP",
                        style = MaterialTheme.typography.labelSmall,
                        color = TacticalTextSecondary
                    )
                }
            }

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
                            Icons.AutoMirrored.Filled.DirectionsWalk,
                            contentDescription = null,
                            tint = TacticalCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "SAFE WALK HUD: $activeRouteName",
                            style = MaterialTheme.typography.labelSmall,
                            color = TacticalCyan
                        )
                    }
                    Text(
                        text = "ACTIVE",
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
            Triple(MainNavTab.PATHFINDER, "SAFE WALK", Icons.AutoMirrored.Filled.DirectionsWalk),
            Triple(MainNavTab.SITREP, "SITREP", Icons.Default.Sensors),
            Triple(MainNavTab.SOURCES, "SOURCES", Icons.Default.SettingsInputAntenna),
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
                        modifier = Modifier.size(20.dp)
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
