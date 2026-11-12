package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.TacticalDataProvider
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
    SURVIVAL("PROTOCOLS", "SURV-04")
}

data class TacticalUiState(
    val activeTab: MainNavTab = MainNavTab.MAP,
    val threatZones: List<ThreatZone> = TacticalDataProvider.THREAT_ZONES,
    val safeHavens: List<SafeHaven> = TacticalDataProvider.SAFE_HAVENS,
    val liveIncidents: List<LiveIncident> = TacticalDataProvider.LIVE_INCIDENTS,
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
    val isPathfinderExpanded: Boolean = false
)

class TacticalViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(TacticalUiState())
    val uiState: StateFlow<TacticalUiState> = _uiState.asStateFlow()

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
            it.copy(originCity = city, activeRoute = newRoute, userCoordinates = coords)
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
}
