package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.TacticalDataProvider
import com.example.location.LocationHelper
import com.example.location.UserMovementState
import com.example.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class MainNavTab(val title: String, val code: String) {
    MAP("GOD'S EYE", "MAP-01"),
    PATHFINDER("EVAC ROUTE", "NAV-02"),
    SITREP("SITREP", "INTEL-03"),
    SOURCES("INTEL & SDR", "SDR-04"),
    SURVIVAL("PROTOCOLS", "SURV-05")
}

data class TacticalUiState(
    val activeTab: MainNavTab = MainNavTab.MAP,
    val threatZones: List<ThreatZone> = TacticalDataProvider.THREAT_ZONES,
    val safeHavens: List<SafeHaven> = TacticalDataProvider.SAFE_HAVENS,
    val liveIncidents: List<LiveIncident> = TacticalDataProvider.LIVE_INCIDENTS,
    val intelSources: List<IntelSource> = TacticalDataProvider.DEFAULT_INTEL_SOURCES,
    val originCity: String = "Tehran",
    val destinationHavenId: String = "haven_kapikoy",
    val standoffBufferKm: Double = 30.0,
    val activeRoute: TacticalRoute = TacticalDataProvider.calculateEvacuationRoute("Tehran", "haven_kapikoy", 30.0),
    val selectedZone: ThreatZone? = null,
    val selectedHaven: SafeHaven? = null,
    val mapMode: MapVisualMode = MapVisualMode.TACTICAL_HUD,
    val showDeadZones: Boolean = true,
    val showSafeHavens: Boolean = true,
    val showRadarSweep: Boolean = true,
    val isNavigationTrackingActive: Boolean = false,
    val isEmergencyBeaconActive: Boolean = false,
    val userCoordinates: Pair<Double, Double> = Pair(35.6892, 51.3890), // Tehran
    val userMovement: UserMovementState = UserMovementState(35.6892, 51.3890),
    val followAgentOnMap: Boolean = false,
    val threatWarning: String? = null,
    val isPathfinderExpanded: Boolean = false
)

class TacticalViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(TacticalUiState())
    val uiState: StateFlow<TacticalUiState> = _uiState.asStateFlow()

    private var locationHelper: LocationHelper? = null

    fun setTab(tab: MainNavTab) {
        _uiState.update { it.copy(activeTab = tab) }
    }

    fun setMapMode(mode: MapVisualMode) {
        _uiState.update { it.copy(mapMode = mode) }
    }

    fun toggleDeadZones() {
        _uiState.update { it.copy(showDeadZones = !it.showDeadZones) }
    }

    fun toggleSafeHavens() {
        _uiState.update { it.copy(showSafeHavens = !it.showSafeHavens) }
    }

    fun toggleRadarSweep() {
        _uiState.update { it.copy(showRadarSweep = !it.showRadarSweep) }
    }

    fun toggleFollowAgent() {
        _uiState.update { it.copy(followAgentOnMap = !it.followAgentOnMap) }
    }

    fun selectZone(zone: ThreatZone?) {
        _uiState.update { it.copy(selectedZone = zone, selectedHaven = null) }
    }

    fun selectHaven(haven: SafeHaven?) {
        _uiState.update { it.copy(selectedHaven = haven, selectedZone = null) }
    }

    fun setOriginCity(city: String) {
        _uiState.update {
            val newRoute = TacticalDataProvider.calculateEvacuationRoute(
                originCityName = city,
                destinationHavenId = it.destinationHavenId,
                standoffBufferKm = it.standoffBufferKm
            )
            val cityCoords = TacticalDataProvider.CITIES.firstOrNull { c -> c.name.equals(city, ignoreCase = true) }
            val coords = if (cityCoords != null) Pair(cityCoords.lat, cityCoords.lng) else it.userCoordinates
            val movement = it.userMovement.copy(lat = coords.first, lng = coords.second)
            it.copy(originCity = city, activeRoute = newRoute, userCoordinates = coords, userMovement = movement)
        }
    }

    fun setDestinationHaven(havenId: String) {
        _uiState.update {
            val newRoute = TacticalDataProvider.calculateEvacuationRoute(
                originCityName = it.originCity,
                destinationHavenId = havenId,
                standoffBufferKm = it.standoffBufferKm
            )
            it.copy(destinationHavenId = havenId, activeRoute = newRoute)
        }
    }

    fun setStandoffBuffer(bufferKm: Double) {
        _uiState.update {
            val newRoute = TacticalDataProvider.calculateEvacuationRoute(
                originCityName = it.originCity,
                destinationHavenId = it.destinationHavenId,
                standoffBufferKm = bufferKm
            )
            it.copy(standoffBufferKm = bufferKm, activeRoute = newRoute)
        }
    }

    fun planBypassForZone(zone: ThreatZone) {
        _uiState.update {
            val newBuffer = (zone.bypassBufferKm + 15.0).coerceIn(20.0, 60.0)
            val newRoute = TacticalDataProvider.calculateEvacuationRoute(
                originCityName = it.originCity,
                destinationHavenId = it.destinationHavenId,
                standoffBufferKm = newBuffer
            )
            it.copy(
                standoffBufferKm = newBuffer,
                activeRoute = newRoute,
                selectedZone = null,
                isPathfinderExpanded = true
            )
        }
    }

    fun setDestinationAndCalculate(haven: SafeHaven) {
        _uiState.update {
            val newRoute = TacticalDataProvider.calculateEvacuationRoute(
                originCityName = it.originCity,
                destinationHavenId = haven.id,
                standoffBufferKm = it.standoffBufferKm
            )
            it.copy(
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
                updateLocation(last)
            }

            locationHelper?.trackUserMovement(intervalMs = 3000L)?.collect { movement ->
                updateLocation(movement)
            }
        }
    }

    private fun updateLocation(movement: UserMovementState) {
        _uiState.update { state ->
            // Check proximity to any active threat zone
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

            val coords = Pair(movement.lat, movement.lng)
            state.copy(
                userMovement = movement,
                userCoordinates = coords,
                threatWarning = warning
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
            // Also generate a live incident from this newly linked custom source!
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
