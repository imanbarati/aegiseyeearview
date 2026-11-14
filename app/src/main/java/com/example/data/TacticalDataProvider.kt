package com.example.data

import com.example.model.*
import kotlin.math.*

object TacticalDataProvider {

    // Major Iranian urban hubs & strategic nodes
    val CITIES = listOf(
        GeoPoint(35.6892, 51.3890, "Tehran"),
        GeoPoint(32.6546, 51.6680, "Isfahan"),
        GeoPoint(38.0800, 46.2919, "Tabriz"),
        GeoPoint(29.5918, 52.5837, "Shiraz"),
        GeoPoint(36.2972, 59.6067, "Mashhad"),
        GeoPoint(31.3183, 48.6706, "Ahvaz"),
        GeoPoint(27.1832, 56.2666, "Bandar Abbas"),
        GeoPoint(34.3142, 47.0650, "Kermanshah"),
        GeoPoint(37.2808, 49.5832, "Rasht"),
        GeoPoint(30.2839, 57.0834, "Kerman"),
        GeoPoint(34.6401, 50.8764, "Qom"),
        GeoPoint(33.5119, 51.9167, "Natanz"),
        GeoPoint(28.9234, 50.8203, "Bushehr"),
        GeoPoint(25.2919, 60.6430, "Chabahar"),
        GeoPoint(36.2688, 50.0041, "Qazvin"),
        GeoPoint(34.7989, 48.5150, "Hamedan"),
        GeoPoint(35.5769, 53.3970, "Semnan"),
        GeoPoint(31.8974, 54.3569, "Yazd"),
        GeoPoint(29.4963, 60.8629, "Zahedan"),
        GeoPoint(37.4764, 49.4674, "Bandar Anzali")
    )

    // Iran simplified perimeter polygon for cartographic background rendering
    val IRAN_BORDER_POLYGON = listOf(
        GeoPoint(39.78, 44.75), // NW corner (Ararat / Turkey / Armenia)
        GeoPoint(38.89, 46.20), // Norduz / Armenia
        GeoPoint(38.43, 48.87), // Astara / Caspian Sea SW
        GeoPoint(37.45, 50.00), // Caspian South
        GeoPoint(36.85, 54.00), // Caspian SE / Gorgan
        GeoPoint(37.50, 56.50), // Turkmenistan border W
        GeoPoint(37.80, 58.50), // Ashgabat border
        GeoPoint(36.00, 61.20), // Sarakhs / NE corner
        GeoPoint(34.00, 60.50), // Afghanistan border N
        GeoPoint(31.00, 61.80), // Zabol / Hamun
        GeoPoint(28.00, 62.50), // Pakistan border
        GeoPoint(25.10, 61.50), // Gwadar / Chabahar Coast
        GeoPoint(25.30, 57.80), // Gulf of Oman coast
        GeoPoint(26.50, 56.40), // Strait of Hormuz
        GeoPoint(27.00, 53.50), // Persian Gulf East
        GeoPoint(28.90, 50.80), // Bushehr
        GeoPoint(30.00, 49.50), // Khuzestan coast
        GeoPoint(30.40, 48.00), // Arvand / Shatt al-Arab
        GeoPoint(32.00, 47.50), // Iraq border Dehloran
        GeoPoint(34.00, 45.50), // Kermanshah / Khanaqin
        GeoPoint(36.50, 45.00), // Piranshahr / Kurdistan
        GeoPoint(38.50, 44.30), // Razi / Turkey border
        GeoPoint(39.78, 44.75)  // Close loop
    )

