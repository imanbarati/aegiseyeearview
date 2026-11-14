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

data class ThreatHeatmapPoint(
    val id: String,
    val lat: Double,
    val lng: Double,
    val intensity: Float, // 0.0f (minimal) to 1.0f (lethal peak)
    val radiusKm: Double, // Gaussian influence radius
    val category: ThreatType,
    val label: String
)

enum class SafeHavenType(val title: String) {
    URBAN_METRO_BUNKER("Designated Safe Zone / Subsurface Shelter"),
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
    val borderStatus: String,
    val clearanceLevel: String,
    val supplies: List<String>,
    val contactFreq: String,
    val advisories: String,
    val isDesignatedWalkingSafeZone: Boolean = false
)

enum class CameraOperatorType(val displayName: String, val shortTag: String) {
    PUBLIC_MUNICIPAL_OSINT("Public Municipal / Traffic Feed", "PUBLIC"),
    STATE_SECURITY_ANPR("State Security / ANPR Checkpoint", "STATE-ANPR"),
    TRANSIT_HUB_FACIAL("State Transit / Biometric Dome", "STATE-BIO"),
    CRITICAL_INFRASTRUCTURE_PTZ("Military / Facility Perimeter PTZ", "STATE-PTZ")
}

enum class CameraVulnerabilityStatus(
    val label: String,
    val securityAssessment: String
) {
    VULNERABLE_PUBLIC_OSINT(
        label = "VULNERABLE (PUBLIC OSINT)",
        securityAssessment = "Unencrypted public IP/RTSP stream indexed by OSINT scanners. Viewable publicly; movement in FOV is exposed."
    ),
    HARDENED_STATE_ENCRYPTED(
        label = "INVULNERABLE FEED (STATE ENCRYPTED)",
        securityAssessment = "Closed-circuit state fiber VPN. Invulnerable to external OSINT access; actively tracks targets in FOV."
    ),
    OFFLINE_JAMMED(
        label = "OFFLINE / BLIND SPOT",
        securityAssessment = "Power or uplink severed due to EW jamming or damage. Confirmed optical blind spot."
    )
}

data class SurveillanceCamera(
    val id: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    val operatorType: CameraOperatorType,
    val vulnerabilityStatus: CameraVulnerabilityStatus,
    val coverageRadiusMeters: Double,
    val azimuthDegrees: Float,
    val fovAngleDegrees: Float,
    val streamProtocol: String,
    val osintNote: String
)

data class UserSurveillanceExposure(
    val isExposed: Boolean,
    val statusBadge: String,
    val statusSummary: String,
    val camerasInFov: List<SurveillanceCamera>,
    val vulnerablePublicNearbyCount: Int,
    val stateEncryptedNearbyCount: Int,
    val offlineBlindSpotsNearbyCount: Int,
    val nearestCamera: SurveillanceCamera?,
    val nearestCameraDistanceMeters: Double
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
    val riskClassification: String,
    val deadZonesAvoided: List<String>,
    val tacticalDirectives: List<String>,
    val isWalkingRoute: Boolean = false,
    val estWalkMinutes: Int = 0,
    val peakHeatmapIntensity: Float = 0f,
    val averageHeatmapIntensity: Float = 0f,
    val cctvCamerasAvoided: Int = 0,
    val targetSafeHavenId: String = ""
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
    val lng: Double,
    val sourceUrl: String = "",
    val publishedAtMs: Long = System.currentTimeMillis(),
    val credibilityScore: Int = 70,
    val verificationStatus: String = "FETCHED",
    val corroboratedByCount: Int = 1,
    val geoPrecision: String = "SOURCE_COORDS",
    val disinformationRiskNote: String? = null
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
    TACTICAL_HUD("TACTICAL HUD", "CartoDB Dark + ISAC Vector Grid"),
    SATELLITE_RECON("SATELLITE RECON", "Esri Orbital Imagery (Free)"),
    FLIR_THERMAL("FLIR THERMAL", "Infrared Threat Signatures")
}

enum class IntelSourceType(val displayName: String, val badge: String, val defaultIcon: String) {
    X_TWITTER("X / Twitter OSINT", "OSINT-X", "tag"),
    FEDIVERSE_MASTODON("Fediverse / Mastodon", "FEDIVERSE", "share"),
    RSS_FEED("RSS / Atom Feed", "RSS-WEB", "rss_feed"),
    OPEN_API_JSON("Open Geo / OSINT API (USGS/GDELT/OpenSky)", "API-JSON", "public"),
    DEEP_WEB_ONION("Deep Web / Tor Onion (.onion / Ahmia)", "TOR-DEEP", "security"),
    HOMEMADE_SDR("Homemade SDR (RTL-SDR / WebSDR)", "SDR-TCP", "settings_input_antenna"),
    RADIO_RADAR("Radio Radar (ADS-B / RF Spectrum)", "RF-RADAR", "radar")
}

data class IntelSource(
    val id: String,
    val name: String,
    val type: IntelSourceType,
    val endpointUrl: String,
    val frequencyMhz: Double? = null,
    val isEnabled: Boolean = true,
    val status: String = "IDLE // READY TO FETCH",
    val signalStrengthDbm: Int = 0,
    val itemsDetected: Int = 0,
    val description: String = "",
    val isCustom: Boolean = false,
    val lastFetchedMs: Long? = null,
    val lastError: String? = null,
    val requiresExternalBridge: Boolean = false
)
