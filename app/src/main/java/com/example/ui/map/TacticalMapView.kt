package com.example.ui.map

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import androidx.compose.animation.core.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.location.UserMovementState
import com.example.model.*
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
import kotlin.math.max

@Composable
fun TacticalMapView(
    threatZones: List<ThreatZone>,
    heatmapPoints: List<ThreatHeatmapPoint>,
    safeHavens: List<SafeHaven>,
    cameras: List<SurveillanceCamera>,
    geoJsonOverlays: List<TacticalGeoJsonOverlay>,
    activeRoute: TacticalRoute?,
    mapMode: MapVisualMode,
    freeProvider: FreeMapProvider,
    showDeadZones: Boolean,
    showHeatmap: Boolean,
    showSafeHavens: Boolean,
    showCctvOverlay: Boolean,
    showRadarSweep: Boolean,
    showMvtGeoJsonOverlay: Boolean,
    selectedZone: ThreatZone?,
    selectedHaven: SafeHaven?,
    selectedCamera: SurveillanceCamera?,
    selectedMvtFeature: MvtFeatureSelection?,
    userMovement: UserMovementState,
    surveillanceExposure: UserSurveillanceExposure,
    followAgent: Boolean,
    onToggleFollowAgent: () -> Unit,
    threatWarning: String?,
    onZoneSelected: (ThreatZone) -> Unit,
    onHavenSelected: (SafeHaven) -> Unit,
    onCameraSelected: (SurveillanceCamera) -> Unit,
    onMvtFeatureSelected: (MvtFeatureSelection?) -> Unit,
    onOpenCctvScanner: () -> Unit,
    onOpenGeoJsonManager: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Ensure OsmDroid initializes strictly with private cache/files paths (zero storage permissions)
    remember(context) {
        OsmDroidConfig.ensureInitialized(context)
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

    // Keep latest callbacks for native OsmDroid overlays
    val latestOnCamera by rememberUpdatedState(onCameraSelected)
    val latestOnHaven by rememberUpdatedState(onHavenSelected)
    val latestOnZone by rememberUpdatedState(onZoneSelected)
    val latestOnMvtFeature by rememberUpdatedState(onMvtFeatureSelected)

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
            overlays.add(mvtOverlay)
            overlays.add(tacticalOverlay)
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
    }

    // Lifecycle & MapListener wiring for OsmDroid MapView
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

    // Switch OsmDroid tile provider cleanly between Offline Procedural Vector and free online tiles
    LaunchedEffect(activeProvider, mapMode) {
        if (activeProvider == FreeMapProvider.BUILT_IN_OFFLINE) {
            mapView.setUseDataConnection(false)
            mapView.setTileProvider(OsmDroidConfig.createOfflineProceduralProvider(context))
        } else {
            mapView.setUseDataConnection(true)
            val source = OsmDroidConfig.tileSourceFor(activeProvider)
            if (mapView.tileProvider !is MapTileProviderBasic) {
                mapView.setTileProvider(MapTileProviderBasic(context, source))
            } else {
                mapView.setTileSource(source)
            }
        }

        // Apply FLIR thermal color filter natively to OsmDroid tiles
        val tilesOverlay = mapView.overlayManager.tilesOverlay
        if (mapMode == MapVisualMode.FLIR_THERMAL) {
            tilesOverlay.setColorFilter(
                ColorMatrixColorFilter(
                    ColorMatrix(
                        floatArrayOf(
                            1.45f, 0f, 0f, 0f, 20f,
                            0f, 0.35f, 0.2f, 0f, 0f,
                            0.4f, 0f, 1.1f, 0f, 25f,
                            0f, 0f, 0f, 1f, 0f
                        )
                    )
                )
            )
        } else {
            tilesOverlay.setColorFilter(null)
        }
        mapView.invalidate()
    }

    // Sync tactical & MVT overlay properties + invalidate OsmDroid canvas for smooth animation
    LaunchedEffect(
        threatZones,
        safeHavens,
        cameras,
        geoJsonOverlays,
        showMvtGeoJsonOverlay,
        activeRoute,
        userMovement,
        mapMode,
        showDeadZones,
        showSafeHavens,
        showCctvOverlay,
        selectedPointName,
        selectedPointCoords,
        selectedMvtFeature,
        pulseProgress,
        dashOffset
    ) {
        mvtOverlay.updateOverlays(
            overlays = if (showMvtGeoJsonOverlay) geoJsonOverlays else emptyList(),
            selected = selectedMvtFeature
        )
        tacticalOverlay.threatZones = threatZones
        tacticalOverlay.safeHavens = safeHavens
        tacticalOverlay.cameras = cameras
        tacticalOverlay.activeRoute = activeRoute
        tacticalOverlay.userMovement = userMovement
        tacticalOverlay.mapMode = mapMode
        tacticalOverlay.showDeadZones = showDeadZones
        tacticalOverlay.showSafeHavens = showSafeHavens
        tacticalOverlay.showCctvOverlay = showCctvOverlay
        tacticalOverlay.selectedLabel = selectedPointName
        tacticalOverlay.selectedCoords = selectedPointCoords
        tacticalOverlay.pulseProgress = pulseProgress
        tacticalOverlay.dashOffset = dashOffset
        mapView.invalidate()
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

    // Center reticle when selecting items from sheets/lists
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
        // 1. OsmDroid Offline-Capable Base Map + MVT Vector + Tactical Overlays
        AndroidView(
            factory = { mapView },
            modifier = Modifier
                .fillMaxSize()
                .testTag("osmdroid_tactical_map")
        )

        // 2. Custom Compose Threat Heatmap, CCTV Optical FOV & Radar Sweep Overlay
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

        // 3. Top-Left HUD Stack
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (threatWarning != null) {
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
                        imageVector = if (activeRoute.isWalkingRoute) Icons.Default.DirectionsWalk else Icons.Default.Route,
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

            // Provider + MVT / GeoJSON Active Status Chip
            val enabledGeoJsonCount = geoJsonOverlays.count { it.isEnabled }
            val totalMvtFeatures = geoJsonOverlays.filter { it.isEnabled }.sumOf { it.featureCount }
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
                    contentDescription = "Manage GeoJSON MVT Overlays",
                    tint = TacticalCyan,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = "OSMDROID: ${activeProvider.label.uppercase()} | MVT: $enabledGeoJsonCount LAYERS ($totalMvtFeatures FT)",
                    style = MaterialTheme.typography.labelSmall,
                    color = TacticalCyan
                )
            }
        }

        // 4. Top-Right: OSINT & State CCTV Vulnerability HUD Pill
        SurveillanceExposureHudPill(
            exposure = surveillanceExposure,
            onClick = onOpenCctvScanner,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
        )

        // 5. Right-Edge: Zoom & Follow-Agent Controls
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
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
