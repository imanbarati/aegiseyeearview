package com.example.data

import com.example.model.IntelSource
import com.example.model.IntelSourceType
import com.example.model.LiveIncident
import com.example.model.SafeHaven
import com.example.model.SafeHavenType
import com.example.model.ThreatHeatmapPoint
import com.example.model.ThreatLevel
import com.example.model.ThreatType
import com.example.model.ThreatZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Real public open data for Aegis Eye.
 * - USGS earthquake GeoJSON (global, last day)
 * - OpenStreetMap Overpass hospitals / clinics near the user
 *
 * No fictional combat / kinetic-strike theatre data.
 */
object RealOpenDataService {

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    data class OpenDataBundle(
        val threatZones: List<ThreatZone>,
        val heatmapPoints: List<ThreatHeatmapPoint>,
        val liveIncidents: List<LiveIncident>,
        val safeHavens: List<SafeHaven>,
        val intelSources: List<IntelSource>,
        val statusMessage: String,
    )

    suspend fun fetchAll(userLat: Double, userLng: Double): OpenDataBundle = withContext(Dispatchers.IO) {
        val quakes = runCatching { fetchUsgsEarthquakes() }.getOrElse { emptyList() }
        val hospitals = runCatching { fetchOsmHospitals(userLat, userLng) }.getOrElse { emptyList() }

        val zones = quakes.map { it.toThreatZone() }
        val heat = quakes.map { it.toHeatmap() }
        val incidents = quakes.map { it.toIncident() }

        val sources = listOf(
            IntelSource(
                id = "usgs-eq-day",
                name = "USGS Earthquakes (M2.5+ / 24h)",
                type = IntelSourceType.OPEN_API_JSON,
                endpointUrl = "https://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/2.5_day.geojson",
                isEnabled = true,
                status = if (quakes.isNotEmpty()) "LIVE • ${quakes.size} events" else "FETCH FAILED / EMPTY",
                itemsDetected = quakes.size,
                description = "Official USGS real-time seismic feed. Mapped as hazard markers — not military intel.",
                lastFetchedMs = System.currentTimeMillis(),
            ),
            IntelSource(
                id = "osm-overpass-hospital",
                name = "OpenStreetMap hospitals (Overpass)",
                type = IntelSourceType.OPEN_API_JSON,
                endpointUrl = "https://overpass-api.de/api/interpreter",
                isEnabled = true,
                status = if (hospitals.isNotEmpty()) "LIVE • ${hospitals.size} POIs" else "FETCH FAILED / EMPTY",
                itemsDetected = hospitals.size,
                description = "Community-mapped hospitals/clinics near GPS. Real POIs, not scripted safe-havens.",
                lastFetchedMs = System.currentTimeMillis(),
            ),
            IntelSource(
                id = "osm-tiles",
                name = "OpenStreetMap / Carto tiles",
                type = IntelSourceType.OPEN_API_JSON,
                endpointUrl = "https://tile.openstreetmap.org/",
                isEnabled = true,
                status = "MAP ENGINE",
                description = "Real basemap tiles via osmdroid.",
            ),
        )

        val status = buildString {
            append("USGS quakes=${quakes.size}")
            append(" • OSM hospitals=${hospitals.size}")
            if (quakes.isEmpty() && hospitals.isEmpty()) append(" • offline or rate-limited")
        }

        OpenDataBundle(
            threatZones = zones,
            heatmapPoints = heat,
            liveIncidents = incidents,
            safeHavens = hospitals,
            intelSources = sources,
            statusMessage = status,
        )
    }

    private data class Quake(
        val id: String,
        val title: String,
        val mag: Double,
        val lat: Double,
        val lng: Double,
        val depthKm: Double,
        val timeMs: Long,
        val url: String,
        val place: String,
    ) {
        fun level(): ThreatLevel = when {
            mag >= 6.0 -> ThreatLevel.CRITICAL_ALPHA
            mag >= 5.0 -> ThreatLevel.HIGH_BRAVO
            mag >= 4.0 -> ThreatLevel.MODERATE_CHARLIE
            else -> ThreatLevel.CAUTION_DELTA
        }

        fun ageMinutes(): Int =
            ((System.currentTimeMillis() - timeMs) / 60_000L).toInt().coerceAtLeast(0)

        fun toThreatZone(): ThreatZone {
            val radius = (mag * 8.0).coerceIn(5.0, 80.0)
            return ThreatZone(
                id = id,
                name = "M${"%.1f".format(mag)} • $place",
                sectorCode = "USGS",
                level = level(),
                type = ThreatType.KINETIC_STRIKE,
                centerLat = lat,
                centerLng = lng,
                radiusKm = radius,
                description = "USGS earthquake magnitude ${"%.1f".format(mag)}, depth ${"%.1f".format(depthKm)} km.",
                tacticalSitRep = title.ifBlank { place },
                casualtyRisk = "Seismic hazard (public USGS feed — not a combat zone)",
                bypassBufferKm = radius * 0.5,
                lastPingMinutesAgo = ageMinutes(),
            )
        }

        fun toHeatmap(): ThreatHeatmapPoint = ThreatHeatmapPoint(
            id = "heat-$id",
            lat = lat,
            lng = lng,
            intensity = (mag / 8.0).toFloat().coerceIn(0.15f, 1f),
            radiusKm = (mag * 6.0).coerceIn(4.0, 60.0),
            category = ThreatType.KINETIC_STRIKE,
            label = "M${"%.1f".format(mag)}",
        )

        fun toIncident(): LiveIncident = LiveIncident(
            id = id,
            minutesAgo = ageMinutes(),
            sector = "USGS",
            title = "Earthquake M${"%.1f".format(mag)}",
            description = title.ifBlank { place },
            severity = level(),
            sourceAgency = "USGS",
            lat = lat,
            lng = lng,
            sourceUrl = url,
            publishedAtMs = timeMs,
            credibilityScore = 95,
            verificationStatus = "OFFICIAL_FEED",
            corroboratedByCount = 1,
            geoPrecision = "USGS_EPICENTER",
            disinformationRiskNote = null,
        )
    }