    // Detailed Conflict & Dead Zones (Tom Clancy's The Division style sectors)
    val THREAT_ZONES = listOf(
        ThreatZone(
            id = "zone_natanz",
            name = "Natanz Kinetic Strike & Exclusion Grid",
            sectorCode = "SECTOR 04-ALPHA",
            level = ThreatLevel.CRITICAL_ALPHA,
            type = ThreatType.KINETIC_STRIKE,
            centerLat = 33.5119,
            centerLng = 51.9167,
            radiusKm = 65.0,
            description = "Active subterranean facility perimeter. Repeated bunker-buster missile strikes and anti-radiation munitions reported.",
            tacticalSitRep = "Thermal imaging indicates continuous missile detonations. Air defense Bavar-373 active. High risk of secondary explosions.",
            casualtyRisk = "EXTREME (100% Lethal Transit Risk)",
            bypassBufferKm = 40.0,
            lastPingMinutesAgo = 3
        ),
        ThreatZone(
            id = "zone_isfahan",
            name = "Isfahan Drone & Airbase Engagement Hub",
            sectorCode = "SECTOR 07-ALPHA",
            level = ThreatLevel.CRITICAL_ALPHA,
            type = ThreatType.KINETIC_STRIKE,
            centerLat = 32.6546,
            centerLng = 51.6680,
            radiusKm = 50.0,
            description = "Heavy UAV swarm launch base and tactical aircraft shelters. Intercept sirens and shrapnel dispersion over residential districts.",
            tacticalSitRep = "Multiple drone assembly hangars neutralized. Air raid sirens active. Roadways blockaded by military armored units.",
            casualtyRisk = "CRITICAL (No-Go Corridor)",
            bypassBufferKm = 35.0,
            lastPingMinutesAgo = 8
        ),
        ThreatZone(
            id = "zone_tehran_south",
            name = "Tehran Southern Defense & Radar Envelope",
            sectorCode = "SECTOR 01-BRAVO",
            level = ThreatLevel.HIGH_BRAVO,
            type = ThreatType.AIR_DEFENSE_RADAR,
            centerLat = 35.5200,
            centerLng = 51.3500,
            radiusKm = 45.0,
            description = "Dense layered surface-to-air missile battery. Parchin missile research complex sector subjected to stand-off strikes.",
            tacticalSitRep = "Intense radar tracking lock. Military checkpoints along highway 7 (Tehran-Qom). Nighttime curfew enforced with live fire.",
            casualtyRisk = "SEVERE (Heavy Armored Blockades)",
            bypassBufferKm = 25.0,
            lastPingMinutesAgo = 14
        ),
        ThreatZone(
            id = "zone_hormuz",
            name = "Strait of Hormuz Anti-Ship & EW Corridor",
            sectorCode = "SECTOR 12-BRAVO",
            level = ThreatLevel.HIGH_BRAVO,
            type = ThreatType.EW_JAMMING,
            centerLat = 26.5667,
            centerLng = 56.2500,
            radiusKm = 85.0,
            description = "High-powered electronic warfare jamming, GPS spoofing, and maritime missile battery coverage. Naval interception zone.",
            tacticalSitRep = "Vessels reporting total navigational blackout. GPS signals spoofed by 30+ nautical miles. Unmanned fast attack craft patrols.",
            casualtyRisk = "HIGH (Maritime Mine & Missile Threat)",
            bypassBufferKm = 50.0,
            lastPingMinutesAgo = 19
        ),
        ThreatZone(
            id = "zone_bushehr",
            name = "Bushehr Coastal Air Defense & Reactor Perimeter",
            sectorCode = "SECTOR 09-ALPHA",
            level = ThreatLevel.CRITICAL_ALPHA,
            type = ThreatType.RADIATION_HAZARD,
            centerLat = 28.9234,
            centerLng = 50.8203,
            radiusKm = 55.0,
            description = "Nuclear power facility perimeter under maximum military lockdown. Air defense exclusion zone active 24/7.",
            tacticalSitRep = "CBRN defense alert status Yellow. Coastline mined, road access strictly prohibited to non-military convoys.",
            casualtyRisk = "CRITICAL (Radiation Contamination Risk)",
            bypassBufferKm = 45.0,
            lastPingMinutesAgo = 27
        ),
        ThreatZone(
            id = "zone_ahvaz",
            name = "Khuzestan Oilfield Combat & Artillery Grid",
            sectorCode = "SECTOR 11-BRAVO",
            level = ThreatLevel.HIGH_BRAVO,
            type = ThreatType.MILITARY_LOCKDOWN,
            centerLat = 31.3183,
            centerLng = 48.6706,
            radiusKm = 55.0,
            description = "Strategic refinery complex under artillery exchange and sabotage threats. Heavy armor deployment across Karun River crossings.",
            tacticalSitRep = "Bridges across Karun river mined. Fuel distribution centers burning. Civilian movement halted by military decree.",
            casualtyRisk = "HIGH (Artillery & Civil Unrest)",
            bypassBufferKm = 30.0,
            lastPingMinutesAgo = 33
        ),
        ThreatZone(
            id = "zone_tabriz_ew",
            name = "Tabriz Northwest Electronic Warfare Jammer",
            sectorCode = "SECTOR 03-CHARLIE",
            level = ThreatLevel.MODERATE_CHARLIE,
            type = ThreatType.EW_JAMMING,
            centerLat = 38.0800,
            centerLng = 46.2919,
            radiusKm = 40.0,
            description = "Electronic countermeasure transmitters causing cell tower dropouts and civilian satellite navigation blind spots.",
            tacticalSitRep = "Satellite links intermittently disrupted. Landline phone network severed. Bypass via secondary mountain passes recommended.",
            casualtyRisk = "MODERATE (Communication Blackout)",
            bypassBufferKm = 20.0,
            lastPingMinutesAgo = 41
        ),
        ThreatZone(
            id = "zone_kermanshah",
            name = "Zagros Ballistic Missile Silo Cluster",
            sectorCode = "SECTOR 05-BRAVO",
            level = ThreatLevel.HIGH_BRAVO,
            type = ThreatType.MISSILE_SILO,
            centerLat = 34.3142,
            centerLng = 47.0650,
            radiusKm = 50.0,
            description = "Underground mountain missile silos and launch positions. High probability of counter-battery airstrikes.",
            tacticalSitRep = "Air patrols overhead. Secondary roads heavily cratered by precision strikes. Highway 48 hazardous.",
            casualtyRisk = "HIGH (Silo Counter-Strikes)",
            bypassBufferKm = 35.0,
            lastPingMinutesAgo = 52
        ),
        ThreatZone(
            id = "zone_fordow",
            name = "Fordow Subterranean Bunker Exclusion",
            sectorCode = "SECTOR 02-ALPHA",
            level = ThreatLevel.CRITICAL_ALPHA,
            type = ThreatType.KINETIC_STRIKE,
            centerLat = 34.8833,
            centerLng = 50.9833,
            radiusKm = 40.0,
            description = "Mountain bunker facility outside Qom. Heavily fortified with triple-layer radar and surface-to-air missiles.",
            tacticalSitRep = "Area under complete satellite surveillance. Armed drone overflights every 15 minutes. Transit forbidden.",
            casualtyRisk = "LETHAL (Automated Turret & SAM Zone)",
            bypassBufferKm = 30.0,
            lastPingMinutesAgo = 11
        ),
        ThreatZone(
            id = "zone_mashhad_east",
            name = "Mashhad Eastern Border Surveillance Belt",
            sectorCode = "SECTOR 08-DELTA",
            level = ThreatLevel.CAUTION_DELTA,
            type = ThreatType.MILITARY_LOCKDOWN,
            centerLat = 36.2972,
            centerLng = 59.6067,
            radiusKm = 35.0,
            description = "Armed border guard checkposts and biometric identity verifications. Transport curfews in effect from 20:00 to 06:00.",
            tacticalSitRep = "Commercial movement delayed by 18+ hours. Supply chains strained but minimal kinetic combat.",
            casualtyRisk = "MODERATE (Detention & Interrogation Risk)",
            bypassBufferKm = 15.0,
            lastPingMinutesAgo = 64
        )
    )

