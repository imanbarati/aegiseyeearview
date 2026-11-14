package com.example.service

import android.content.Context
import com.example.data.TacticalDataProvider
import com.example.model.GeoPoint
import com.example.model.ThreatLevel
import com.example.model.ThreatType
import com.example.model.ThreatZone
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

/**
 * Strongly typed threat-zone polygon parsed from local GeoJSON files
 * (`Polygon` or `MultiPolygon`) for rendering as custom clickable OsmDroid polygons.
 */
data class GeoJsonThreatPolygon(
    val id: String,
    val name: String,
    val sectorCode: String,
    val threatLevel: ThreatLevel,
    val category: String,
    val notes: String,
    val casualtyRisk: String,
    val bypassBufferKm: Double,
    val sourceFileName: String,
    val outerRing: List<GeoPoint>,
    val holes: List<List<GeoPoint>> = emptyList(),
    val centerLat: Double,
    val centerLng: Double,
    val approxRadiusKm: Double,
    val approxAreaKm2: Double,
    val strokeColorHex: String,
    val fillColorHex: String,
    val properties: Map<String, String> = emptyMap(),
    val isEnabled: Boolean = true,
    val intensity: Float = defaultIntensityForLevel(threatLevel)
) {
    val isHighIntensity: Boolean
        get() = threatLevel == ThreatLevel.CRITICAL_ALPHA ||
            threatLevel == ThreatLevel.HIGH_BRAVO ||
            intensity >= ThreatGeofenceEvaluator.HIGH_INTENSITY_THRESHOLD

    fun containsPoint(lat: Double, lng: Double): Boolean {
        if (!isEnabled || outerRing.size < 3) return false
        if (!ThreatGeofenceEvaluator.isPointInsidePolygon(lat, lng, outerRing)) return false
        return holes.none { hole -> ThreatGeofenceEvaluator.isPointInsidePolygon(lat, lng, hole) }
    }
    fun toThreatZone(): ThreatZone {
        val mappedType = when {
            "AIR_DEFENSE" in category.uppercase() || "SAM" in category.uppercase() -> ThreatType.AIR_DEFENSE_RADAR
            "EW" in category.uppercase() || "JAM" in category.uppercase() -> ThreatType.EW_JAMMING
            "LOCKDOWN" in category.uppercase() || "CHECKPOINT" in category.uppercase() -> ThreatType.MILITARY_LOCKDOWN
            "BALLISTIC" in category.uppercase() || "MISSILE" in category.uppercase() -> ThreatType.MISSILE_SILO
            "RADIATION" in category.uppercase() || "CBRN" in category.uppercase() -> ThreatType.RADIATION_HAZARD
            else -> ThreatType.KINETIC_STRIKE
        }
        return ThreatZone(
            id = id,
            name = name,
            sectorCode = sectorCode,
            level = threatLevel,
            type = mappedType,
            centerLat = centerLat,
            centerLng = centerLng,
            radiusKm = approxRadiusKm.coerceAtLeast(3.0),
            description = notes.ifBlank { "Parsed from local GeoJSON threat layer ($sourceFileName)." },
            tacticalSitRep = "Custom GeoJSON exclusion polygon active (${outerRing.size} vertices, ~${approxAreaKm2.toInt()} km²).",
            casualtyRisk = casualtyRisk,
            bypassBufferKm = bypassBufferKm,
            lastPingMinutesAgo = 2
        )
    }
}

/**
 * Service responsible for reading local GeoJSON files (from app-bundled assets or user-imported
 * local `.geojson` documents) representing threat zones and parsing their Polygon / MultiPolygon
 * geometries into clickable OsmDroid threat-zone polygon models.
 */
object GeoJsonThreatZoneService {

    private const val ASSET_DIR = "tactical"

    fun loadThreatPolygonsFromAssets(context: Context): List<GeoJsonThreatPolygon> {
        val files = try {
            context.assets.list(ASSET_DIR)
                ?.filter { it.endsWith(".geojson", ignoreCase = true) || it.endsWith(".json", ignoreCase = true) }
                ?.sorted()
                ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }

        val polygons = mutableListOf<GeoJsonThreatPolygon>()
        for (fileName in files) {
            val raw = try {
                context.assets.open("$ASSET_DIR/$fileName").bufferedReader().use { it.readText() }
            } catch (_: Exception) {
                null
            } ?: continue
            polygons.addAll(parseThreatPolygons(rawJson = raw, sourceFileName = fileName))
        }
        return polygons
    }

