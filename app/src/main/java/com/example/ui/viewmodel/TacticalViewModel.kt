package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.TacticalDataProvider
import com.example.location.LocationHelper
import com.example.location.UserMovementState
import com.example.model.*
import com.example.routing.TacticalPathfinder
import com.example.ui.map.FreeMapProvider
import com.example.ui.map.mvt.GeoJsonTacticalLoader
import com.example.ui.map.mvt.MvtFeatureSelection
import com.example.ui.map.mvt.TacticalGeoJsonOverlay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    val geoJsonOverlays: List<TacticalGeoJsonOverlay> = emptyList(),
    val selectedMvtFeature: MvtFeatureSelection? = null,
    val isGeoJsonSheetOpen: Boolean = false,
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
    val threatWarning: String? = null,
    val isPathfinderExpanded: Boolean = false
)

class TacticalViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(TacticalUiState())
    val uiState: StateFlow<TacticalUiState> = _uiState.asStateFlow()

    private var locationHelper: LocationHelper? = null
    private var latestRealGpsMovement: UserMovementState? = null

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

    fun toggleMvtGeoJsonOverlay() {
        _uiState.update { it.copy(showMvtGeoJsonOverlay = !it.showMvtGeoJsonOverlay) }
    }

    fun selectZone(zone: ThreatZone?) {
        _uiState.update { it.copy(selectedZone = zone, selectedHaven = null, selectedCamera = null, selectedMvtFeature = null) }
    }

    fun selectHaven(haven: SafeHaven?) {
        _uiState.update { it.copy(selectedHaven = haven, selectedZone = null, selectedCamera = null, selectedMvtFeature = null) }
    }

    fun selectCamera(camera: SurveillanceCamera?) {
        _uiState.update {
            it.copy(
                selectedCamera = camera,
                selectedZone = null,
                selectedHaven = null,
                selectedMvtFeature = null,
                isCctvScannerOpen = false
            )
        }
    }

    fun selectMvtFeature(selection: MvtFeatureSelection?) {
        _uiState.update {
            it.copy(
                selectedMvtFeature = selection,
                selectedZone = null,
                selectedHaven = null,
                selectedCamera = null,
                isCctvScannerOpen = false,
                isGeoJsonSheetOpen = false
            )
        }
    }

    fun toggleCctvScanner(open: Boolean? = null) {
        _uiState.update {
            it.copy(
                isCctvScannerOpen = open ?: !it.isCctvScannerOpen,
                isGeoJsonSheetOpen = false,
                selectedZone = null,
                selectedHaven = null,
                selectedCamera = null,
                selectedMvtFeature = null
            )
        }
    }

    fun toggleGeoJsonSheet(open: Boolean? = null) {
        _uiState.update {
            it.copy(
                isGeoJsonSheetOpen = open ?: !it.isGeoJsonSheetOpen,
                isCctvScannerOpen = false,
                selectedZone = null,
                selectedHaven = null,
                selectedCamera = null,
                selectedMvtFeature = null
            )
        }
    }

    fun loadBundledGeoJsonOverlays(context: Context) {
        if (_uiState.value.geoJsonOverlays.isNotEmpty()) return
        val bundled = GeoJsonTacticalLoader.loadBundledOverlays(context.applicationContext)
        if (bundled.isNotEmpty()) {
            _uiState.update { state ->
                val customOnly = state.geoJsonOverlays.filter { it.isCustomImported }
                state.copy(geoJsonOverlays = bundled + customOnly)
            }
        }
    }

    fun toggleGeoJsonLayer(layerId: String) {
        _uiState.update { state ->
            state.copy(
                geoJsonOverlays = state.geoJsonOverlays.map {
                    if (it.id == layerId) it.copy(isEnabled = !it.isEnabled) else it
                }
            )
        }
    }

    fun importCustomGeoJson(fileName: String, rawJson: String): Boolean {
        val id = "custom_geojson_${System.currentTimeMillis()}"
        val overlay = GeoJsonTacticalLoader.parseGeoJsonToMvtOverlay(
            id = id,
            fallbackName = fileName.substringBeforeLast('.'),
            fileName = fileName,
            rawJson = rawJson,
            isCustomImported = true
        ) ?: return false

        _uiState.update { state ->
            state.copy(
                geoJsonOverlays = listOf(overlay) + state.geoJsonOverlays,
                showMvtGeoJsonOverlay = true
            )
        }
        return true
    }

    fun deleteCustomGeoJsonOverlay(layerId: String) {
        _uiState.update { state ->
            state.copy(geoJsonOverlays = state.geoJsonOverlays.filterNot { it.id == layerId })
        }
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