    // Key Safe Havens & International Evacuation Terminals
    val SAFE_HAVENS = listOf(
        SafeHaven(
            id = "haven_kapikoy",
            name = "Razi / Kapiköy Border Gate (Turkey)",
            countryOrRegion = "Turkey International Corridor",
            type = SafeHavenType.BORDER_CROSSING,
            lat = 38.4891,
            lng = 44.3312,
            borderStatus = "OPEN - DIPLOMATIC & HUMANITARIAN ESCORT",
            clearanceLevel = "Level 1: Expedited Visa & Red Crescent processing",
            supplies = listOf("Emergency Medical Trauma Unit", "Satellite Phone Link", "Emergency Fuel 50L/car", "Potable Water"),
            contactFreq = "142.800 MHz (UN Evac Net)",
            advisories = "Safest overland corridor to NATO territory. Escorts leave Khoy every 4 hours. Avoid Tabriz urban center by using Marand bypass."
        ),
        SafeHaven(
            id = "haven_norduz",
            name = "Norduz / Meghri Border (Armenia)",
            countryOrRegion = "Armenia Southern Boundary",
            type = SafeHavenType.BORDER_CROSSING,
            lat = 38.8920,
            lng = 46.2080,
            borderStatus = "OPEN - 24/7 HUMANITARIAN CONVOY",
            clearanceLevel = "Level 1: Open entry with international passport",
            supplies = listOf("Field Hospital", "Children's Aid Hub", "Power Recharging", "Warm Clothing"),
            contactFreq = "156.450 MHz (Red Cross Aras)",
            advisories = "Mountain terrain road. Zero EW jamming reported along the Jolfa-Norduz canyon road. High fuel consumption due to steep incline."
        ),
        SafeHaven(
            id = "haven_bazargan",
            name = "Bazargan / Gürbulak Checkpoint",
            countryOrRegion = "Turkey Northern Route",
            type = SafeHavenType.BORDER_CROSSING,
            lat = 39.4000,
            lng = 44.3800,
            borderStatus = "RESTRICTED - 6HR CONVOY PROCESSING",
            clearanceLevel = "Level 2: Strict vehicle search before border crossing",
            supplies = listOf("Refueling Station", "Satellite Internet Terminal", "Food Rations"),
            contactFreq = "149.225 MHz (TAC-3)",
            advisories = "High congestion of commercial freight. Commercial queue exceeds 12km; use humanitarian lane if family or wounded are present."
        ),
        SafeHaven(
            id = "haven_astara",
            name = "Astara Caspian Crossing (Azerbaijan)",
            countryOrRegion = "Azerbaijan Border",
            type = SafeHavenType.BORDER_CROSSING,
            lat = 38.4286,
            lng = 48.8717,
            borderStatus = "OPEN - RESTRICTED FOOT PASSAGE",
            clearanceLevel = "Level 2: Valid visa or UNHCR emergency credential",
            supplies = listOf("Medical Clinic", "Consular Emergency Desk", "Bottled Water"),
            contactFreq = "151.775 MHz (Caspian Guard)",
            advisories = "Coastline road from Rasht is calm. Low probability of kinetic airstrikes along Caspian shoreline."
        ),
        SafeHaven(
            id = "haven_chabahar",
            name = "Chabahar Deep Sea Port Terminal",
            countryOrRegion = "Oman Gulf / International Waters",
            type = SafeHavenType.MARITIME_PORT,
            lat = 25.2919,
            lng = 60.6430,
            borderStatus = "OPEN - MARITIME EVACUATION FERRIES",
            clearanceLevel = "Level 1: International maritime safety convention",
            supplies = listOf("Container Ship Berths", "Naval Security Escort", "Heavy Cargo Relief", "Water Desalination"),
            contactFreq = "Marine VHF Ch 16 (156.800 MHz)",
            advisories = "1,800km from northern strike zones. Completely outside active missile engagement ranges. Sea ferry departures to Muscat and Dubai."
        ),
        SafeHaven(
            id = "haven_dizin",
            name = "Alborz Mountain Redoubt & Alpine Haven",
            countryOrRegion = "Central Alborz Alpine Ridge",
            type = SafeHavenType.MOUNTAIN_REDOUBT,
            lat = 36.0500,
            lng = 51.4167,
            borderStatus = "SECURE - AUTONOMOUS REFUGE",
            clearanceLevel = "Civilian Sanctuary (No military installations)",
            supplies = listOf("Natural Spring Water", "Off-grid Solar Power", "Medical Cache", "Mountain Cabins"),
            contactFreq = "433.500 MHz (Local Mountain Rescue)",
            advisories = "Elevation 3,000m. Completely shielded from radar and missile paths by massive mountain ridges. Snow chains required in winter."
        ),
        SafeHaven(
            id = "haven_semnan",
            name = "Semnan Desert Crossroads Triage Post",
            countryOrRegion = "Kavir Desert Northern Edge",
            type = SafeHavenType.HUMANITARIAN_STATION,
            lat = 35.5769,
            lng = 53.3970,
            borderStatus = "OPERATIONAL - RED CRESCENT HUB",
            clearanceLevel = "Open humanitarian assistance for all evacuees",
            supplies = listOf("Trauma Surgery Tent", "Water Distribution Point", "Fuel Depots", "Emergency Sleeping Cots"),
            contactFreq = "143.900 MHz (Red Crescent East)",
            advisories = "Key waypoint for refugees escaping eastward toward Khorasan and Central Asia."
        ),
        SafeHaven(
            id = "safezone_tehran_tajrish",
            name = "Tajrish Deep Metro Bunker (Safe Zone N-01)",
            countryOrRegion = "Tehran Northern Foothills",
            type = SafeHavenType.URBAN_METRO_BUNKER,
            lat = 35.8058,
            lng = 51.4281,
            borderStatus = "OPEN - SUBSURFACE BLAST SHELTER",
            clearanceLevel = "Civilian Walk-In Sanctuary (45m Underground)",
            supplies = listOf("Blast Doors", "Potable Water Cistern", "Triage Medics", "Emergency Battery Bank"),
            contactFreq = "446.006 MHz (Tehran North Net)",
            advisories = "Deepest northern metro station shielded by Alborz slope. Approach via residential alleys to avoid Valiasr main street cameras.",
            isDesignatedWalkingSafeZone = true
        ),
        SafeHaven(
            id = "safezone_tehran_chitgar",
            name = "Chitgar West Red Crescent Safe Zone",
            countryOrRegion = "Tehran West Perimeter",
            type = SafeHavenType.URBAN_METRO_BUNKER,
            lat = 35.7365,
            lng = 51.2090,
            borderStatus = "OPEN - HUMANITARIAN STAGING ZONE",
            clearanceLevel = "Open Civilian Refuge & Westward Exit Staging",
            supplies = listOf("Red Crescent Tents", "Water Purification", "Evac Bus Dispatch", "First Aid"),
            contactFreq = "143.900 MHz (West Relief)",
            advisories = "Open parkland free of tall glass collapse hazards. Ideal staging ground before heading west toward Karaj/Qazvin.",
            isDesignatedWalkingSafeZone = true
        ),
        SafeHaven(
            id = "safezone_isfahan_soffe",
            name = "Mount Soffeh Civil Defense Shelter",
            countryOrRegion = "Isfahan South Ridge",
            type = SafeHavenType.URBAN_METRO_BUNKER,
            lat = 32.5870,
            lng = 51.6440,
            borderStatus = "OPEN - MOUNTAIN CAVE & BUNKER",
            clearanceLevel = "Civilian Walk-In Refuge",
            supplies = listOf("Rock-Shielded Tunnels", "Drinking Water", "Solar Comms Relay"),
            contactFreq = "156.450 MHz (Isfahan Rescue)",
            advisories = "Natural granite shielding away from northern airbase strike zones.",
            isDesignatedWalkingSafeZone = true
        ),
        SafeHaven(
            id = "safezone_tabriz_elgoli",
            name = "El-Goli Underground Sanctuary",
            countryOrRegion = "Tabriz Southeast",
            type = SafeHavenType.URBAN_METRO_BUNKER,
            lat = 38.0270,
            lng = 46.3640,
            borderStatus = "OPEN - EMERGENCY SAFE ZONE",
            clearanceLevel = "Civilian Walk-In Sanctuary",
            supplies = listOf("Subsurface Vault", "Thermal Blankets", "Water & Rations"),
            contactFreq = "142.800 MHz (NW Relief)",
            advisories = "Outside central Tabriz EW interference core. Safe staging point for Marand bypass.",
            isDesignatedWalkingSafeZone = true
        )
    )