    fun parseThreatPolygons(rawJson: String, sourceFileName: String): List<GeoJsonThreatPolygon> {
        return try {
            val root = JSONObject(rawJson)
            val result = mutableListOf<GeoJsonThreatPolygon>()
            when (root.optString("type")) {
                "FeatureCollection" -> {
                    val features = root.optJSONArray("features") ?: JSONArray()
                    for (i in 0 until features.length()) {
                        val feat = features.optJSONObject(i) ?: continue
                        result.addAll(parseFeaturePolygons(feat, sourceFileName, i))
                    }
                }
                "Feature" -> {
                    result.addAll(parseFeaturePolygons(root, sourceFileName, 0))
                }
            }
            result
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseFeaturePolygons(
        featObj: JSONObject,
        sourceFileName: String,
        featureIndex: Int
    ): List<GeoJsonThreatPolygon> {
        val geom = featObj.optJSONObject("geometry") ?: return emptyList()
        val geomType = geom.optString("type")
        if (geomType != "Polygon" && geomType != "MultiPolygon") return emptyList()
        val coords = geom.optJSONArray("coordinates") ?: return emptyList()

        val propsObj = featObj.optJSONObject("properties") ?: JSONObject()
        val propsMap = LinkedHashMap<String, String>()
        val keys = propsObj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = propsObj.opt(key)
            if (value != null && value != JSONObject.NULL) {
                propsMap[key] = value.toString()
            }
        }

        val threatLevel = parseThreatLevel(propsMap["threat_level"])
        val defaultColors = defaultColorsForLevel(threatLevel)

        val baseId = propsMap["id"] ?: "${sourceFileName.substringBeforeLast('.')}_poly_$featureIndex"
        val name = propsMap["name"] ?: "GeoJSON Threat Sector #${featureIndex + 1}"
        val sectorCode = propsMap["sector_code"] ?: "GJ-${featureIndex + 1}"
        val category = propsMap["category"] ?: "THREAT_POLYGON"
        val notes = propsMap["notes"] ?: "Local GeoJSON threat zone polygon."
        val casualtyRisk = propsMap["casualty_risk"] ?: when (threatLevel) {
            ThreatLevel.CRITICAL_ALPHA -> "EXTREME (90%+)"
            ThreatLevel.HIGH_BRAVO -> "SEVERE (75%)"
            ThreatLevel.MODERATE_CHARLIE -> "MODERATE (50%)"
            ThreatLevel.CAUTION_DELTA -> "ELEVATED (25%)"
        }
        val bypassBuffer = propsMap["bypass_buffer_km"]?.toDoubleOrNull() ?: 20.0
        val strokeHex = propsMap["stroke"] ?: defaultColors.first
        val fillHex = propsMap["fill"] ?: defaultColors.second
        val parsedIntensity = propsMap["intensity"]?.toFloatOrNull()?.coerceIn(0f, 1f)
            ?: defaultIntensityForLevel(threatLevel)

        val polygonCoordsList: List<JSONArray> = if (geomType == "Polygon") {
            listOf(coords)
        } else {
            (0 until coords.length()).mapNotNull { coords.optJSONArray(it) }
        }

        val parsed = mutableListOf<GeoJsonThreatPolygon>()
        polygonCoordsList.forEachIndexed { partIdx, polyArray ->
            val rings = parseRings(polyArray)
            if (rings.isNotEmpty() && rings.first().size >= 3) {
                val outer = rings.first()
                val holes = if (rings.size > 1) rings.drop(1) else emptyList()
                val centerLat = outer.map { it.lat }.average()
                val centerLng = outer.map { it.lng }.average()
                val maxRadiusKm = outer.maxOfOrNull { pt ->
                    TacticalDataProvider.calculateDistanceKm(centerLat, centerLng, pt.lat, pt.lng)
                } ?: 5.0
                val areaKm2 = estimatePolygonAreaKm2(outer)

                parsed.add(
                    GeoJsonThreatPolygon(
                        id = if (polygonCoordsList.size == 1) baseId else "${baseId}_p$partIdx",
                        name = name,
                        sectorCode = sectorCode,
                        threatLevel = threatLevel,
                        category = category,
                        notes = notes,
                        casualtyRisk = casualtyRisk,
                        bypassBufferKm = bypassBuffer,
                        sourceFileName = sourceFileName,
                        outerRing = outer,
                        holes = holes,
                        centerLat = centerLat,
                        centerLng = centerLng,
                        approxRadiusKm = maxRadiusKm,
                        approxAreaKm2 = areaKm2,
                        strokeColorHex = strokeHex,
                        fillColorHex = fillHex,
                        properties = propsMap,
                        intensity = parsedIntensity
                    )
                )
            }
        }
        return parsed
    }

    private fun parseRings(polyArray: JSONArray): List<List<GeoPoint>> {
        val rings = mutableListOf<List<GeoPoint>>()
        for (i in 0 until polyArray.length()) {
            val ringArr = polyArray.optJSONArray(i) ?: continue
            val pts = mutableListOf<GeoPoint>()
            for (j in 0 until ringArr.length()) {
                val pair = ringArr.optJSONArray(j) ?: continue
                if (pair.length() < 2) continue
                val lng = pair.optDouble(0, Double.NaN)
                val lat = pair.optDouble(1, Double.NaN)
                if (!lat.isNaN() && !lng.isNaN()) {
                    pts.add(GeoPoint(lat = lat, lng = lng))
                }
            }
            if (pts.size >= 3) rings.add(pts)
        }
        return rings
    }

    private fun parseThreatLevel(raw: String?): ThreatLevel {
        val norm = raw?.uppercase().orEmpty()
        return when {
            "CRITICAL" in norm || "LETHAL" in norm || "ALPHA" in norm -> ThreatLevel.CRITICAL_ALPHA
            "HIGH" in norm || "BRAVO" in norm || "SEVERE" in norm -> ThreatLevel.HIGH_BRAVO
            "MODERATE" in norm || "CHARLIE" in norm -> ThreatLevel.MODERATE_CHARLIE
            else -> ThreatLevel.CAUTION_DELTA
        }
    }

    private fun defaultColorsForLevel(level: ThreatLevel): Pair<String, String> = when (level) {
        ThreatLevel.CRITICAL_ALPHA -> "#FF2A2A" to "#FF2A2A"
        ThreatLevel.HIGH_BRAVO -> "#FF6B00" to "#FF6B00"
        ThreatLevel.MODERATE_CHARLIE -> "#FFB300" to "#FFB300"
        ThreatLevel.CAUTION_DELTA -> "#00E5FF" to "#00E5FF"
    }

    private fun estimatePolygonAreaKm2(ring: List<GeoPoint>): Double {
        if (ring.size < 3) return 0.0
        val avgLatRad = Math.toRadians(ring.map { it.lat }.average())
        val kmPerDegLat = 111.32
        val kmPerDegLng = 111.32 * kotlin.math.cos(avgLatRad)
        var sum = 0.0
        for (i in ring.indices) {
            val p1 = ring[i]
            val p2 = ring[(i + 1) % ring.size]
            val x1 = p1.lng * kmPerDegLng
            val y1 = p1.lat * kmPerDegLat
            val x2 = p2.lng * kmPerDegLng
            val y2 = p2.lat * kmPerDegLat
            sum += (x1 * y2) - (x2 * y1)
        }
        return abs(sum) / 2.0
    }
}

fun defaultIntensityForLevel(level: ThreatLevel): Float = when (level) {
    ThreatLevel.CRITICAL_ALPHA -> 0.95f
    ThreatLevel.HIGH_BRAVO -> 0.80f
    ThreatLevel.MODERATE_CHARLIE -> 0.55f
    ThreatLevel.CAUTION_DELTA -> 0.35f
}

data class ThreatGeofenceAlert(
    val polygonId: String,
    val sectorCode: String,
    val zoneName: String,
    val threatLevel: ThreatLevel,
    val intensity: Float,
    val casualtyRisk: String,
    val bypassBufferKm: Double,
    val centerLat: Double,
    val centerLng: Double,
    val enteredLat: Double,
    val enteredLng: Double,
    val triggeredAtMs: Long = System.currentTimeMillis()
)

/**
 * Evaluates user GPS coordinates against active GeoJSON threat polygons using ray-casting
 * point-in-polygon tests (with hole subtraction) and high-intensity threshold filtering.
 */
object ThreatGeofenceEvaluator {

