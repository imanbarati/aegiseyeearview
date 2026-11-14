package com.example.ui.map

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.local.BreadcrumbEntity
import com.example.location.UserMovementState
import com.example.model.*
import com.example.sensor.CompassMath
import com.example.sensor.CompassReading
import com.example.service.GeoJsonThreatPolygon
import com.example.service.ThreatGeofenceAlert
import com.example.ui.map.mvt.MvtFeatureSelection
import com.example.ui.map.mvt.MvtVectorTileOverlay
import com.example.ui.map.mvt.TacticalGeoJsonOverlay
import com.example.ui.theme.*
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint as OsmGeoPoint
import org.osmdroid.util.TileSystem
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.FolderOverlay
import kotlin.math.max

@Composable
fun TacticalMapView(
    threatZones: List<ThreatZone>,
    heatmapPoints: List<ThreatHeatmapPoint>,
    safeHavens: List<SafeHaven>,
    cameras: List<SurveillanceCamera>,
    geoJsonOverlays: List<TacticalGeoJsonOverlay>,
    geoJsonThreatPolygons: List<GeoJsonThreatPolygon>,
    activeRoute: TacticalRoute?,
    mapMode: MapVisualMode,
    freeProvider: FreeMapProvider,
    showDeadZones: Boolean,
    showHeatmap: Boolean,
    showSafeHavens: Boolean,
    showCctvOverlay: Boolean,
    showRadarSweep: Boolean,
    showMvtGeoJsonOverlay: Boolean,
    showThreatPolygons: Boolean,
    isNetworkOnline: Boolean,
    tileCacheStats: TileCacheStats?,
    selectedZone: ThreatZone?,
    selectedHaven: SafeHaven?,
    selectedCamera: SurveillanceCamera?,
    selectedMvtFeature: MvtFeatureSelection?,
    selectedThreatPolygon: GeoJsonThreatPolygon?,
    userMovement: UserMovementState,
    surveillanceExposure: UserSurveillanceExposure,
    followAgent: Boolean,
    onToggleFollowAgent: () -> Unit,
    compassReading: CompassReading,
    isHeadingLockEnabled: Boolean,
    geoJsonIntrusionAlert: ThreatGeofenceAlert?,
    isBackgroundSentinelRunning: Boolean,
    isBackgroundSentinelArmed: Boolean,
    distanceMeasurement: DistanceMeasurementState,
    breadcrumbs: List<BreadcrumbEntity>,
    showBreadcrumbs: Boolean,
    isSosPulseActive: Boolean,
    sosPulseCoords: Pair<Double, Double>?,
    onToggleHeadingLock: () -> Unit,
    onPlanBypassFromAlert: () -> Unit,
    onDismissIntrusionAlert: () -> Unit,
    onToggleSentinel: () -> Unit,
    onMeasurePointTapped: (GeoPoint) -> Unit,
    onToggleMeasureMode: () -> Unit,
    onSnapMeasurePointAToGps: () -> Unit,
    onClearMeasurePoints: () -> Unit,
    onCloseMeasureMode: () -> Unit,
    onOpenSosBroadcast: () -> Unit,
    threatWarning: String?,
    onZoneSelected: (ThreatZone) -> Unit,
    onHavenSelected: (SafeHaven) -> Unit,
    onCameraSelected: (SurveillanceCamera) -> Unit,
    onMvtFeatureSelected: (MvtFeatureSelection?) -> Unit,
    onThreatPolygonSelected: (GeoJsonThreatPolygon?) -> Unit,
    onBoundsChanged: (BoundingBox) -> Unit,
    onOpenCctvScanner: () -> Unit,
    onOpenGeoJsonManager: () -> Unit,
    onOpenCacheManager: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current

    // Configure OsmDroid private directories & 30-day offline tile disk cache policy
    remember(context) {
        OsmTileDiskCacheManager.configureCachePolicy(context)
        true
    }

    var selectedPointName by remember { mutableStateOf<String?>(null) }
    var selectedPointCoords by remember { mutableStateOf<Pair<Double, Double>?>(null) }

    // Synchronized viewport state bridging OsmDroid MapView -> Compose ThreatHeatmapOverlay
    var syncedViewport by remember {
        mutableStateOf<MercatorViewport?>(
            MercatorViewport(
                centerLat = userMovement.lat,
                centerLng = userMovement.lng,
                zoom = if (activeRoute?.isWalkingRoute == true) 11.4 else 5.8,
                widthPx = 1080f,
                heightPx = 1920f
            )
        )
    }

    val activeProvider = remember(mapMode, freeProvider) {
        if (freeProvider == FreeMapProvider.BUILT_IN_OFFLINE) {
            FreeMapProvider.BUILT_IN_OFFLINE
        } else if (mapMode == MapVisualMode.SATELLITE_RECON) {
            FreeMapProvider.ESRI_SATELLITE
        } else {
            freeProvider
        }
    }

    // Infinite HUD animations
    val infiniteTransition = rememberInfiniteTransition(label = "OsmTacticalHud")
    val radarAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "RadarAngle"
    )
    val pulseProgress by infiniteTransition.animateFloat(
        initialValue = 0.0f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseProgress"
    )
    val dashOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 60f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "DashOffset"
    )

    // Latest callbacks for native OsmDroid overlays
    val latestOnCamera by rememberUpdatedState(onCameraSelected)
    val latestOnHaven by rememberUpdatedState(onHavenSelected)
    val latestOnZone by rememberUpdatedState(onZoneSelected)
    val latestOnMvtFeature by rememberUpdatedState(onMvtFeatureSelected)
    val latestOnThreatPolygon by rememberUpdatedState(onThreatPolygonSelected)
    val latestOnBoundsChanged by rememberUpdatedState(onBoundsChanged)
    val latestOnMeasureTapped by rememberUpdatedState(onMeasurePointTapped)

    val threatPolygonsFolder = remember { FolderOverlay() }

    val measureOverlay = remember {
        DistanceMeasurementOsmOverlay { tappedGeo ->
            latestOnMeasureTapped(tappedGeo)
        }
    }

    val mvtOverlay = remember {
        MvtVectorTileOverlay { selection ->
            selectedPointName = selection.displayTitle
            val firstGeo = selection.feature.geoGeometries.flatten().firstOrNull()
            selectedPointCoords = firstGeo?.let { it.lat to it.lng }
            latestOnMvtFeature(selection)
        }
    }

    val tacticalOverlay = remember {
        TacticalOsmOverlay(
            onCameraTapped = { cam ->
                selectedPointName = cam.name
                selectedPointCoords = cam.lat to cam.lng
                latestOnCamera(cam)
            },
            onHavenTapped = { haven ->
                selectedPointName = haven.name
                selectedPointCoords = haven.lat to haven.lng
                latestOnHaven(haven)
            },
            onZoneTapped = { zone ->
                selectedPointName = zone.name
                selectedPointCoords = zone.centerLat to zone.centerLng
                latestOnZone(zone)
            },
            onCityTapped = { city ->
                selectedPointName = city.name
                selectedPointCoords = city.lat to city.lng
            },
            onEmptyTapped = {
                selectedPointName = null
                selectedPointCoords = null
            }
        )
    }

    val mapView = remember(context) {
        MapView(context).apply {
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            isTilesScaledToDpi = false
            minZoomLevel = 3.0
            maxZoomLevel = 18.0
            controller.setZoom(if (activeRoute?.isWalkingRoute == true) 11.4 else 6.0)
            controller.setCenter(OsmGeoPoint(userMovement.lat, userMovement.lng))
            OsmDroidConfig.configureNightModeTilesOverlay(overlayManager.tilesOverlay, mapMode, activeProvider)
            overlays.add(mvtOverlay)
            overlays.add(threatPolygonsFolder)
            overlays.add(tacticalOverlay)
            overlays.add(measureOverlay)
        }
    }

    fun syncViewportFromOsm(view: MapView) {
        if (view.width <= 0 || view.height <= 0) return
        val center = view.mapCenter
        val zoom = view.zoomLevelDouble
        syncedViewport = MercatorViewport(
            centerLat = center.latitude,
            centerLng = center.longitude,
            zoom = zoom,
            widthPx = view.width.toFloat(),
            heightPx = view.height.toFloat(),
            worldSizePxOverride = TileSystem.MapSize(zoom)
        )
        try {
            latestOnBoundsChanged(view.boundingBox)
        } catch (_: Exception) {
        }
    }

    // Lifecycle & MapListener wiring
    DisposableEffect(lifecycleOwner, mapView) {
        val mapListener = object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean {
                syncViewportFromOsm(mapView)
                return false
            }

            override fun onZoom(event: ZoomEvent?): Boolean {
                syncViewportFromOsm(mapView)
                return false
            }
        }
        val layoutListener = android.view.View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            syncViewportFromOsm(mapView)
        }

        mapView.addMapListener(mapListener)
        mapView.addOnLayoutChangeListener(layoutListener)

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_DESTROY -> mapView.onDetach()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.removeMapListener(mapListener)
            mapView.removeOnLayoutChangeListener(layoutListener)
            mapView.onDetach()
        }
    }

    // Switch OsmDroid tile provider + automatically toggle data connection on connectivity loss
    LaunchedEffect(activeProvider, mapMode, isNetworkOnline) {
        if (activeProvider == FreeMapProvider.BUILT_IN_OFFLINE) {
            mapView.setUseDataConnection(false)
            mapView.setTileProvider(OsmDroidConfig.createOfflineProceduralProvider(context))
        } else {
            // When connectivity drops, disable network fetch so OsmDroid serves cached SQLite tiles seamlessly
            mapView.setUseDataConnection(isNetworkOnline)
            val source = OsmDroidConfig.tileSourceFor(activeProvider)
            if (mapView.tileProvider !is MapTileProviderBasic) {
                mapView.setTileProvider(MapTileProviderBasic(context, source))
            } else {
                mapView.setTileSource(source)
            }
        }

        // Apply custom God's Eye night-mode ColorMatrix & dark tile-loading grid styling
        OsmDroidConfig.configureNightModeTilesOverlay(
            tilesOverlay = mapView.overlayManager.tilesOverlay,
            mapMode = mapMode,
            provider = activeProvider
        )
        mapView.invalidate()
    }

    // Rebuild clickable GeoJSON Threat Polygons when polygon list, toggle, or selection changes
    LaunchedEffect(geoJsonThreatPolygons, showThreatPolygons, selectedThreatPolygon) {
        ThreatPolygonOverlayBuilder.rebuildFolder(
            folder = threatPolygonsFolder,
            mapView = mapView,
            polygons = geoJsonThreatPolygons,
            showPolygons = showThreatPolygons,
            selectedPolygonId = selectedThreatPolygon?.id,
            onPolygonClick = { poly ->
                selectedPointName = poly.name
                selectedPointCoords = poly.centerLat to poly.centerLng
                latestOnThreatPolygon(poly)
            }
        )
        mapView.invalidate()
    }

    // Sync MVT vector overlay & tactical overlays + animation invalidation
    LaunchedEffect(
        threatZones,
        safeHavens,
        cameras,
        geoJsonOverlays,
        showMvtGeoJsonOverlay,
        showThreatPolygons,
        activeRoute,
        userMovement,
        mapMode,
        showDeadZones,
        showSafeHavens,
        showCctvOverlay,
        selectedPointName,
        selectedPointCoords,
        selectedMvtFeature,
        distanceMeasurement,
        breadcrumbs,
        showBreadcrumbs,
        isSosPulseActive,
        sosPulseCoords,
        pulseProgress,
        dashOffset
    ) {
        mvtOverlay.updateOverlays(
            overlays = if (showMvtGeoJsonOverlay) geoJsonOverlays else emptyList(),
            selected = selectedMvtFeature,
            renderPolygons = !showThreatPolygons
        )
        tacticalOverlay.threatZones = threatZones
        tacticalOverlay.safeHavens = safeHavens
        tacticalOverlay.cameras = cameras
        tacticalOverlay.activeRoute = activeRoute
        tacticalOverlay.breadcrumbs = breadcrumbs
        tacticalOverlay.showBreadcrumbs = showBreadcrumbs
        tacticalOverlay.isSosPulseActive = isSosPulseActive
        tacticalOverlay.sosPulseCoords = sosPulseCoords
        tacticalOverlay.userMovement = userMovement
        tacticalOverlay.mapMode = mapMode
        tacticalOverlay.showDeadZones = showDeadZones
        tacticalOverlay.showSafeHavens = showSafeHavens
        tacticalOverlay.showCctvOverlay = showCctvOverlay
        tacticalOverlay.selectedLabel = selectedPointName
        tacticalOverlay.selectedCoords = selectedPointCoords
        tacticalOverlay.pulseProgress = pulseProgress
        tacticalOverlay.dashOffset = dashOffset
        measureOverlay.measurementState = distanceMeasurement
        measureOverlay.pulseProgress = pulseProgress
        mapView.invalidate()
    }

    // Sync OsmDroid map orientation when heading-up compass lock is active
    LaunchedEffect(compassReading.headingDegrees, isHeadingLockEnabled) {
        val targetOrientation = if (isHeadingLockEnabled) -compassReading.headingDegrees else 0f
        if (kotlin.math.abs(mapView.mapOrientation - targetOrientation) > 0.5f) {
            mapView.mapOrientation = targetOrientation
            mapView.invalidate()
        }
    }

    // Trigger Compose UI haptic confirmation on new GeoJSON intrusion alert
    LaunchedEffect(geoJsonIntrusionAlert?.polygonId) {
        if (geoJsonIntrusionAlert != null) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    // Follow agent location
    LaunchedEffect(userMovement.lat, userMovement.lng, followAgent) {
        if (followAgent) {
            mapView.controller.animateTo(OsmGeoPoint(userMovement.lat, userMovement.lng))
            if (mapView.zoomLevelDouble < 12.5) {
                mapView.controller.setZoom(13.0)
            }
            syncViewportFromOsm(mapView)
        }
    }

    // Auto-frame active walking / evacuation route
    LaunchedEffect(activeRoute?.id, activeRoute?.path?.size) {
        if (activeRoute != null && activeRoute.path.size >= 2) {
            val lats = activeRoute.path.map { it.lat }
            val lngs = activeRoute.path.map { it.lng }
            val minLa = lats.min()
            val maxLa = lats.max()
            val minLn = lngs.min()
            val maxLn = lngs.max()
            val padLat = max((maxLa - minLa) * 0.25, 0.02)
            val padLng = max((maxLn - minLn) * 0.25, 0.02)
            val box = BoundingBox(maxLa + padLat, maxLn + padLng, minLa - padLat, minLn - padLng)
            mapView.post {
                if (mapView.width > 0 && mapView.height > 0) {
                    mapView.zoomToBoundingBox(box, true)
                    syncViewportFromOsm(mapView)
                }
            }
        }
    }

    // Center reticle when selecting items from sheets
    LaunchedEffect(selectedZone) {
        if (selectedZone != null) {
            selectedPointName = selectedZone.name
            selectedPointCoords = selectedZone.centerLat to selectedZone.centerLng
        }
    }
    LaunchedEffect(selectedHaven) {
        if (selectedHaven != null) {
            selectedPointName = selectedHaven.name
            selectedPointCoords = selectedHaven.lat to selectedHaven.lng
        }
    }
    LaunchedEffect(selectedCamera) {
        if (selectedCamera != null) {
            mapView.controller.animateTo(OsmGeoPoint(selectedCamera.lat, selectedCamera.lng))
            if (mapView.zoomLevelDouble < 13.8) {
                mapView.controller.setZoom(14.2)
            }
            selectedPointName = selectedCamera.name
            selectedPointCoords = selectedCamera.lat to selectedCamera.lng
            syncViewportFromOsm(mapView)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                when (mapMode) {
                    MapVisualMode.TACTICAL_HUD -> TacticalDarkBg
                    MapVisualMode.SATELLITE_RECON -> Color(0xFF04070C)
                    MapVisualMode.FLIR_THERMAL -> Color(0xFF07000B)
                }
            )
    ) {
        // 1. OsmDroid Base Map + Clickable GeoJSON Threat Polygons + MVT Overlay + Tactical Overlay
        AndroidView(
            factory = { mapView },
            modifier = Modifier
                .fillMaxSize()
                .testTag("osmdroid_tactical_map")
        )

        // 2. Compose Threat Heatmap, CCTV Optical FOV & Radar Sweep Overlay
        ThreatHeatmapOverlay(
            heatmapPoints = heatmapPoints,
            cameras = cameras,
            viewport = syncedViewport,
            showHeatmap = showHeatmap,
            showCctvOverlay = showCctvOverlay,
            showRadarSweep = showRadarSweep,
            mapMode = mapMode,
            pulseProgress = pulseProgress,
            radarAngle = radarAngle,
            modifier = Modifier.fillMaxSize()
        )

        // 2b. Pulsing red intrusion perimeter border when inside a high-intensity GeoJSON threat zone
        if (geoJsonIntrusionAlert != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(
                        width = (2.5f + pulseProgress * 2.5f).dp,
                        color = ThreatCriticalRed.copy(alpha = 0.45f + pulseProgress * 0.45f)
                    )
                    .testTag("geojson_intrusion_perimeter_glow")
            )
        }

        // 3. Top-Left HUD Stack
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .widthIn(max = 250.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isSosPulseActive) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(ThreatCriticalRed.copy(alpha = 0.85f + pulseProgress * 0.14f))
                        .border(1.5.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                        .clickable(onClick = onOpenSosBroadcast)
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                        .testTag("active_sos_hud_banner"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Campaign,
                        contentDescription = "SOS Broadcast Active",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "SOS BROADCAST PULSING • TAP TO SHARE",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            AnimatedVisibility(
                visible = geoJsonIntrusionAlert != null,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                geoJsonIntrusionAlert?.let { alert ->
                    GeoJsonIntrusionAlertBanner(
                        alert = alert,
                        pulseProgress = pulseProgress,
                        onPlanBypass = onPlanBypassFromAlert,
                        onDismiss = onDismissIntrusionAlert
                    )
                }
            }

            if (threatWarning != null && geoJsonIntrusionAlert == null) {
                Row(
                    modifier = Modifier
                        .background(ThreatCriticalRed.copy(alpha = 0.92f), RoundedCornerShape(8.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Proximity Alert",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = threatWarning,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White
                    )
                }
            }

            if (showHeatmap) {
                HeatmapLegendBar()
            }

            if (activeRoute != null) {
                Row(
                    modifier = Modifier
                        .background(TacticalSurface.copy(alpha = 0.92f), RoundedCornerShape(8.dp))
                        .border(1.dp, ThreatLowGreen.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (activeRoute.isWalkingRoute) Icons.AutoMirrored.Filled.DirectionsWalk else Icons.Default.Route,
                        contentDescription = null,
                        tint = ThreatLowGreen,
                        modifier = Modifier.size(16.dp)
                    )
                    Column {
                        Text(
                            text = "${activeRoute.destination} (${String.format("%.1f", activeRoute.distanceKm)} KM)",
                            style = MaterialTheme.typography.labelMedium,
                            color = ThreatLowGreen
                        )
                        val walkInfo = if (activeRoute.estWalkMinutes > 0) "WALK ~${activeRoute.estWalkMinutes} MIN | " else ""
                        Text(
                            text = "${walkInfo}RISK ${activeRoute.riskScore}/100 | AVOIDED ${activeRoute.cctvCamerasAvoided} CAM",
                            style = MaterialTheme.typography.labelSmall,
                            color = TacticalTextSecondary
                        )
                    }
                }
            }

            // GeoJSON Threat Polygons & MVT Pill
            val enabledPolys = geoJsonThreatPolygons.count { it.isEnabled }
            val enabledGeoJsonCount = geoJsonOverlays.count { it.isEnabled }
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(TacticalSurface.copy(alpha = 0.88f))
                    .border(1.dp, TacticalBorder, RoundedCornerShape(6.dp))
                    .clickable(onClick = onOpenGeoJsonManager)
                    .padding(horizontal = 8.dp, vertical = 5.dp)
                    .testTag("mvt_geojson_status_pill"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Layers,
                    contentDescription = "Manage GeoJSON & MVT Overlays",
                    tint = TacticalCyan,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = "THREAT POLYS: $enabledPolys | MVT: $enabledGeoJsonCount LAYERS",
                    style = MaterialTheme.typography.labelSmall,
                    color = TacticalCyan
                )
            }

            // Disk Cache & Offline Failover Status Pill
            val cacheColor = if (!isNetworkOnline) DivisionAmber else ThreatLowGreen
            val tileText = if (!isNetworkOnline) {
                "OFFLINE FAILOVER // DISK CACHE (${tileCacheStats?.estimatedTileCount ?: 0} TILES)"
            } else {
                val mb = tileCacheStats?.usedMb ?: 0.0
                "DISK CACHE: ${tileCacheStats?.estimatedTileCount ?: 0} TILES (${String.format("%.1f", mb)} MB)"
            }
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(TacticalSurface.copy(alpha = 0.90f))
                    .border(1.dp, cacheColor.copy(alpha = 0.65f), RoundedCornerShape(6.dp))
                    .clickable(onClick = onOpenCacheManager)
                    .padding(horizontal = 8.dp, vertical = 5.dp)
                    .testTag("disk_cache_status_pill"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = if (isNetworkOnline) Icons.Default.Storage else Icons.Default.CloudOff,
                    contentDescription = "Open OsmDroid Disk Cache Manager",
                    tint = cacheColor,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = tileText,
                    style = MaterialTheme.typography.labelSmall,
                    color = cacheColor
                )
            }
            // Background GeoJSON Threat Proximity Sentinel Status Pill
            val sentinelColor = when {
                !isBackgroundSentinelArmed -> TacticalTextDim
                isBackgroundSentinelRunning -> ThreatLowGreen
                else -> DivisionAmber
            }
            val sentinelLabel = when {
                !isBackgroundSentinelArmed -> "THREAT SENTINEL: PAUSED"
                isBackgroundSentinelRunning -> "SENTINEL BG SERVICE: ARMED"
                else -> "SENTINEL FOREGROUND: ARMED"
            }
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(TacticalSurface.copy(alpha = 0.90f))
                    .border(1.dp, sentinelColor.copy(alpha = 0.65f), RoundedCornerShape(6.dp))
                    .clickable(onClick = onToggleSentinel)
                    .padding(horizontal = 8.dp, vertical = 5.dp)
                    .testTag("threat_sentinel_status_pill"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = if (isBackgroundSentinelArmed) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff,
                    contentDescription = "Toggle background threat proximity alerts",
                    tint = sentinelColor,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = sentinelLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = sentinelColor
                )
            }
        }

        // 4. Top-Right: OSINT CCTV Exposure HUD Pill + Persistent Rotating Tactical Compass Overlay
        val nextWaypoint = activeRoute?.path?.getOrNull(1) ?: activeRoute?.path?.lastOrNull()
        val waypointBearing = nextWaypoint?.let { wp ->
            CompassMath.bearingBetweenDegrees(userMovement.lat, userMovement.lng, wp.lat, wp.lng)
        }
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SurveillanceExposureHudPill(
                exposure = surveillanceExposure,
                onClick = onOpenCctvScanner
            )

            FloatingTacticalCompass(
                compassReading = compassReading,
                isHeadingLockEnabled = isHeadingLockEnabled,
                targetBearingDegrees = waypointBearing,
                onToggleHeadingLock = onToggleHeadingLock
            )
        }

        // 5. Right-Edge: SOS Broadcast, Zoom, Cache, Rangefinder & Follow-Agent Controls
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SmallFloatingActionButton(
                onClick = onOpenSosBroadcast,
                containerColor = if (isSosPulseActive) ThreatCriticalRed else Color(0xFFB71C1C),
                contentColor = Color.White,
                modifier = Modifier
                    .border(
                        width = if (isSosPulseActive) (1.5f + pulseProgress * 1.5f).dp else 1.dp,
                        color = Color.White.copy(alpha = if (isSosPulseActive) 0.9f else 0.5f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .testTag("sos_broadcast_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Sos,
                        contentDescription = "SOS Broadcast",
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = if (isSosPulseActive) "LIVE" else "SOS",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            SmallFloatingActionButton(
                onClick = {
                    mapView.controller.zoomIn()
                    syncViewportFromOsm(mapView)
                },
                containerColor = TacticalSurface.copy(alpha = 0.92f),
                contentColor = TacticalCyan,
                modifier = Modifier.testTag("map_zoom_in_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Zoom In")
            }

            SmallFloatingActionButton(
                onClick = {
                    mapView.controller.zoomOut()
                    syncViewportFromOsm(mapView)
                },
                containerColor = TacticalSurface.copy(alpha = 0.92f),
                contentColor = TacticalCyan,
                modifier = Modifier.testTag("map_zoom_out_button")
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Zoom Out")
            }

            SmallFloatingActionButton(
                onClick = onOpenCacheManager,
                containerColor = TacticalSurface.copy(alpha = 0.92f),
                contentColor = if (isNetworkOnline) TacticalCyan else DivisionAmber,
                modifier = Modifier.testTag("map_disk_cache_button")
            ) {
                Icon(Icons.Default.DownloadForOffline, contentDescription = "Offline Tile Disk Cache")
            }

            SmallFloatingActionButton(
                onClick = onToggleMeasureMode,
                containerColor = if (distanceMeasurement.isActive) TacticalCyan else TacticalSurface.copy(alpha = 0.92f),
                contentColor = if (distanceMeasurement.isActive) TacticalDarkBg else TacticalCyan,
                modifier = Modifier.testTag("map_measure_distance_button")
            ) {
                Icon(Icons.Default.Straighten, contentDescription = "Measure Two-Point Distance")
            }

            SmallFloatingActionButton(
                onClick = {
                    onToggleFollowAgent()
                    mapView.controller.animateTo(OsmGeoPoint(userMovement.lat, userMovement.lng))
                    if (mapView.zoomLevelDouble < 13.0) {
                        mapView.controller.setZoom(13.5)
                    }
                    syncViewportFromOsm(mapView)
                },
                containerColor = if (followAgent) DivisionOrange else TacticalSurface.copy(alpha = 0.92f),
                contentColor = if (followAgent) TacticalDarkBg else DivisionOrange,
                modifier = Modifier.testTag("map_recenter_agent_button")
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = "Center on Agent GPS")
            }
        }

        // 6. Two-Point Distance Rangefinder HUD Card (positioned cleanly above floating layer bar)
        AnimatedVisibility(
            visible = distanceMeasurement.isActive || distanceMeasurement.pointA != null,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 12.dp, end = 12.dp, bottom = 74.dp)
        ) {
            DistanceMeasurementHudCard(
                state = distanceMeasurement,
                onSnapPointAToGps = onSnapMeasurePointAToGps,
                onClearPoints = onClearMeasurePoints,
                onCloseMode = onCloseMeasureMode
            )
        }
    }
}