    private fun fetchUsgsEarthquakes(): List<Quake> {
        val url = "https://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/2.5_day.geojson"
        val req = Request.Builder().url(url).header("Accept", "application/json").get().build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("USGS HTTP ${resp.code}")
            val body = resp.body?.string() ?: error("empty")
            val features = JSONObject(body).optJSONArray("features") ?: JSONArray()
            val out = ArrayList<Quake>(features.length())
            for (i in 0 until features.length()) {
                val f = features.optJSONObject(i) ?: continue
                val props = f.optJSONObject("properties") ?: continue
                val geom = f.optJSONObject("geometry") ?: continue
                val coords = geom.optJSONArray("coordinates") ?: continue
                if (coords.length() < 2) continue
                val lng = coords.optDouble(0)
                val lat = coords.optDouble(1)
                val depth = if (coords.length() > 2) coords.optDouble(2) else 0.0
                val mag = props.optDouble("mag", Double.NaN)
                if (mag.isNaN()) continue
                out.add(
                    Quake(
                        id = f.optString("id", "eq-$i"),
                        title = props.optString("title", ""),
                        mag = mag,
                        lat = lat,
                        lng = lng,
                        depthKm = depth,
                        timeMs = props.optLong("time", System.currentTimeMillis()),
                        url = props.optString("url", ""),
                        place = props.optString("place", "Unknown"),
                    ),
                )
            }
            return out.sortedByDescending { it.mag }.take(80)
        }
    }

    private fun fetchOsmHospitals(lat: Double, lng: Double, radiusM: Int = 25_000): List<SafeHaven> {
        val query =
            """
            [out:json][timeout:25];
            (
              node["amenity"~"hospital|clinic"](around:$radiusM,$lat,$lng);
              way["amenity"~"hospital|clinic"](around:$radiusM,$lat,$lng);
            );
            out center tags 40;
            """.trimIndent()
        val body = query.toRequestBody("text/plain".toMediaType())
        val req = Request.Builder()
            .url("https://overpass-api.de/api/interpreter")
            .post(body)
            .header("Accept", "application/json")
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("Overpass HTTP ${resp.code}")
            val root = JSONObject(resp.body?.string() ?: error("empty"))
            val elements = root.optJSONArray("elements") ?: JSONArray()
            val out = ArrayList<SafeHaven>()
            for (i in 0 until elements.length()) {
                val el = elements.optJSONObject(i) ?: continue
                val tags = el.optJSONObject("tags") ?: JSONObject()
                val name = tags.optString("name").ifBlank {
                    tags.optString("amenity", "Medical facility")
                }
                val plat: Double
                val plng: Double
                when (el.optString("type")) {
                    "node" -> {
                        plat = el.optDouble("lat")
                        plng = el.optDouble("lon")
                    }
                    else -> {
                        val c = el.optJSONObject("center") ?: continue
                        plat = c.optDouble("lat")
                        plng = c.optDouble("lon")
                    }
                }
                val amenity = tags.optString("amenity", "hospital")
                out.add(
                    SafeHaven(
                        id = "osm-${el.optLong("id")}",
                        name = name,
                        countryOrRegion = tags.optString("addr:city").ifBlank { "OpenStreetMap" },
                        type = SafeHavenType.HUMANITARIAN_STATION,
                        lat = plat,
                        lng = plng,
                        borderStatus = "OSM POI • $amenity",
                        clearanceLevel = "Public map data (verify on site)",
                        supplies = listOfNotNull(
                            tags.optString("emergency").takeIf { it.isNotBlank() }?.let { "emergency=$it" },
                            tags.optString("phone").takeIf { it.isNotBlank() },
                            tags.optString("website").takeIf { it.isNotBlank() },
                        ).ifEmpty { listOf("See OSM tags / local signage") },
                        contactFreq = tags.optString("phone").ifBlank { "N/A" },
                        advisories = "Sourced live from OpenStreetMap Overpass near your GPS. Not a military safe zone.",
                        isDesignatedWalkingSafeZone = true,
                    ),
                )
            }
            return out.distinctBy { it.id }.take(40)
        }
    }
}
