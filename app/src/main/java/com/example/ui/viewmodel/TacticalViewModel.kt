package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.TacticalDataProvider
import com.example.data.local.AegisLocalDatabase
import com.example.data.local.BreadcrumbEntity
import com.example.data.local.SosBroadcastEntity
import com.example.data.repository.BreadcrumbDistanceTracker
import com.example.data.repository.EmergencyYellowPagePublisher
import com.example.data.repository.TacticalDatabaseRepository
import com.example.data.repository.YellowPagePublishResult
import com.example.location.LocationHelper
import com.example.location.UserMovementState
import com.example.model.*
import com.example.routing.TacticalPathfinder
import com.example.sensor.CompassReading
import com.example.sensor.DeviceOrientationHelper
import com.example.service.GeoJsonThreatPolygon
import com.example.service.GeoJsonThreatZoneService
import com.example.service.ThreatGeofenceAlert
import com.example.service.ThreatHapticTrigger
import com.example.service.ThreatProximityBus
import com.example.service.ThreatProximityService
import com.example.ui.map.BackupExportProgress
import com.example.ui.map.DistanceMeasurementState
import com.example.ui.map.FreeMapProvider
import com.example.ui.map.LocalBackupFileInfo
import com.example.ui.map.OfflineMapBackupExporter
import com.example.ui.map.OsmDroidConfig
import com.example.ui.map.OsmTileDiskCacheManager
import com.example.ui.map.TacticalBackupSnapshot
import com.example.ui.map.TileCacheStats
import com.example.ui.map.TilePrefetchProgress
import com.example.ui.map.mvt.GeoJsonTacticalLoader
import com.example.ui.map.mvt.MvtFeatureSelection
import com.example.ui.map.mvt.TacticalGeoJsonOverlay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.BoundingBox

enum class MainNavTab(val title: String, val code: String) {
    MAP("GOD'S EYE", "MAP-01"),
    PATHFINDER("SAFE WALK", "NAV-02"),
    SITREP("SITREP", "INTEL-03"),
    SOURCES("INTEL & SDR", "SDR-04"),
    SURVIVAL("PROTOCOLS", "SURV-05")
}

data class TacticalUiState(
    val activeTab: MainNavTab = MainNavTab.MAP,
    val threatZones: List<ThreatZone> = TacticalDataProvider.THREAT_ZONES,
    val heatmapPoints: List<ThreatHeatmapPoint> = TacticalDataProvider.THREAT_HEATMAP_POINTS,
    val safeHavens: List<SafeHaven> = TacticalDataProvider.SAFE_HAVENS,
    val cameras: List<SurveillanceCamera> = TacticalDataProvider.SURVEILLANCE_CAMERAS,
    val liveIncidents: List<LiveIncident> = TacticalDataProvider.LIVE_INCIDENTS,
    val intelSources: List<IntelSource> = TacticalDataProvider.DEFAULT_INTEL_SOURCES,
    val originCity: String = "Tehran",
    val destinationHavenId: String? = null, // null = Auto-select nearest designated Safe Zone
    val standoffBufferKm: Double = 22.0, // A* heatmap threat weight
    val avoidCctvCameras: Boolean = true,
    val activeRoute: TacticalRoute = TacticalPathfinder.findSafestWalkingRoute(
        startLat = 35.6892,
        startLng = 51.3890,
        safeHavens = TacticalDataProvider.SAFE_HAVENS,
        heatmapPoints = TacticalDataProvider.THREAT_HEATMAP_POINTS,
        cameras = TacticalDataProvider.SURVEILLANCE_CAMERAS,
        targetSafeHavenId = null,
        avoidCctv = true,
        threatWeight = 22.0
    ),
    val selectedZone: ThreatZone? = null,
    val selectedHaven: SafeHaven? = null,
    val selectedCamera: SurveillanceCamera? = null,
    val isCctvScannerOpen: Boolean = false,
    val mapMode: MapVisualMode = MapVisualMode.TACTICAL_HUD,
    val freeMapProvider: FreeMapProvider = FreeMapProvider.CARTO_DARK,
    val showDeadZones: Boolean = true,
    val showHeatmap: Boolean = true,
    val showSafeHavens: Boolean = true,
    val showCctvOverlay: Boolean = true,
    val showRadarSweep: Boolean = true,
    val showMvtGeoJsonOverlay: Boolean = true,
    val showThreatPolygons: Boolean = true,
    val geoJsonOverlays: List<TacticalGeoJsonOverlay> = emptyList(),
    val geoJsonThreatPolygons: List<GeoJsonThreatPolygon> = emptyList(),
    val selectedMvtFeature: MvtFeatureSelection? = null,
    val selectedThreatPolygon: GeoJsonThreatPolygon? = null,
    val isGeoJsonSheetOpen: Boolean = false,
    val isCacheManagerOpen: Boolean = false,
    val isNetworkOnline: Boolean = true,
    val tileCacheStats: TileCacheStats? = null,
    val tilePrefetchProgress: TilePrefetchProgress = TilePrefetchProgress(),
    val isNavigationTrackingActive: Boolean = false,
    val isEmergencyBeaconActive: Boolean = false,
    val userCoordinates: Pair<Double, Double> = Pair(35.6892, 51.3890),
    val userMovement: UserMovementState = UserMovementState(35.6892, 51.3890),
    val surveillanceExposure: UserSurveillanceExposure = TacticalPathfinder.evaluateCctvExposure(
        lat = 35.6892,
        lng = 51.3890,
        cameras = TacticalDataProvider.SURVEILLANCE_CAMERAS
    ),
    val followAgentOnMap: Boolean = false,
    val compassReading: CompassReading = CompassReading(),
    val isCompassHeadingLockEnabled: Boolean = false,
    val geoJsonIntrusionAlert: ThreatGeofenceAlert? = null,
    val isBackgroundSentinelRunning: Boolean = false,
    val isBackgroundSentinelArmed: Boolean = true,
    val distanceMeasurement: DistanceMeasurementState = DistanceMeasurementState(),
    val showBreadcrumbs: Boolean = true,
    val breadcrumbs: List<BreadcrumbEntity> = emptyList(),
    val sosBroadcasts: List<SosBroadcastEntity> = emptyList(),
    val isSosSheetOpen: Boolean = false,
    val sosPulseCoords: Pair<Double, Double>? = null,
    val sosCallsign: String = "AGENT-01",
    val sosEmergencyNote: String = "Requesting immediate emergency assistance / extraction.",
    val yellowPageUrl: String = "",
    val backupExportProgress: BackupExportProgress = BackupExportProgress(),
    val savedLocalBackups: List<LocalBackupFileInfo> = emptyList(),
    val threatWarning: String? = null,
    val isPathfinderExpanded: Boolean = false
)

class TacticalViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(TacticalUiState())
    val uiState: StateFlow<TacticalUiState> = _uiState.asStateFlow()

    private var locationHelper: LocationHelper? = null
    private var orientationHelper: DeviceOrientationHelper? = null
    private var latestRealGpsMovement: UserMovementState? = null
    private var currentMapBoundingBox: BoundingBox? = null
    private var monitoringStarted: Boolean = false
    private var orientationStarted: Boolean = false
    private var busObserverStarted: Boolean = false
    private var persistenceInitialized: Boolean = false
    private var repository: TacticalDatabaseRepository? = null
    private var lastBreadcrumbGeoPoint: GeoPoint? = null
    private var nextBreadcrumbSeq: Int = 1
    private var appContextRef: Context? = null

    fun setTab(tab: MainNavTab) {
        _uiState.update { it.copy(activeTab = tab) }
    }

    fun setMapMode(mode: MapVisualMode) {
        _uiState.update { it.copy(mapMode = mode) }
    }

    fun setFreeMapProvider(provider: FreeMapProvider) {
        _uiState.update { it.copy(freeMapProvider = provider) }
    }

    fun toggleDeadZones() {
        _uiState.update { it.copy(showDeadZones = !it.showDeadZones) }
    }

    fun toggleHeatmap() {
        _uiState.update { it.copy(showHeatmap = !it.showHeatmap) }
    }

    fun toggleSafeHavens() {
        _uiState.update { it.copy(showSafeHavens = !it.showSafeHavens) }
    }

    fun toggleCctvOverlay() {
        _uiState.update { it.copy(showCctvOverlay = !it.showCctvOverlay) }
    }

    fun toggleRadarSweep() {
        _uiState.update { it.copy(showRadarSweep = !it.showRadarSweep) }
    }

    fun toggleFollowAgent() {
        _uiState.update { it.copy(followAgentOnMap = !it.followAgentOnMap) }
    }

    fun toggleCompassHeadingLock() {
        _uiState.update { it.copy(isCompassHeadingLockEnabled = !it.isCompassHeadingLockEnabled) }
    }

    fun toggleMeasureDistanceMode() {
        _uiState.update { state ->
            val nextActive = !state.distanceMeasurement.isActive
            state.copy(
                distanceMeasurement = if (nextActive) {
                    state.distanceMeasurement.copy(isActive = true)
                } else {
                    DistanceMeasurementState(isActive = false)
                }
            )
        }
    }

    fun onMeasureMapPointTapped(point: GeoPoint) {
        _uiState.update { state ->
            val dm = state.distanceMeasurement
            val updated = when {
                dm.pointA == null -> dm.copy(isActive = true, pointA = point, pointB = null)
                dm.pointB == null -> dm.copy(isActive = true, pointB = point)
                else -> dm.copy(isActive = true, pointA = point, pointB = null)
            }
            state.copy(distanceMeasurement = updated)
        }
    }

    fun snapMeasurePointAToGps() {
        _uiState.update { state ->
            val gpsPt = GeoPoint(lat = state.userMovement.lat, lng = state.userMovement.lng, name = "MY GPS")
            state.copy(
                distanceMeasurement = state.distanceMeasurement.copy(
                    isActive = true,
                    pointA = gpsPt
                )
            )
        }
    }

    fun clearDistanceMeasurement() {
        _uiState.update { state ->
            state.copy(distanceMeasurement = DistanceMeasurementState(isActive = state.distanceMeasurement.isActive))
        }
    }

    fun closeDistanceMeasurementMode() {
        _uiState.update { it.copy(distanceMeasurement = DistanceMeasurementState()) }
    }

    fun dismissGeoJsonIntrusionAlert() {
        ThreatProximityBus.dismissVisualAlert()
        _uiState.update { it.copy(geoJsonIntrusionAlert = null) }
    }

    fun planBypassForIntrusionAlert() {
        val alert = _uiState.value.geoJsonIntrusionAlert ?: return
        val matchingPoly = _uiState.value.geoJsonThreatPolygons.firstOrNull { it.id == alert.polygonId }
        if (matchingPoly != null) {
            planBypassForGeoJsonPolygon(matchingPoly)
        } else {
            calculateNearestSafeWalk()
        }
    }

    fun toggleMvtGeoJsonOverlay() {
        _uiState.update { it.copy(showMvtGeoJsonOverlay = !it.showMvtGeoJsonOverlay) }
    }

    fun toggleThreatPolygons() {
        _uiState.update { it.copy(showThreatPolygons = !it.showThreatPolygons) }
    }

    fun updateMapBoundingBox(bounds: BoundingBox) {
        currentMapBoundingBox = bounds
    }

    fun selectZone(zone: ThreatZone?) {
        _uiState.update {
            it.copy(
                selectedZone = zone,
                selectedHaven = null,
                selectedCamera = null,
                selectedMvtFeature = null,
                selectedThreatPolygon = null
            )
        }
    }

    fun selectHaven(haven: SafeHaven?) {
        _uiState.update {
            it.copy(
                selectedHaven = haven,
                selectedZone = null,
                selectedCamera = null,
                selectedMvtFeature = null,
                selectedThreatPolygon = null
            )
        }
    }

    fun selectCamera(camera: SurveillanceCamera?) {
        _uiState.update {
            it.copy(
                selectedCamera = camera,
                selectedZone = null,
                selectedHaven = null,
                selectedMvtFeature = null,
                selectedThreatPolygon = null,
                isCctvScannerOpen = false
            )
        }
    }

    fun selectMvtFeature(selection: MvtFeatureSelection?) {
        _uiState.update {
            it.copy(
                selectedMvtFeature = selection,
                selectedThreatPolygon = null,
                selectedZone = null,
                selectedHaven = null,
                selectedCamera = null,
                isCctvScannerOpen = false,
                isGeoJsonSheetOpen = false,
                isCacheManagerOpen = false,
                isSosSheetOpen = false
            )
        }
    }

    fun selectThreatPolygon(polygon: GeoJsonThreatPolygon?) {
        _uiState.update {
            it.copy(
                selectedThreatPolygon = polygon,
                selectedMvtFeature = null,
                selectedZone = null,
                selectedHaven = null,
                selectedCamera = null,
                isCctvScannerOpen = false,
                isGeoJsonSheetOpen = false,
                isCacheManagerOpen = false,
                isSosSheetOpen = false
            )
        }
    }

    fun toggleCctvScanner(open: Boolean? = null) {
        _uiState.update {
            it.copy(
                isCctvScannerOpen = open ?: !it.isCctvScannerOpen,
                isGeoJsonSheetOpen = false,
                isCacheManagerOpen = false,
                isSosSheetOpen = false,
                selectedZone = null,
                selectedHaven = null,
                selectedCamera = null,
                selectedMvtFeature = null,
                selectedThreatPolygon = null
            )
        }
    }

    fun toggleGeoJsonSheet(open: Boolean? = null) {
        _uiState.update {
            it.copy(
                isGeoJsonSheetOpen = open ?: !it.isGeoJsonSheetOpen,
                isCctvScannerOpen = false,
                isCacheManagerOpen = false,
                isSosSheetOpen = false,
                selectedZone = null,
                selectedHaven = null,
                selectedCamera = null,
                selectedMvtFeature = null,
                selectedThreatPolygon = null
            )
        }
    }

    fun toggleCacheManagerSheet(open: Boolean? = null) {
        _uiState.update {
            it.copy(
                isCacheManagerOpen = open ?: !it.isCacheManagerOpen,
                isGeoJsonSheetOpen = false,
                isCctvScannerOpen = false,
                isSosSheetOpen = false,
                selectedZone = null,
                selectedHaven = null,
                selectedCamera = null,
                selectedMvtFeature = null,
                selectedThreatPolygon = null
            )
        }
    }

    fun toggleSosSheet(open: Boolean? = null) {
        _uiState.update {
            it.copy(
                isSosSheetOpen = open ?: !it.isSosSheetOpen,
                isCacheManagerOpen = false,
                isGeoJsonSheetOpen = false,
                isCctvScannerOpen = false,
                selectedZone = null,
                selectedHaven = null,
                selectedCamera = null,
                selectedMvtFeature = null,
                selectedThreatPolygon = null
            )
        }
    }

    fun initPersistence(context: Context) {
        val appCtx = context.applicationContext
        appContextRef = appCtx
        if (persistenceInitialized) return
        persistenceInitialized = true

        val db = AegisLocalDatabase.getInstance(appCtx)
        val repo = TacticalDatabaseRepository(db.sosBroadcastDao(), db.breadcrumbDao())
        repository = repo

        viewModelScope.launch {
            repo.pruneExpiredBreadcrumbs()
        }

        viewModelScope.launch {
            repo.sosBroadcasts.collect { broadcasts ->
                _uiState.update { it.copy(sosBroadcasts = broadcasts) }
            }
        }

        var initialSeedEvaluated = false
        viewModelScope.launch {
            repo.breadcrumbs.collect { crumbs ->
                if (crumbs.isNotEmpty()) {
                    val last = crumbs.last()
                    lastBreadcrumbGeoPoint = GeoPoint(last.lat, last.lng)
                    nextBreadcrumbSeq = (crumbs.maxOfOrNull { it.sequenceNumber } ?: 0) + 1
                } else if (!initialSeedEvaluated) {
                    val current = _uiState.value.userMovement
                    maybeDropBreadcrumb(
                        lat = current.lat,
                        lng = current.lng,
                        speedKmh = current.speedKmh,
                        bearingDegrees = current.bearingDegrees
                    )
                }
                initialSeedEvaluated = true
                _uiState.update { it.copy(breadcrumbs = crumbs) }
            }
        }
    }

    fun updateYellowPageUrl(url: String) {
        _uiState.update { it.copy(yellowPageUrl = url) }
    }

    fun updateSosCallsign(callsign: String) {
        _uiState.update { it.copy(sosCallsign = callsign) }
    }

    fun updateSosNote(note: String) {
        _uiState.update { it.copy(sosEmergencyNote = note) }
    }

    fun triggerSosBroadcast(context: Context) {
        val appCtx = context.applicationContext
        initPersistence(appCtx)

        val state = _uiState.value
        val movement = state.userMovement
        val endpoint = state.yellowPageUrl.trim()
        val callsign = state.sosCallsign.ifBlank { "AGENT-01" }
        val note = state.sosEmergencyNote.ifBlank { "Requesting immediate emergency extraction." }

        _uiState.update {
            it.copy(
                isEmergencyBeaconActive = true,
                sosPulseCoords = movement.lat to movement.lng
            )
        }
        ThreatHapticTrigger.triggerThreatEntryHaptic(appCtx)

        val initialStatus = if (endpoint.isBlank()) {
            "SAVED LOCAL DB • SHARE READY"
        } else {
            "SAVED LOCAL DB • PUBLISHING ONLINE..."
        }
        val draftEntity = SosBroadcastEntity(
            callsign = callsign,
            lat = movement.lat,
            lng = movement.lng,
            accuracyMeters = movement.accuracyMeters,
            emergencyMessage = note,
            yellowPageEndpoint = endpoint,
            onlineSyncStatus = initialStatus,
            onlineRecordUrl = null
        )

        val repo = repository ?: return
        viewModelScope.launch {
            val insertedId = repo.insertSosBroadcast(draftEntity)
            val savedEntity = draftEntity.copy(id = insertedId)
            when (val result = EmergencyYellowPagePublisher.publish(endpoint, savedEntity)) {
                is YellowPagePublishResult.Published -> {
                    repo.updateSosOnlineStatus(
                        id = insertedId,
                        status = "PUBLISHED ONLINE (HTTP ${result.httpCode})",
                        recordUrl = result.recordUrl
                    )
                }
                is YellowPagePublishResult.OfflineOrUnconfigured -> {
                    repo.updateSosOnlineStatus(
                        id = insertedId,
                        status = "SAVED LOCAL DB • SHARE READY",
                        recordUrl = null
                    )
                }
                is YellowPagePublishResult.Failed -> {
                    repo.updateSosOnlineStatus(
                        id = insertedId,
                        status = "SAVED LOCAL DB • ONLINE ERR: ${result.error.take(40)}",
                        recordUrl = null
                    )
                }
            }
        }
    }

    fun stopSosBroadcastPulse() {
        _uiState.update {
            it.copy(
                isEmergencyBeaconActive = false,
                sosPulseCoords = null
            )
        }
    }

    fun shareSosEntry(context: Context, entry: SosBroadcastEntity) {
        EmergencyYellowPagePublisher.launchQuickShare(context, entry)
    }

    fun clearSosHistory() {
        val repo = repository ?: return
        viewModelScope.launch {
            repo.clearAllSosBroadcasts()
        }
    }

    fun toggleBreadcrumbs() {
        _uiState.update { it.copy(showBreadcrumbs = !it.showBreadcrumbs) }
    }

    fun clearBreadcrumbs() {
        lastBreadcrumbGeoPoint = null
        nextBreadcrumbSeq = 1
        val repo = repository ?: return
        viewModelScope.launch {
            repo.clearBreadcrumbs()
        }
    }

    private fun maybeDropBreadcrumb(
        lat: Double,
        lng: Double,
        speedKmh: Float,
        bearingDegrees: Float
    ) {
        val repo = repository ?: return
        val (shouldDrop, distanceMeters) = BreadcrumbDistanceTracker.shouldDropBreadcrumb(
            lastPoint = lastBreadcrumbGeoPoint,
            candidateLat = lat,
            candidateLng = lng
        )
        if (!shouldDrop) return

        val seq = nextBreadcrumbSeq
        nextBreadcrumbSeq += 1
        lastBreadcrumbGeoPoint = GeoPoint(lat, lng)

        viewModelScope.launch {
            repo.insertBreadcrumb(
                BreadcrumbEntity(
                    sequenceNumber = seq,
                    lat = lat,
                    lng = lng,
                    speedKmh = speedKmh,
                    bearingDegrees = bearingDegrees,
                    distanceFromPreviousMeters = distanceMeters,
                    isTemporary = true
                )
            )
        }
    }

    fun loadBundledGeoJsonOverlays(context: Context) {
        appContextRef = context.applicationContext
        if (_uiState.value.geoJsonOverlays.isNotEmpty() && _uiState.value.geoJsonThreatPolygons.isNotEmpty()) return
        val appCtx = context.applicationContext
        val bundledMvt = if (_uiState.value.geoJsonOverlays.isEmpty()) {
            GeoJsonTacticalLoader.loadBundledOverlays(appCtx)
        } else emptyList()
        val bundledPolys = if (_uiState.value.geoJsonThreatPolygons.isEmpty()) {
            GeoJsonThreatZoneService.loadThreatPolygonsFromAssets(appCtx)
        } else emptyList()

        _uiState.update { state ->
            val customMvt = state.geoJsonOverlays.filter { it.isCustomImported }
            val nextMvt = if (bundledMvt.isNotEmpty()) bundledMvt + customMvt else state.geoJsonOverlays
            val nextPolys = if (bundledPolys.isNotEmpty()) bundledPolys + state.geoJsonThreatPolygons.filter { existing ->
                bundledPolys.none { it.id == existing.id }
            } else state.geoJsonThreatPolygons
            ThreatProximityBus.updatePolygons(nextPolys)
            state.copy(geoJsonOverlays = nextMvt, geoJsonThreatPolygons = nextPolys)
        }
        evaluateAndTriggerGeoJsonAlert(_uiState.value.userMovement.lat, _uiState.value.userMovement.lng)
    }

    fun toggleGeoJsonLayer(layerId: String) {
        _uiState.update { state ->
            val targetOverlay = state.geoJsonOverlays.firstOrNull { it.id == layerId }
            val updatedOverlays = state.geoJsonOverlays.map {
                if (it.id == layerId) it.copy(isEnabled = !it.isEnabled) else it
            }
            val updatedPolys = if (targetOverlay != null) {
                val nextEnabled = !targetOverlay.isEnabled
                state.geoJsonThreatPolygons.map { poly ->
                    if (poly.sourceFileName == targetOverlay.fileName) poly.copy(isEnabled = nextEnabled) else poly
                }
            } else {
                state.geoJsonThreatPolygons
            }
            ThreatProximityBus.updatePolygons(updatedPolys)
            state.copy(geoJsonOverlays = updatedOverlays, geoJsonThreatPolygons = updatedPolys)
        }
        evaluateAndTriggerGeoJsonAlert(_uiState.value.userMovement.lat, _uiState.value.userMovement.lng)
    }

    fun importCustomGeoJson(fileName: String, rawJson: String): Boolean {
        val id = "custom_geojson_${System.currentTimeMillis()}"
        val overlay = GeoJsonTacticalLoader.parseGeoJsonToMvtOverlay(
            id = id,
            fallbackName = fileName.substringBeforeLast('.'),
            fileName = fileName,
            rawJson = rawJson,
            isCustomImported = true
        )
        val threatPolys = GeoJsonThreatZoneService.parseThreatPolygons(rawJson, fileName)
        if (overlay == null && threatPolys.isEmpty()) return false

        _uiState.update { state ->
            val nextPolys = threatPolys + state.geoJsonThreatPolygons
            ThreatProximityBus.updatePolygons(nextPolys)
            state.copy(
                geoJsonOverlays = if (overlay != null) listOf(overlay) + state.geoJsonOverlays else state.geoJsonOverlays,
                geoJsonThreatPolygons = nextPolys,
                showMvtGeoJsonOverlay = true,
                showThreatPolygons = true
            )
        }
        evaluateAndTriggerGeoJsonAlert(_uiState.value.userMovement.lat, _uiState.value.userMovement.lng)
        return true
    }

    fun deleteCustomGeoJsonOverlay(layerId: String) {
        _uiState.update { state ->
            val removed = state.geoJsonOverlays.firstOrNull { it.id == layerId }
            val nextPolys = if (removed != null) {
                state.geoJsonThreatPolygons.filterNot { it.sourceFileName == removed.fileName }
            } else {
                state.geoJsonThreatPolygons
            }
            ThreatProximityBus.updatePolygons(nextPolys)
            state.copy(
                geoJsonOverlays = state.geoJsonOverlays.filterNot { it.id == layerId },
                geoJsonThreatPolygons = nextPolys
            )
        }
        evaluateAndTriggerGeoJsonAlert(_uiState.value.userMovement.lat, _uiState.value.userMovement.lng)
    }

    fun startOrientationTracking(context: Context) {
        val appCtx = context.applicationContext
        appContextRef = appCtx
        if (orientationStarted) return
        orientationStarted = true
        if (orientationHelper == null) {
            orientationHelper = DeviceOrientationHelper(appCtx)
        }
        viewModelScope.launch {
            orientationHelper?.observeHeading()?.collect { reading ->
                _uiState.update { it.copy(compassReading = reading) }
            }
        }
    }

    fun startThreatProximitySentinel(context: Context) {
        val appCtx = context.applicationContext
        appContextRef = appCtx
        if (!busObserverStarted) {
            busObserverStarted = true
            viewModelScope.launch {
                ThreatProximityBus.activeGeofenceAlert.collect { alert ->
                    _uiState.update { it.copy(geoJsonIntrusionAlert = alert) }
                }
            }
            viewModelScope.launch {
                ThreatProximityBus.isSentinelRunning.collect { running ->
                    _uiState.update { it.copy(isBackgroundSentinelRunning = running) }
                }
            }
        }
        if (_uiState.value.geoJsonThreatPolygons.isNotEmpty()) {
            ThreatProximityBus.updatePolygons(_uiState.value.geoJsonThreatPolygons)
        }
        if (_uiState.value.isBackgroundSentinelArmed) {
            ThreatProximityService.start(appCtx)
        }
    }

    fun toggleBackgroundSentinel(context: Context) {
        val appCtx = context.applicationContext
        appContextRef = appCtx
        val nextArmed = !_uiState.value.isBackgroundSentinelArmed
        _uiState.update { it.copy(isBackgroundSentinelArmed = nextArmed) }
        if (nextArmed) {
            ThreatProximityService.start(appCtx)
            evaluateAndTriggerGeoJsonAlert(_uiState.value.userMovement.lat, _uiState.value.userMovement.lng)
        } else {
            ThreatProximityService.stop(appCtx)
            ThreatProximityBus.dismissVisualAlert()
        }
    }

    private fun evaluateAndTriggerGeoJsonAlert(lat: Double, lng: Double) {
        if (!_uiState.value.isBackgroundSentinelArmed) return
        val enteredNewZone = ThreatProximityBus.evaluateCoordinates(lat, lng)
        val alert = ThreatProximityBus.activeGeofenceAlert.value
        _uiState.update { it.copy(geoJsonIntrusionAlert = alert) }
        if (enteredNewZone && alert != null) {
            appContextRef?.let { ThreatHapticTrigger.triggerThreatEntryHaptic(it) }
        }
    }

    fun startConnectivityAndCacheMonitoring(context: Context) {
        val appCtx = context.applicationContext
        OsmTileDiskCacheManager.configureCachePolicy(appCtx)
        if (!monitoringStarted) {
            monitoringStarted = true
            viewModelScope.launch {
                OsmTileDiskCacheManager.observeNetworkConnectivity(appCtx).collect { online ->
                    _uiState.update { it.copy(isNetworkOnline = online) }
                }
            }
        }
        refreshTileCacheStats(appCtx)
    }

    fun refreshTileCacheStats(context: Context) {
        val appCtx = context.applicationContext
        viewModelScope.launch {
            val stats = OsmTileDiskCacheManager.inspectCache(appCtx)
            val backups = OfflineMapBackupExporter.listLocalBackups(appCtx)
            _uiState.update { it.copy(tileCacheStats = stats, savedLocalBackups = backups) }
        }
    }

    private fun buildCurrentBackupSnapshot(): TacticalBackupSnapshot {
        val state = _uiState.value
        val bounds = currentMapBoundingBox ?: BoundingBox(
            state.userMovement.lat + 0.35,
            state.userMovement.lng + 0.35,
            state.userMovement.lat - 0.35,
            state.userMovement.lng - 0.35
        )
        return TacticalBackupSnapshot(
            bounds = bounds,
            userLat = state.userMovement.lat,
            userLng = state.userMovement.lng,
            mapMode = state.mapMode,
            freeMapProvider = state.freeMapProvider,
            threatZones = state.threatZones,
            heatmapPoints = state.heatmapPoints,
            safeHavens = state.safeHavens,
            cameras = state.cameras,
            geoJsonThreatPolygons = state.geoJsonThreatPolygons,
            geoJsonOverlays = state.geoJsonOverlays,
            activeRoute = state.activeRoute,
            breadcrumbs = state.breadcrumbs,
            sosBroadcasts = state.sosBroadcasts,
            liveIncidents = state.liveIncidents,
            intelSources = state.intelSources
        )
    }

    fun exportOfflineBackupToLocalFile(context: Context) {
        val appCtx = context.applicationContext
        val snapshot = buildCurrentBackupSnapshot()
        _uiState.update {
            it.copy(
                backupExportProgress = BackupExportProgress(
                    isExporting = true,
                    statusMessage = "PACKAGING CACHED TILES & OVERLAYS..."
                )
            )
        }

        viewModelScope.launch {
            try {
                val summary = OfflineMapBackupExporter.exportToAppBackupFile(appCtx, snapshot)
                val updatedBackups = OfflineMapBackupExporter.listLocalBackups(appCtx)
                _uiState.update {
                    it.copy(
                        savedLocalBackups = updatedBackups,
                        backupExportProgress = BackupExportProgress(
                            isExporting = false,
                            statusMessage = "SAVED ${summary.archiveName} (${summary.tilesExportedCount} TILES + ${summary.geoJsonPolygonsCount} POLYS + ${summary.mvtOverlaysCount} MVT)",
                            lastSummary = summary
                        )
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        backupExportProgress = BackupExportProgress(
                            isExporting = false,
                            statusMessage = "BACKUP EXPORT FAILED",
                            errorMessage = e.localizedMessage ?: "I/O error writing backup archive"
                        )
                    )
                }
            }
        }
    }

    fun exportOfflineBackupToUri(context: Context, uri: Uri, archiveName: String) {
        val appCtx = context.applicationContext
        val snapshot = buildCurrentBackupSnapshot()
        _uiState.update {
            it.copy(
                backupExportProgress = BackupExportProgress(
                    isExporting = true,
                    statusMessage = "WRITING OFFLINE BACKUP ARCHIVE..."
                )
            )
        }

        viewModelScope.launch {
            try {
                val summary = OfflineMapBackupExporter.exportToDocumentUri(
                    context = appCtx,
                    uri = uri,
                    snapshot = snapshot,
                    archiveName = archiveName
                )
                _uiState.update {
                    it.copy(
                        backupExportProgress = BackupExportProgress(
                            isExporting = false,
                            statusMessage = "EXPORTED ${summary.archiveName} (${summary.tilesExportedCount} TILES + ${summary.geoJsonPolygonsCount} POLYS + ${summary.mvtOverlaysCount} MVT)",
                            lastSummary = summary
                        )
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        backupExportProgress = BackupExportProgress(
                            isExporting = false,
                            statusMessage = "EXPORT TO DEVICE FAILED",
                            errorMessage = e.localizedMessage ?: "Unable to write selected file"
                        )
                    )
                }
            }
        }
    }

    fun deleteLocalBackup(context: Context, filePath: String) {
        val appCtx = context.applicationContext
        viewModelScope.launch {
            OfflineMapBackupExporter.deleteLocalBackup(filePath)
            val updatedBackups = OfflineMapBackupExporter.listLocalBackups(appCtx)
            _uiState.update { it.copy(savedLocalBackups = updatedBackups) }
        }
    }

    fun prefetchCurrentSectorToDisk(context: Context) {
        val appCtx = context.applicationContext
        val state = _uiState.value
        val activeOnlineProvider = when {
            state.freeMapProvider == FreeMapProvider.BUILT_IN_OFFLINE -> return
            state.mapMode == MapVisualMode.SATELLITE_RECON -> FreeMapProvider.ESRI_SATELLITE
            else -> state.freeMapProvider
        }
        val tileSource = OsmDroidConfig.tileSourceFor(activeOnlineProvider) as? OnlineTileSourceBase ?: return
        val bounds = currentMapBoundingBox ?: BoundingBox(
            state.userMovement.lat + 0.35,
            state.userMovement.lng + 0.35,
            state.userMovement.lat - 0.35,
            state.userMovement.lng - 0.35
        )

        viewModelScope.launch {
            val finalResult = OsmTileDiskCacheManager.prefetchRegionToDisk(
                context = appCtx,
                tileSource = tileSource,
                bounds = bounds,
                minZoom = 6,
                maxZoom = 12,
                onProgress = { progress ->
                    _uiState.update { it.copy(tilePrefetchProgress = progress) }
                }
            )
            val updatedStats = OsmTileDiskCacheManager.inspectCache(appCtx)
            _uiState.update {
                it.copy(
                    tilePrefetchProgress = finalResult,
                    tileCacheStats = updatedStats
                )
            }
        }
    }

    fun clearOsmTileCache(context: Context) {
        val appCtx = context.applicationContext
        viewModelScope.launch {
            OsmTileDiskCacheManager.clearDiskCache(appCtx)
            val stats = OsmTileDiskCacheManager.inspectCache(appCtx)
            _uiState.update {
                it.copy(
                    tileCacheStats = stats,
                    tilePrefetchProgress = TilePrefetchProgress(statusMessage = "CACHE PURGED")
                )
            }
        }
    }

    fun planBypassForGeoJsonPolygon(polygon: GeoJsonThreatPolygon) {
        planBypassForZone(polygon.toThreatZone())
        _uiState.update { it.copy(selectedThreatPolygon = null) }
    }

    fun setOriginCity(city: String) {
        _uiState.update { state ->
            val coords = if (city.equals("Live GPS", ignoreCase = true) && latestRealGpsMovement != null) {
                Pair(latestRealGpsMovement!!.lat, latestRealGpsMovement!!.lng)
            } else {
                val cityCoords = TacticalDataProvider.CITIES.firstOrNull { c -> c.name.equals(city, ignoreCase = true) }
                if (cityCoords != null) Pair(cityCoords.lat, cityCoords.lng) else state.userCoordinates
            }
            val movement = state.userMovement.copy(lat = coords.first, lng = coords.second)
            val newRoute = TacticalPathfinder.findSafestWalkingRoute(
                startLat = coords.first,
                startLng = coords.second,
                safeHavens = state.safeHavens,
                heatmapPoints = state.heatmapPoints,
                cameras = state.cameras,
                targetSafeHavenId = state.destinationHavenId,
                avoidCctv = state.avoidCctvCameras,
                threatWeight = state.standoffBufferKm
            )
            val exposure = TacticalPathfinder.evaluateCctvExposure(coords.first, coords.second, state.cameras)
            state.copy(
                originCity = city,
                userCoordinates = coords,
                userMovement = movement,
                activeRoute = newRoute,
                surveillanceExposure = exposure
            )
        }
        val updatedMovement = _uiState.value.userMovement
        maybeDropBreadcrumb(
            lat = updatedMovement.lat,
            lng = updatedMovement.lng,
            speedKmh = updatedMovement.speedKmh,
            bearingDegrees = updatedMovement.bearingDegrees
        )
        evaluateAndTriggerGeoJsonAlert(
            updatedMovement.lat,
            updatedMovement.lng
        )
    }

    fun setDestinationHaven(havenId: String?) {
        _uiState.update { state ->
            val newRoute = TacticalPathfinder.findSafestWalkingRoute(
                startLat = state.userMovement.lat,
                startLng = state.userMovement.lng,
                safeHavens = state.safeHavens,
                heatmapPoints = state.heatmapPoints,
                cameras = state.cameras,
                targetSafeHavenId = havenId,
                avoidCctv = state.avoidCctvCameras,
                threatWeight = state.standoffBufferKm
            )
            state.copy(destinationHavenId = havenId, activeRoute = newRoute)
        }
    }

    fun setStandoffBuffer(weight: Double) {
        _uiState.update { state ->
            val newRoute = TacticalPathfinder.findSafestWalkingRoute(
                startLat = state.userMovement.lat,
                startLng = state.userMovement.lng,
                safeHavens = state.safeHavens,
                heatmapPoints = state.heatmapPoints,
                cameras = state.cameras,
                targetSafeHavenId = state.destinationHavenId,
                avoidCctv = state.avoidCctvCameras,
                threatWeight = weight
            )
            state.copy(standoffBufferKm = weight, activeRoute = newRoute)
        }
    }

    fun toggleAvoidCctv() {
        _uiState.update { state ->
            val nextAvoid = !state.avoidCctvCameras
            val newRoute = TacticalPathfinder.findSafestWalkingRoute(
                startLat = state.userMovement.lat,
                startLng = state.userMovement.lng,
                safeHavens = state.safeHavens,
                heatmapPoints = state.heatmapPoints,
                cameras = state.cameras,
                targetSafeHavenId = state.destinationHavenId,
                avoidCctv = nextAvoid,
                threatWeight = state.standoffBufferKm
            )
            state.copy(avoidCctvCameras = nextAvoid, activeRoute = newRoute)
        }
    }

    fun calculateNearestSafeWalk() {
        _uiState.update { state ->
            val newRoute = TacticalPathfinder.findSafestWalkingRoute(
                startLat = state.userMovement.lat,
                startLng = state.userMovement.lng,
                safeHavens = state.safeHavens,
                heatmapPoints = state.heatmapPoints,
                cameras = state.cameras,
                targetSafeHavenId = null,
                avoidCctv = state.avoidCctvCameras,
                threatWeight = state.standoffBufferKm
            )
            state.copy(
                destinationHavenId = null,
                activeRoute = newRoute,
                activeTab = MainNavTab.MAP,
                isPathfinderExpanded = false
            )
        }
    }

    fun planBypassForZone(zone: ThreatZone) {
        _uiState.update { state ->
            val boostedWeight = (state.standoffBufferKm + 10.0).coerceIn(15.0, 50.0)
            val newRoute = TacticalPathfinder.findSafestWalkingRoute(
                startLat = state.userMovement.lat,
                startLng = state.userMovement.lng,
                safeHavens = state.safeHavens,
                heatmapPoints = state.heatmapPoints,
                cameras = state.cameras,
                targetSafeHavenId = state.destinationHavenId,
                avoidCctv = state.avoidCctvCameras,
                threatWeight = boostedWeight
            )
            state.copy(
                standoffBufferKm = boostedWeight,
                activeRoute = newRoute,
                selectedZone = null,
                isPathfinderExpanded = true
            )
        }
    }

    fun setDestinationAndCalculate(haven: SafeHaven) {
        _uiState.update { state ->
            val newRoute = TacticalPathfinder.findSafestWalkingRoute(
                startLat = state.userMovement.lat,
                startLng = state.userMovement.lng,
                safeHavens = state.safeHavens,
                heatmapPoints = state.heatmapPoints,
                cameras = state.cameras,
                targetSafeHavenId = haven.id,
                avoidCctv = state.avoidCctvCameras,
                threatWeight = state.standoffBufferKm
            )
            state.copy(
                destinationHavenId = haven.id,
                activeRoute = newRoute,
                selectedHaven = null,
                isPathfinderExpanded = true
            )
        }
    }

    fun toggleNavigationTracking() {
        _uiState.update {
            val nextState = !it.isNavigationTrackingActive
            it.copy(isNavigationTrackingActive = nextState, isPathfinderExpanded = false)
        }
    }

    fun togglePathfinderSheet() {
        _uiState.update { it.copy(isPathfinderExpanded = !it.isPathfinderExpanded) }
    }

    fun toggleEmergencyBeacon() {
        _uiState.update { it.copy(isEmergencyBeaconActive = !it.isEmergencyBeaconActive) }
    }

    fun focusIncident(incident: LiveIncident) {
        _uiState.update {
            it.copy(
                activeTab = MainNavTab.MAP,
                userCoordinates = Pair(incident.lat, incident.lng)
            )
        }
    }

    // --- Google Play Services Location Integration ---
    fun startGpsTracking(context: Context) {
        if (locationHelper == null) {
            locationHelper = LocationHelper(context.applicationContext)
        }

        viewModelScope.launch {
            val last = locationHelper?.getLastKnownLocation()
            if (last != null) {
                latestRealGpsMovement = last
                if (_uiState.value.originCity == "Live GPS") {
                    updateLocation(last)
                }
            }

            locationHelper?.trackUserMovement(intervalMs = 3000L)?.collect { movement ->
                latestRealGpsMovement = movement
                if (_uiState.value.originCity == "Live GPS" || _uiState.value.followAgentOnMap) {
                    updateLocation(movement)
                }
            }
        }
    }

    private fun updateLocation(movement: UserMovementState) {
        _uiState.update { state ->
            var warning: String? = null
            for (zone in state.threatZones) {
                val dist = TacticalDataProvider.calculateDistanceKm(
                    movement.lat, movement.lng,
                    zone.centerLat, zone.centerLng
                )
                if (dist <= zone.radiusKm) {
                    warning = "CRITICAL PROXIMITY: INSIDE ${zone.sectorCode} (${zone.name})"
                    break
                } else if (dist <= zone.radiusKm + 15.0) {
                    warning = "WARNING: APPROACHING ${zone.name} (${dist.toInt()} KM TO EXCLUSION PERIMETER)"
                    break
                }
            }

            val exposure = TacticalPathfinder.evaluateCctvExposure(movement.lat, movement.lng, state.cameras)
            val newRoute = TacticalPathfinder.findSafestWalkingRoute(
                startLat = movement.lat,
                startLng = movement.lng,
                safeHavens = state.safeHavens,
                heatmapPoints = state.heatmapPoints,
                cameras = state.cameras,
                targetSafeHavenId = state.destinationHavenId,
                avoidCctv = state.avoidCctvCameras,
                threatWeight = state.standoffBufferKm
            )

            val coords = Pair(movement.lat, movement.lng)
            state.copy(
                userMovement = movement,
                userCoordinates = coords,
                threatWarning = warning,
                surveillanceExposure = exposure,
                activeRoute = newRoute
            )
        }
        maybeDropBreadcrumb(
            lat = movement.lat,
            lng = movement.lng,
            speedKmh = movement.speedKmh,
            bearingDegrees = movement.bearingDegrees
        )
        evaluateAndTriggerGeoJsonAlert(movement.lat, movement.lng)
    }

    // --- OSINT, Fediverse, RSS & Homemade SDR / Radio Source Management ---
    fun toggleIntelSource(sourceId: String) {
        _uiState.update { state ->
            val updated = state.intelSources.map {
                if (it.id == sourceId) it.copy(isEnabled = !it.isEnabled) else it
            }
            state.copy(intelSources = updated)
        }
    }

    fun addCustomSource(
        name: String,
        type: IntelSourceType,
        endpoint: String,
        frequencyMhz: Double?,
        description: String
    ) {
        val newSource = IntelSource(
            id = "custom_${System.currentTimeMillis()}",
            name = name,
            type = type,
            endpointUrl = endpoint,
            frequencyMhz = frequencyMhz,
            isEnabled = true,
            status = if (frequencyMhz != null) "TUNED // ${frequencyMhz} MHz" else "CONNECTED (USER STREAM)",
            signalStrengthDbm = -44,
            itemsDetected = 1,
            description = description,
            isCustom = true
        )

        _uiState.update { state ->
            val newIncident = LiveIncident(
                id = "inc_custom_${System.currentTimeMillis()}",
                minutesAgo = 1,
                sector = if (frequencyMhz != null) "RF SPECTRUM // ${frequencyMhz} MHz" else "OSINT // ${type.badge}",
                title = "New Stream Linked: $name",
                description = "Custom tactical source connected to ISAC telemetry: $endpoint. Monitoring active.",
                severity = ThreatLevel.CAUTION_DELTA,
                sourceAgency = type.displayName.uppercase(),
                lat = state.userCoordinates.first,
                lng = state.userCoordinates.second
            )

            state.copy(
                intelSources = state.intelSources + newSource,
                liveIncidents = listOf(newIncident) + state.liveIncidents
            )
        }
    }

    fun deleteCustomSource(sourceId: String) {
        _uiState.update { state ->
            state.copy(intelSources = state.intelSources.filterNot { it.id == sourceId })
        }
    }
}