    // Live Tactical Incidents (Simulating real-time ISAC recon feed)
    val LIVE_INCIDENTS = listOf(
        LiveIncident(
            id = "inc_01",
            minutesAgo = 2,
            sector = "SECTOR 04-NATANZ",
            title = "Secondary Strike Detected on Subterranean Bunkers",
            description = "Seismic sensors registered magnitude 2.8 ground vibration. Air defense radar arrays offline. Thick smoke plume drifting south-east.",
            severity = ThreatLevel.CRITICAL_ALPHA,
            sourceAgency = "ORBITAL RECON (ISAC-SAT 4)",
            lat = 33.5119,
            lng = 51.9167
        ),
        LiveIncident(
            id = "inc_02",
            minutesAgo = 7,
            sector = "SECTOR 01-TEHRAN",
            title = "GPS Spoofing Envelope Extended Over Freeway 7",
            description = "Civilian navigation systems experiencing 12km position offset error. Use manual topographic maps and landmark navigation.",
            severity = ThreatLevel.HIGH_BRAVO,
            sourceAgency = "SIGINT TELEMETRY",
            lat = 35.4500,
            lng = 51.2500
        ),
        LiveIncident(
            id = "inc_03",
            minutesAgo = 15,
            sector = "SECTOR 07-ISFAHAN",
            title = "Martial Law Blockades on Isfahan Ring Expressway",
            description = "Armored personnel carriers obstructing traffic at North and East junctions. Evacuation convoys diverted through Najafabad.",
            severity = ThreatLevel.CRITICAL_ALPHA,
            sourceAgency = "CIVIL DEFENSE RECON",
            lat = 32.7000,
            lng = 51.5500
        ),
        LiveIncident(
            id = "inc_04",
            minutesAgo = 28,
            sector = "NW CORRIDOR (KHOY)",
            title = "Razi Border Corridor Escort Convoys Running on Schedule",
            description = "Turkish border authorities confirmed passage of 240 civilian vehicles in the last hour. Fuel cache operational at KM 42.",
            severity = ThreatLevel.CAUTION_DELTA,
            sourceAgency = "UNHCR COURIER NET",
            lat = 38.5500,
            lng = 44.9500
        ),
        LiveIncident(
            id = "inc_05",
            minutesAgo = 44,
            sector = "SECTOR 12-HORMUZ",
            title = "High Velocity Projectile Warning in Coastal Strait",
            description = "Commercial shipping instructed to maintain 40 nautical mile standoff from Bandar Abbas naval docks.",
            severity = ThreatLevel.HIGH_BRAVO,
            sourceAgency = "MARITIME ADVISORY",
            lat = 27.0500,
            lng = 56.3500
        ),
        LiveIncident(
            id = "inc_06",
            minutesAgo = 62,
            sector = "ALBORZ NORTH",
            title = "Caspian Shoreline Road (Highway 59) Cleared of Debris",
            description = "Karaj-Chalus highway pass re-opened for civilian evacuation toward Caspian ports. Weather clear, visibility optimal.",
            severity = ThreatLevel.MODERATE_CHARLIE,
            sourceAgency = "ROAD POLICE HIGHWAY DISPATCH",
            lat = 36.1000,
            lng = 51.3000
        )
    )

    // Emergency Survival Protocols
    val SURVIVAL_PROTOCOLS = listOf(
        SurvivalProtocol(
            id = "prot_airstrike",
            category = "KINETIC ATTACK",
            title = "Airstrike & Missile Impact Immediate Action",
            code = "ISAC-PROT-01",
            summary = "Directives when missile sirens sound or blast flashes are visible.",
            directives = listOf(
                "IMMEDIATE DROP: Fall to ground face-down with hands over neck and head, mouth slightly open to equalize blast wave pressure.",
                "AVOID STRUCTURE GLASS: Stand-off from windows and glass facades by at least 15 meters to survive flying fragmentation.",
                "BASEMENT SHELTER: Sub-surface basements, metro stations, or concrete stairwell cores provide 85%+ blast survivability.",
                "POST-BLAST ESCAPE: Extinguish gas mains, grab 72-hour bug-out pack, and do not use elevators or vehicles immediately."
            ),
            emergencyFrequency = "446.006 MHz (Crisis Net Ch 1)"
        ),
        SurvivalProtocol(
            id = "prot_ew_blackout",
            category = "COMMUNICATION BREAKDOWN",
            title = "Electronic Warfare & Total GPS Jamming Survival",
            code = "ISAC-PROT-02",
            summary = "Navigating and coordinating during complete cellular and GPS blackout.",
            directives = listOf(
                "ANALOG COMPASS & PAPER MAPS: Do not rely on mobile GPS. Orient using physical landmarks (Alborz mountains = North in Tehran).",
                "PRE-ARRANGED RALLY POINTS: Establish family rendezvous points at designated safe coordinates before network collapses.",
                "FM/AM SHORTWAVE RADIO: Keep battery-powered transistor radios tuned to emergency frequencies for official evacuation corridors.",
                "FARADAY PROTECTION: Wrap secondary phones and emergency radios in multiple layers of aluminum foil to shield against EMP bursts."
            ),
            emergencyFrequency = "9.580 MHz (Shortwave SW1)"
        ),
        SurvivalProtocol(
            id = "prot_checkpoint",
            category = "CIVIL DEFENSE",
            title = "Armed Checkpoint & Border Negotiation Protocol",
            code = "ISAC-PROT-03",
            summary = "Protocols for passing military blockades and international border gates safely.",
            directives = listOf(
                "INTERIOR LIGHTS ON: When approaching checkpoints at night, turn on cabin lights and keep both hands visible at top of steering wheel.",
                "NO SUDDEN MOVEMENTS: Do not reach into pockets or glove box without explicitly informing the guard in advance.",
                "DOCUMENTS READY: Keep passports, medical exemptions, and civilian IDs in a transparent waterproof sleeve.",
                "NO RECORDING DEVICES: Power off dashcams, GoPro mounts, and phones at checkpoints to prevent equipment confiscation."
            ),
            emergencyFrequency = "142.800 MHz (Border Escort)"
        ),
        SurvivalProtocol(
            id = "prot_water_medical",
            category = "RESOURCE SURVIVAL",
            title = "Emergency Water, Triage & CBRN Precautions",
            code = "ISAC-PROT-04",
            summary = "Sustaining hydration and first aid during infrastructure collapse.",
            directives = listOf(
                "WATER SANITIZATION: Boil rolling water for 3 minutes or use 4 drops of chlorine bleach per liter. Let settle for 30 minutes.",
                "TRAUMA TRIAGE: Prioritize arterial tourniquets 5-7cm above severe limb bleeding. Note application time on patient's forehead.",
                "CBRN DUST FILTER: Wear P100/N95 masks or wet multi-layered cotton bandanas if industrial or nuclear smoke is reported.",
                "IODINE PROTOCOL: Potassium Iodide (KI) tablets should strictly be taken only upon confirmed radioactive release verification."
            ),
            emergencyFrequency = "156.450 MHz (Medical Channel)"
        )
    )