    const val HIGH_INTENSITY_THRESHOLD = 0.70f

    fun isPointInsidePolygon(lat: Double, lng: Double, ring: List<GeoPoint>): Boolean {
        if (ring.size < 3) return false
        var inside = false
        var j = ring.lastIndex
        for (i in ring.indices) {
            val yi = ring[i].lat
            val xi = ring[i].lng
            val yj = ring[j].lat
            val xj = ring[j].lng
            val intersects = ((yi > lat) != (yj > lat)) &&
                (lng < (xj - xi) * (lat - yi) / ((yj - yi).takeIf { it != 0.0 } ?: 1e-12) + xi)
            if (intersects) inside = !inside
            j = i
        }
        return inside
    }

    fun evaluateGeoJsonEntry(
        lat: Double,
        lng: Double,
        polygons: List<GeoJsonThreatPolygon>
    ): ThreatGeofenceAlert? {
        val hit = polygons
            .asSequence()
            .filter { it.isEnabled && it.isHighIntensity && it.containsPoint(lat, lng) }
            .maxByOrNull { it.intensity }
            ?: return null

        return ThreatGeofenceAlert(
            polygonId = hit.id,
            sectorCode = hit.sectorCode,
            zoneName = hit.name,
            threatLevel = hit.threatLevel,
            intensity = hit.intensity,
            casualtyRisk = hit.casualtyRisk,
            bypassBufferKm = hit.bypassBufferKm,
            centerLat = hit.centerLat,
            centerLng = hit.centerLng,
            enteredLat = lat,
            enteredLng = lng
        )
    }
}