@Composable
private fun SurveillanceExposureHudPill(
    exposure: UserSurveillanceExposure,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val badgeColor = if (exposure.isExposed) ThreatCriticalRed else ThreatLowGreen
    val bgColor = if (exposure.isExposed) ThreatCriticalRed.copy(alpha = 0.22f) else ThreatLowGreen.copy(alpha = 0.16f)

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(TacticalSurface.copy(alpha = 0.94f))
            .background(bgColor)
            .border(1.dp, badgeColor, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp)
            .testTag("cctv_exposure_hud_pill"),
        horizontalAlignment = Alignment.End
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(badgeColor, CircleShape)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = exposure.statusBadge,
                style = MaterialTheme.typography.labelMedium,
                color = badgeColor
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.Videocam,
                contentDescription = "Inspect nearby OSINT and state CCTV cameras",
                tint = badgeColor,
                modifier = Modifier.size(14.dp)
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "VULN OSINT:${exposure.vulnerablePublicNearbyCount} | STATE:${exposure.stateEncryptedNearbyCount} | BLIND:${exposure.offlineBlindSpotsNearbyCount}",
            style = MaterialTheme.typography.labelSmall,
            color = TacticalTextSecondary
        )
    }
}

@Composable
private fun GeoJsonIntrusionAlertBanner(
    alert: ThreatGeofenceAlert,
    pulseProgress: Float,
    onPlanBypass: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgAlpha = 0.86f + pulseProgress * 0.12f
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(ThreatCriticalRed.copy(alpha = bgAlpha))
            .border(1.5.dp, Color.White.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
            .padding(10.dp)
            .testTag("geojson_intrusion_alert_banner"),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(
                    imageVector = Icons.Default.GppMaybe,
                    contentDescription = "High Intensity GeoJSON Threat Zone Alert",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "ZONE BREACH // ${alert.sectorCode}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(28.dp).testTag("dismiss_intrusion_alert_button")
            ) {
                Icon(Icons.Default.Close, contentDescription = "Dismiss Alert", tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }

        Text(
            text = "${alert.zoneName} (${(alert.intensity * 100).toInt()}% intensity • ${alert.casualtyRisk})",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.94f)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onPlanBypass,
                colors = ButtonDefaults.buttonColors(
                    containerColor = TacticalDarkBg,
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp).testTag("intrusion_evade_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.AltRoute, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("EVADE ZONE", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