    // Pre-calculated optimal evasion routes that mathematically bypass all red dead zones!
    fun calculateEvacuationRoute(
        originCityName: String,
        destinationHavenId: String,
        standoffBufferKm: Double = 30.0
    ): TacticalRoute {
        val origin = CITIES.firstOrNull { it.name.equals(originCityName, ignoreCase = true) }
            ?: CITIES.first()
        val haven = SAFE_HAVENS.firstOrNull { it.id == destinationHavenId }
            ?: SAFE_HAVENS.first()

        // Generate high-resolution tactical waypoint path avoiding dead zones
        val waypoints = mutableListOf<GeoPoint>()
        val deadZonesAvoided = mutableListOf<String>()
        val directives = mutableListOf<String>()

        waypoints.add(origin)

        // Custom tactical routing logic based on origin & destination
        when {
            // Tehran -> Turkey (Razi / Kapikoy or Bazargan)
            origin.name == "Tehran" && (haven.id == "haven_kapikoy" || haven.id == "haven_bazargan") -> {
                deadZonesAvoided.add("Natanz Strike Grid (40km Buffer)")
                deadZonesAvoided.add("Parchin SAM Radar Corridor")
                deadZonesAvoided.add("Tabriz EW Jamming Center (Bypassed via Marand)")

                waypoints.add(GeoPoint(35.8327, 50.9915, "Karaj Transit Hub", true))
                waypoints.add(GeoPoint(36.2688, 50.0041, "Qazvin Resupply Station", true))
                waypoints.add(GeoPoint(36.6769, 48.4963, "Zanjan Valley Waypoint", true))
                waypoints.add(GeoPoint(37.4000, 47.1000, "Mianeh Mountain Bypass", true))
                waypoints.add(GeoPoint(38.0000, 46.8000, "Bostanabad Checkpoint", true))
                // Northern ring bypass around Tabriz EW zone:
                waypoints.add(GeoPoint(38.3500, 46.1000, "Sufian EW Shielded Pass", true))
                waypoints.add(GeoPoint(38.4326, 45.7725, "Marand Safe Corridor", true))
                waypoints.add(GeoPoint(38.5800, 44.9500, "Khoy Convoy Assembly Area", true))
                waypoints.add(GeoPoint(haven.lat, haven.lng, haven.name, true))

                directives.add("Proceed west via Freeway 2 to Qazvin (Low risk).")
                directives.add("Refuel at Zanjan; automated fuel caches operational.")
                directives.add("ALERT: Do NOT enter Tabriz city center due to EW jamming. Take Marand ring-road.")
                directives.add("Final stretch Khoy to Razi border escorted by humanitarian convoy.")
            }

            // Tehran -> Armenia (Norduz)
            origin.name == "Tehran" && haven.id == "haven_norduz" -> {
                deadZonesAvoided.add("Natanz Strike Grid")
                deadZonesAvoided.add("Tabriz Electronic Jamming Grid")

                waypoints.add(GeoPoint(35.8327, 50.9915, "Karaj Transit Hub", true))
                waypoints.add(GeoPoint(36.2688, 50.0041, "Qazvin Resupply Station", true))
                waypoints.add(GeoPoint(36.6769, 48.4963, "Zanjan Valley Waypoint", true))
                waypoints.add(GeoPoint(38.2500, 47.5000, "Ahar Mountain Corridor", true))
                waypoints.add(GeoPoint(38.8000, 46.9000, "Karasht Aras River Canyon", true))
                waypoints.add(GeoPoint(haven.lat, haven.lng, haven.name, true))

                directives.add("Depart Tehran North-West via Karaj.")
                directives.add("Branch north toward Ahar into the Aras Valley.")
                directives.add("Canyon topography shields vehicles from radar tracking.")
                directives.add("Arrive at Norduz border crossing; processing time ~45 minutes.")
            }

            // Tehran -> Alborz Mountain Redoubt (Dizin)
            origin.name == "Tehran" && haven.id == "haven_dizin" -> {
                deadZonesAvoided.add("All Urban Conflict Zones")
                deadZonesAvoided.add("Southern Tehran Missile Facilities")

                waypoints.add(GeoPoint(35.7500, 51.4200, "Tehran Northern District", true))
                waypoints.add(GeoPoint(35.8500, 51.5300, "Lashgarak Road Pass", true))
                waypoints.add(GeoPoint(35.9800, 51.4500, "Shemshak Alpine Pass", true))
                waypoints.add(GeoPoint(haven.lat, haven.lng, haven.name, true))

                directives.add("Immediate 45-minute alpine climb to 3,000m sanctuary.")
                directives.add("Steep mountain topography provides natural radar absorption.")
                directives.add("Safe from airburst shockwaves and urban unrest.")
            }

            // Isfahan -> Chabahar Port (Evasion of Natanz & Hormuz)
            origin.name == "Isfahan" && haven.id == "haven_chabahar" -> {
                deadZonesAvoided.add("Natanz Exclusion Zone (North - avoided entirely)")
                deadZonesAvoided.add("Strait of Hormuz EW/Naval Combat Corridor")
                deadZonesAvoided.add("Bushehr Coastal Air Defense")

                waypoints.add(GeoPoint(32.4000, 51.8500, "Shahreza Southern Exit", true))
                waypoints.add(GeoPoint(31.8974, 54.3569, "Yazd Desert Oasis Waypoint", true))
                waypoints.add(GeoPoint(30.2839, 57.0834, "Kerman Plateau Assembly Area", true))
                waypoints.add(GeoPoint(29.1000, 58.3500, "Bam Desert Pass", true))
                waypoints.add(GeoPoint(27.2000, 60.6000, "Iranshahr Intermediate Checkpoint", true))
                waypoints.add(GeoPoint(26.0000, 60.8000, "Nik Shahr Coastal Approach", true))
                waypoints.add(GeoPoint(haven.lat, haven.lng, haven.name, true))

                directives.add("Evacuate south-east into Yazd, keeping 140km standoff from Natanz.")
                directives.add("Follow the central desert highway toward Kerman and Bam.")
                directives.add("Traverse Baluchistan corridor directly into Chabahar International Port.")
                directives.add("Board authorized international evacuation vessels to Oman.")
            }

            // Default safe dynamic spline algorithm between any origin and haven:
            else -> {
                // Compute direct midpoint, and push it away from any conflicting dead zones!
                var midLat = (origin.lat + haven.lat) / 2.0
                var midLng = (origin.lng + haven.lng) / 2.0

                // Check conflict zones near the path
                THREAT_ZONES.forEach { zone ->
                    val distToZone = calculateDistanceKm(midLat, midLng, zone.centerLat, zone.centerLng)
                    val safeDistance = zone.radiusKm + standoffBufferKm
                    if (distToZone < safeDistance) {
                        deadZonesAvoided.add("${zone.name} (${safeDistance.toInt()}km buffer)")
                        // Push midpoint perpendicularly away from zone center
                        val pushAngle = atan2(midLat - zone.centerLat, midLng - zone.centerLng)
                        midLat = zone.centerLat + (safeDistance + 15.0) / 111.0 * sin(pushAngle)
                        midLng = zone.centerLng + (safeDistance + 15.0) / (111.0 * cos(Math.toRadians(midLat))) * cos(pushAngle)
                    }
                }

                waypoints.add(GeoPoint(
                    (origin.lat * 0.67 + midLat * 0.33),
                    (origin.lng * 0.67 + midLng * 0.33),
                    "Tactical Transit Waypoint Alpha",
                    true
                ))
                waypoints.add(GeoPoint(midLat, midLng, "Threat Bypass Nexus", true))
                waypoints.add(GeoPoint(
                    (midLat * 0.33 + haven.lat * 0.67),
                    (midLng * 0.33 + haven.lng * 0.67),
                    "Safe Corridor Approach Bravo",
                    true
                ))
                waypoints.add(GeoPoint(haven.lat, haven.lng, haven.name, true))

                if (deadZonesAvoided.isEmpty()) {
                    deadZonesAvoided.add("Active SAM radar coverage envelopes")
                    deadZonesAvoided.add("Military curfew perimeter")
                }

                directives.add("Follow ISAC calculated safe vector avoiding high-threat radius.")
                directives.add("Maintain constant standoff buffer of ${standoffBufferKm.toInt()} km from contested sectors.")
                directives.add("Keep vehicle auxiliary fuel full and maintain encrypted radio watch on ${haven.contactFreq}.")
            }
        }

        // Calculate total distance
        var totalDist = 0.0
        for (i in 0 until waypoints.size - 1) {
            totalDist += calculateDistanceKm(
                waypoints[i].lat, waypoints[i].lng,
                waypoints[i + 1].lat, waypoints[i + 1].lng
            )
        }

        val estDriveHours = totalDist / 68.0 // Avg crisis speed 68 km/h with checkpoints
        val riskScore = when {
            totalDist < 100 -> 12
            deadZonesAvoided.size <= 2 -> 24
            deadZonesAvoided.size <= 4 -> 38
            else -> 48
        }

        val classification = when {
            riskScore < 25 -> "OPTIMAL / HIGHEST SAFETY RATING"
            riskScore < 45 -> "VIABLE EVACUATION CORRIDOR / MODERATE HAZARD"
            else -> "HIGH RISK CORRIDOR / PROCEED WITH ARMORED ESCORT"
        }

        return TacticalRoute(
            id = "route_${origin.name.lowercase()}_${haven.id}",
            name = "CORRIDOR: ${origin.name.uppercase()} -> ${haven.name.uppercase()}",
            origin = origin.name,
            destination = haven.name,
            path = waypoints,
            distanceKm = round(totalDist * 10.0) / 10.0,
            estDriveHours = round(estDriveHours * 10.0) / 10.0,
            riskScore = riskScore,
            riskClassification = classification,
            deadZonesAvoided = deadZonesAvoided,
            tacticalDirectives = directives
        )
    }

