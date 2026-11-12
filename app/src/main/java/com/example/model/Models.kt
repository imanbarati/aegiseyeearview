package com.example.model

data class GeoPoint(
    val lat: Double,
    val lng: Double,
    val name: String = "",
    val isCheckpoint: Boolean = false
)

enum class ThreatLevel(val label: String, val code: String) {
    CRITICAL_ALPHA("LETHAL / NO-GO", "LVL-1 ALPHA"),
    HIGH_BRAVO("HIGH HAZARD / ACTIVE COMBAT", "LVL-2 BRAVO"),
    MODERATE_CHARLIE("CONTESTED / RESTRICTED", "LVL-3 CHARLIE"),
    CAUTION_DELTA("ELEVATED SURVEILLANCE", "LVL-4 DELTA")
}

enum class ThreatType(val displayName: String, val iconName: String) {
    KINETIC_STRIKE("Kinetic Airstrike Zone", "rocket"),
    AIR_DEFENSE_RADAR("S-300 / Bavar SAM Envelope", "radar"),
    EW_JAMMING("GPS & Comms Jamming / EW", "wifi_off"),
    MILITARY_LOCKDOWN("Martial Law & Armored Checkpoints", "shield"),
    MISSILE_SILO("Ballistic Facility Perimeter", "warning"),
    RADIATION_HAZARD("CBRN / Nuclear Exclusion", "biohazard")
}

data class ThreatZone(
    val id: String,
    val name: String,
    val sectorCode: String,
    val level: ThreatLevel,
    val type: ThreatType,
    val centerLat: Double,
    val centerLng: Double,
    val radiusKm: Double,
    val description: String,
    val tacticalSitRep: String,
    val casualtyRisk: String,
    val bypassBufferKm: Double,
    val lastPingMinutesAgo: Int
)

enum class SafeHavenType(val title: String) {
    BORDER_CROSSING("International Border Gate"),
    MARITIME_PORT("Maritime Sea Corridor"),
    HUMANITARIAN_STATION("UN / Red Crescent Hub"),
    MOUNTAIN_REDOUBT("Off-Grid Mountain Safe Haven")
}

data class SafeHaven(
    val id: String,
    val name: String,
    val countryOrRegion: String,
    val type: SafeHavenType,
    val lat: Double,
    val lng: Double,
    val borderStatus: String, // e.g. "OPEN - HUMANITARIAN PASSAGE", "ESCORT REQUIRED", "SLOW PROCESSING"
    val clearanceLevel: String,
    val supplies: List<String>,
    val contactFreq: String,
    val advisories: String
)

data class TacticalRoute(
    val id: String,
    val name: String,
    val origin: String,
    val destination: String,
    val path: List<GeoPoint>,
    val distanceKm: Double,
    val estDriveHours: Double,
    val riskScore: Int, // 0 - 100
    val riskClassification: String, // "OPTIMAL / LOWEST CASUALTY RISK", "ELEVATED CONFLICT EXPOSURE", "EMERGENCY ONLY"
    val deadZonesAvoided: List<String>,
    val tacticalDirectives: List<String>
)

data class LiveIncident(
    val id: String,
    val minutesAgo: Int,
    val sector: String,
    val title: String,
    val description: String,
    val severity: ThreatLevel,
    val sourceAgency: String,
    val lat: Double,
    val lng: Double
)

data class SurvivalProtocol(
    val id: String,
    val category: String,
    val title: String,
    val code: String,
    val summary: String,
    val directives: List<String>,
    val emergencyFrequency: String
)

enum class MapVisualMode(val displayName: String, val subtitle: String) {
    TACTICAL_HUD("TACTICAL HUD", "ISAC Holographic Wireframe"),
    SATELLITE_RECON("SATELLITE RECON", "High-Contrast Orbital Optics"),
    FLIR_THERMAL("FLIR THERMAL", "Infrared Threat Signatures")
}