    // Great-circle Haversine distance in kilometers
    fun calculateDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2.0) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    // Default verified OSINT, Fediverse, RSS, and Homemade SDR Radio Radar sources
    val DEFAULT_INTEL_SOURCES = listOf(
        IntelSource(
            id = "src_x_heyitsmyturn",
            name = "@hey_itsmyturn OSINT Monitor",
            type = IntelSourceType.X_TWITTER,
            endpointUrl = "https://x.com/hey_itsmyturn?lang=en",
            isEnabled = true,
            status = "STREAMING (LIVE OSINT)",
            signalStrengthDbm = -52,
            itemsDetected = 38,
            description = "Middle East kinetic conflict monitor, UAV/ballistic launches, satellite pass updates, air defense telemetry."
        ),
        IntelSource(
            id = "src_fediverse_crisis",
            name = "Fediverse #IranCrisis / Mastodon",
            type = IntelSourceType.FEDIVERSE_MASTODON,
            endpointUrl = "https://mastodon.social/tags/irancrisis.rss",
            isEnabled = true,
            status = "FEDERATED (POLLING 20s)",
            signalStrengthDbm = -60,
            itemsDetected = 24,
            description = "Decentralized citizen reports, civilian shelter conditions, road blockades, and local fuel availability."
        ),
        IntelSource(
            id = "src_rss_humanitarian",
            name = "UNHCR & Red Crescent Emergency Feed",
            type = IntelSourceType.RSS_FEED,
            endpointUrl = "https://reliefweb.int/updates/rss.xml",
            isEnabled = true,
            status = "CONNECTED (RSS/ATOM)",
            signalStrengthDbm = -45,
            itemsDetected = 17,
            description = "Official humanitarian corridor bulletins, border processing speeds, medical evacuation airlift requests."
        ),
        IntelSource(
            id = "src_homemade_sdr_adsb",
            name = "Homemade RTL-SDR ADS-B Radar",
            type = IntelSourceType.HOMEMADE_SDR,
            endpointUrl = "rtl_tcp://192.168.1.150:1234",
            frequencyMhz = 1090.00,
            isEnabled = true,
            status = "RF AGC LOCK // 1090 MHz",
            signalStrengthDbm = -38,
            itemsDetected = 9,
            description = "Local RTL2832U SDR dongle capturing military transponders, tactical tankers, UAV orbits, and emergency squawks (7700)."
        ),
        IntelSource(
            id = "src_radio_radar_vhf",
            name = "Radio Radar / Air Defense Scanner",
            type = IntelSourceType.RADIO_RADAR,
            endpointUrl = "sdr://local-radio/vhf",
            frequencyMhz = 142.80,
            isEnabled = true,
            status = "SWEEPING (118-144 MHz)",
            signalStrengthDbm = -42,
            itemsDetected = 14,
            description = "Local RF spectrum scanner monitoring emergency crisis dispatch, UN convoy escorts, and radar pulse detections."
        )
    )

    // Latitude/Longitude Threat-Level Heatmap Telemetry Points (Kinetic, EW, SAM & Urban Combat Intensity)
    val THREAT_HEATMAP_POINTS = listOf(
        // Macro theater peaks
        ThreatHeatmapPoint("hm_natanz_core", 33.5119, 51.9167, 1.00f, 38.0, ThreatType.KINETIC_STRIKE, "Natanz Bunker Strike Core"),
        ThreatHeatmapPoint("hm_natanz_n", 33.7200, 51.8500, 0.72f, 22.0, ThreatType.AIR_DEFENSE_RADAR, "Natanz North SAM Battery"),
        ThreatHeatmapPoint("hm_isfahan_ab", 32.7480, 51.8610, 0.96f, 28.0, ThreatType.KINETIC_STRIKE, "Isfahan 8th Tactical Airbase"),
        ThreatHeatmapPoint("hm_isfahan_city", 32.6546, 51.6680, 0.68f, 18.0, ThreatType.MILITARY_LOCKDOWN, "Isfahan Ring Checkpoint"),
        ThreatHeatmapPoint("hm_fordow", 34.8833, 50.9833, 0.94f, 26.0, ThreatType.KINETIC_STRIKE, "Fordow Mountain Perimeter"),
        ThreatHeatmapPoint("hm_parchin", 35.5250, 51.7700, 0.91f, 24.0, ThreatType.MISSILE_SILO, "Parchin Missile Complex"),
        ThreatHeatmapPoint("hm_tehran_south_sam", 35.5300, 51.3700, 0.84f, 18.0, ThreatType.AIR_DEFENSE_RADAR, "Rey Air Defense Grid"),
        ThreatHeatmapPoint("hm_bushehr", 28.9234, 50.8203, 0.95f, 32.0, ThreatType.RADIATION_HAZARD, "Bushehr Reactor Exclusion"),
        ThreatHeatmapPoint("hm_hormuz_ew", 26.5667, 56.2500, 0.88f, 45.0, ThreatType.EW_JAMMING, "Hormuz Naval EW Jamming"),
        ThreatHeatmapPoint("hm_bandar_abbas", 27.1832, 56.2666, 0.76f, 24.0, ThreatType.MILITARY_LOCKDOWN, "Bandar Abbas Naval Docks"),
        ThreatHeatmapPoint("hm_ahvaz", 31.3183, 48.6706, 0.85f, 30.0, ThreatType.MILITARY_LOCKDOWN, "Khuzestan Oil & Armor Zone"),
        ThreatHeatmapPoint("hm_kermanshah", 34.3142, 47.0650, 0.82f, 28.0, ThreatType.MISSILE_SILO, "Zagros Silo Counter-Battery"),
        ThreatHeatmapPoint("hm_tabriz_ew", 38.0800, 46.2919, 0.64f, 20.0, ThreatType.EW_JAMMING, "Tabriz Central EW Node"),
        ThreatHeatmapPoint("hm_mashhad_chk", 36.2972, 59.6067, 0.42f, 18.0, ThreatType.MILITARY_LOCKDOWN, "Mashhad Curfew Belt"),

        // Granular Tehran urban heatmap pockets (for walking route avoidance!)
        ThreatHeatmapPoint("hm_thr_mehrabad", 35.6890, 51.3130, 0.92f, 3.8, ThreatType.KINETIC_STRIKE, "Mehrabad Airfield Strike Risk"),
        ThreatHeatmapPoint("hm_thr_enghelab", 35.7010, 51.3910, 0.78f, 2.8, ThreatType.MILITARY_LOCKDOWN, "Central Command Armored Cordon"),
        ThreatHeatmapPoint("hm_thr_valiasr_mid", 35.7350, 51.4100, 0.65f, 2.2, ThreatType.EW_JAMMING, "Abbasabad EW Jamming Tower"),
        ThreatHeatmapPoint("hm_thr_lavizan", 35.7680, 51.4920, 0.86f, 3.2, ThreatType.AIR_DEFENSE_RADAR, "Lavizan SAM Battery"),
        ThreatHeatmapPoint("hm_thr_azadegan", 35.6350, 51.3400, 0.74f, 4.0, ThreatType.MILITARY_LOCKDOWN, "Azadegan Freeway Blockade")
    )

    // Cataloged Public OSINT & State Security CCTV Cameras (Vulnerable vs Hardened vs Offline Blind Spots)
    val SURVEILLANCE_CAMERAS = listOf(
        SurveillanceCamera(
            id = "cam_thr_azadi_sq",
            name = "Azadi Square Municipal Dome #04",
            lat = 35.6997,
            lng = 51.3380,
            operatorType = CameraOperatorType.PUBLIC_MUNICIPAL_OSINT,
            vulnerabilityStatus = CameraVulnerabilityStatus.VULNERABLE_PUBLIC_OSINT,
            coverageRadiusMeters = 450.0,
            azimuthDegrees = 360f,
            fovAngleDegrees = 360f,
            streamProtocol = "RTSP :554 (Unencrypted Public IP)",
            osintNote = "Indexed on public Shodan/OSINT webcam feeds. Anyone online can observe traffic movement here."
        ),
        SurveillanceCamera(
            id = "cam_thr_valiasr_junction",
            name = "Valiasr–Hemmat State ANPR Gantry",
            lat = 35.7448,
            lng = 51.4095,
            operatorType = CameraOperatorType.STATE_SECURITY_ANPR,
            vulnerabilityStatus = CameraVulnerabilityStatus.HARDENED_STATE_ENCRYPTED,
            coverageRadiusMeters = 520.0,
            azimuthDegrees = 180f,
            fovAngleDegrees = 120f,
            streamProtocol = "Encrypted State Fiber VPN",
            osintNote = "Invulnerable to external OSINT hijacking. Active facial & license plate recognition logging."
        ),
        SurveillanceCamera(
            id = "cam_thr_vanak_metro",
            name = "Vanak Sq Transit Biometric Array",
            lat = 35.7575,
            lng = 51.4099,
            operatorType = CameraOperatorType.TRANSIT_HUB_FACIAL,
            vulnerabilityStatus = CameraVulnerabilityStatus.HARDENED_STATE_ENCRYPTED,
            coverageRadiusMeters = 380.0,
            azimuthDegrees = 360f,
            fovAngleDegrees = 360f,
            streamProtocol = "Closed Intranet TLS 1.3",
            osintNote = "State transit surveillance dome. Route 400m east or west through side alleys to stay invulnerable."
        ),
        SurveillanceCamera(
            id = "cam_thr_tehran_uni",
            name = "Enghelab Avenue Traffic Cam #19",
            lat = 35.7022,
            lng = 51.3955,
            operatorType = CameraOperatorType.PUBLIC_MUNICIPAL_OSINT,
            vulnerabilityStatus = CameraVulnerabilityStatus.VULNERABLE_PUBLIC_OSINT,
            coverageRadiusMeters = 350.0,
            azimuthDegrees = 90f,
            fovAngleDegrees = 110f,
            streamProtocol = "HTTP MJPEG / Port 8080 (Default Auth)",
            osintNote = "Public municipal webcam with exposed telemetry stream. Avoid main avenue exposure."
        ),
        SurveillanceCamera(
            id = "cam_thr_tajrish_approach",
            name = "Shariati–Qeytarieh Relay Cam #07",
            lat = 35.7880,
            lng = 51.4380,
            operatorType = CameraOperatorType.PUBLIC_MUNICIPAL_OSINT,
            vulnerabilityStatus = CameraVulnerabilityStatus.OFFLINE_JAMMED,
            coverageRadiusMeters = 300.0,
            azimuthDegrees = 360f,
            fovAngleDegrees = 360f,
            streamProtocol = "OFFLINE (Power Grid / EW Cut)",
            osintNote = "Substation outage verified via SDR RF silence. Confirmed safe pedestrian blind spot."
        ),
        SurveillanceCamera(
            id = "cam_thr_chitgar_toll",
            name = "Tehran–Karaj Hwy 2 State Toll ANPR",
            lat = 35.7280,
            lng = 51.2450,
            operatorType = CameraOperatorType.STATE_SECURITY_ANPR,
            vulnerabilityStatus = CameraVulnerabilityStatus.HARDENED_STATE_ENCRYPTED,
            coverageRadiusMeters = 600.0,
            azimuthDegrees = 270f,
            fovAngleDegrees = 95f,
            streamProtocol = "Dedicated State Microwave Link",
            osintNote = "Monitors all westbound vehicles exiting Tehran. Pedestrian trail north of park stays outside FOV."
        ),
        SurveillanceCamera(
            id = "cam_thr_tochal_south",
            name = "Velenjak Foothill Weather & Traffic Cam",
            lat = 35.8020,
            lng = 51.4020,
            operatorType = CameraOperatorType.PUBLIC_MUNICIPAL_OSINT,
            vulnerabilityStatus = CameraVulnerabilityStatus.VULNERABLE_PUBLIC_OSINT,
            coverageRadiusMeters = 320.0,
            azimuthDegrees = 160f,
            fovAngleDegrees = 100f,
            streamProtocol = "Public HLS / Port 80",
            osintNote = "Publicly accessible OSINT municipal camera overlooking Velenjak road."
        ),
        SurveillanceCamera(
            id = "cam_isfahan_khaju",
            name = "Isfahan Zayanderud Bridge Municipal Cam",
            lat = 32.6368,
            lng = 51.6833,
            operatorType = CameraOperatorType.PUBLIC_MUNICIPAL_OSINT,
            vulnerabilityStatus = CameraVulnerabilityStatus.VULNERABLE_PUBLIC_OSINT,
            coverageRadiusMeters = 400.0,
            azimuthDegrees = 360f,
            fovAngleDegrees = 360f,
            streamProtocol = "RTSP :554 (Public OSINT)",
            osintNote = "Exposed city traffic feed over river crossing."
        ),
        SurveillanceCamera(
            id = "cam_isfahan_north_gate",
            name = "Isfahan Northern Airbase Perimeter PTZ",
            lat = 32.7250,
            lng = 51.7800,
            operatorType = CameraOperatorType.CRITICAL_INFRASTRUCTURE_PTZ,
            vulnerabilityStatus = CameraVulnerabilityStatus.HARDENED_STATE_ENCRYPTED,
            coverageRadiusMeters = 1200.0,
            azimuthDegrees = 360f,
            fovAngleDegrees = 360f,
            streamProtocol = "Military Encrypted Coax/Fiber",
            osintNote = "High-zoom thermal PTZ array. Do not approach within 1.2 km."
        ),
        SurveillanceCamera(
            id = "cam_tabriz_central",
            name = "Tabriz Saat Square Municipal Cam",
            lat = 38.0740,
            lng = 46.2960,
            operatorType = CameraOperatorType.PUBLIC_MUNICIPAL_OSINT,
            vulnerabilityStatus = CameraVulnerabilityStatus.OFFLINE_JAMMED,
            coverageRadiusMeters = 350.0,
            azimuthDegrees = 360f,
            fovAngleDegrees = 360f,
            streamProtocol = "OFFLINE (EW Blackout)",
            osintNote = "Camera uplink jammed by Tabriz NW EW array."
        ),
        SurveillanceCamera(
            id = "cam_bandar_port",
            name = "Bandar Abbas Harbor Maritime OSINT Cam",
            lat = 27.1450,
            lng = 56.2100,
            operatorType = CameraOperatorType.PUBLIC_MUNICIPAL_OSINT,
            vulnerabilityStatus = CameraVulnerabilityStatus.VULNERABLE_PUBLIC_OSINT,
            coverageRadiusMeters = 500.0,
            azimuthDegrees = 180f,
            fovAngleDegrees = 120f,
            streamProtocol = "HTTP Port 8081 (Exposed Port Cam)",
            osintNote = "Public port authority stream visible in maritime OSINT trackers."
        )
    )
}


