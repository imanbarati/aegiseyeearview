package com.example.ui.map.mvt

import android.content.Context
import com.example.model.GeoPoint
import org.json.JSONArray
import org.json.JSONObject

/**
 * Represents a local GeoJSON tactical file compiled into a Mapbox Vector Tile (MVT)
 * packet and decoded via [MvtParser] for rendering inside the OsmDroid map view.
 */
data class TacticalGeoJsonOverlay(
    val id: String,
    val name: String,
    val fileName: String,
    val isEnabled: Boolean,
    val isCustomImported: Boolean,
    val tileZ: Int,
    val tileX: Int,
    val tileY: Int,
    val mvtByteSize: Int,
    val mvtTile: MvtTile
) {
    val featureCount: Int get() = mvtTile.totalFeatures
}

object GeoJsonTacticalLoader {

    private const val ASSET_SUBDIR = "tactical"
    private const val DEFAULT_TILE_ZOOM = 10

    fun loadBundledOverlays(context: Context): List<TacticalGeoJsonOverlay> {
        val assetFiles = try {
            context.assets.list(ASSET_SUBDIR)?.filter { it.endsWith(".geojson", ignoreCase = true) || it.endsWith(".json", ignoreCase = true) }?.sorted()
                ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }

        val loaded = mutableListOf<TacticalGeoJsonOverlay>()
        for (fileName in assetFiles) {
            val raw = try {
                context.assets.open("$ASSET_SUBDIR/$fileName").bufferedReader().use { it.readText() }
            } catch (_: Exception) {
                null
            } ?: continue

            val overlay = parseGeoJsonToMvtOverlay(
                id = "asset_${fileName.substringBeforeLast('.')}",
                fallbackName = formatDisplayName(fileName),
                fileName = fileName,
                rawJson = raw,
                isCustomImported = false
            )
            if (overlay != null) {
                loaded.add(overlay)
            }
        }
        return loaded
    }

    fun parseGeoJsonToMvtOverlay(
        id: String,
        fallbackName: String,
        fileName: String,
        rawJson: String,
        isCustomImported: Boolean
    ): TacticalGeoJsonOverlay? {
        return try {
            val root = JSONObject(rawJson)
            val collectionName = root.optString("name").takeIf { it.isNotBlank() } ?: fallbackName
            val inputFeatures = extractFeatures(root)
            if (inputFeatures.isEmpty()) return null

            // Compute centroid to anchor the MVT tile coordinate cleanly
            val allPoints = inputFeatures.flatMap { it.geoRingsOrLines }.flatten()
            val avgLat = allPoints.map { it.lat }.average()
            val avgLng = allPoints.map { it.lng }.average()
            val (tileX, tileY) = MvtMercator.latLngToTileIndex(avgLat, avgLng, DEFAULT_TILE_ZOOM)

            val mvtLayer = MvtInputLayer(
                name = collectionName,
                extent = 4096,
                features = inputFeatures
            )

            // Compile GeoJSON into Mapbox Vector Tile binary (.mvt / .pbf) bytes,
            // then decode via MvtParser to validate and produce the MvtTile rendered by OsmDroid.
            val mvtBytes = MvtEncoder.encodeTile(
                layers = listOf(mvtLayer),
                tileZ = DEFAULT_TILE_ZOOM,
                tileX = tileX,
                tileY = tileY
            )
            val parsedTile = MvtParser.parse(mvtBytes, DEFAULT_TILE_ZOOM, tileX, tileY)

            TacticalGeoJsonOverlay(
                id = id,
                name = formatDisplayName(collectionName),
                fileName = fileName,
                isEnabled = true,
                isCustomImported = isCustomImported,
                tileZ = DEFAULT_TILE_ZOOM,
                tileX = tileX,
                tileY = tileY,
                mvtByteSize = mvtBytes.size,
                mvtTile = parsedTile
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun extractFeatures(root: JSONObject): List<MvtInputFeature> {
        val type = root.optString("type")
        val results = mutableListOf<MvtInputFeature>()
        var nextId = 1L

        when (type) {
            "FeatureCollection" -> {
                val arr = root.optJSONArray("features") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val featObj = arr.optJSONObject(i) ?: continue
                    parseSingleFeature(featObj, nextId++)?.let { results.add(it) }
                }
            }
            "Feature" -> {
                parseSingleFeature(root, nextId)?.let { results.add(it) }
            }
        }
        return results
    }

    private fun parseSingleFeature(featObj: JSONObject, fallbackId: Long): MvtInputFeature? {
        val geom = featObj.optJSONObject("geometry") ?: return null
        val geomType = geom.optString("type")
        val coords = geom.optJSONArray("coordinates") ?: return null

        val propsObj = featObj.optJSONObject("properties")
        val props = LinkedHashMap<String, Any?>()
        if (propsObj != null) {
            val keys = propsObj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val v = propsObj.opt(k)
                if (v != null && v != JSONObject.NULL) {
                    props[k] = when (v) {
                        is Boolean, is Int, is Long, is Double, is Float, is String -> v
                        else -> v.toString()
                    }
                }
            }
        }

        val (mvtType, rings) = when (geomType) {
            "Point" -> {
                val pt = parseCoordPair(coords) ?: return null
                MvtGeometryType.POINT to listOf(listOf(pt))
            }
            "MultiPoint" -> {
                val pts = parseCoordList(coords)
                if (pts.isEmpty()) return null
                MvtGeometryType.POINT to listOf(pts)
            }
            "LineString" -> {
                val line = parseCoordList(coords)
                if (line.size < 2) return null
                MvtGeometryType.LINESTRING to listOf(line)
            }
            "MultiLineString" -> {
                val lines = mutableListOf<List<GeoPoint>>()
                for (i in 0 until coords.length()) {
                    coords.optJSONArray(i)?.let { arr ->
                        val line = parseCoordList(arr)
                        if (line.size >= 2) lines.add(line)
                    }
                }
                if (lines.isEmpty()) return null
                MvtGeometryType.LINESTRING to lines
            }
            "Polygon" -> {
                val ringsList = parsePolygonRings(coords)
                if (ringsList.isEmpty()) return null
                MvtGeometryType.POLYGON to ringsList
            }
            "MultiPolygon" -> {
                val allRings = mutableListOf<List<GeoPoint>>()
                for (i in 0 until coords.length()) {
                    coords.optJSONArray(i)?.let { polyArr ->
                        allRings.addAll(parsePolygonRings(polyArr))
                    }
                }
                if (allRings.isEmpty()) return null
                MvtGeometryType.POLYGON to allRings
            }
            else -> return null
        }

        return MvtInputFeature(
            id = fallbackId,
            type = mvtType,
            properties = props,
            geoRingsOrLines = rings
        )
    }

    private fun parsePolygonRings(polyCoords: JSONArray): List<List<GeoPoint>> {
        val rings = mutableListOf<List<GeoPoint>>()
        for (i in 0 until polyCoords.length()) {
            polyCoords.optJSONArray(i)?.let { ringArr ->
                val pts = parseCoordList(ringArr)
                if (pts.size >= 3) rings.add(pts)
            }
        }
        return rings
    }

    private fun parseCoordList(arr: JSONArray): List<GeoPoint> {
        val pts = mutableListOf<GeoPoint>()
        for (i in 0 until arr.length()) {
            arr.optJSONArray(i)?.let { pair ->
                parseCoordPair(pair)?.let { pts.add(it) }
            }
        }
        return pts
    }

    private fun parseCoordPair(arr: JSONArray): GeoPoint? {
        if (arr.length() < 2) return null
        val lng = arr.optDouble(0, Double.NaN)
        val lat = arr.optDouble(1, Double.NaN)
        if (lat.isNaN() || lng.isNaN()) return null
        return GeoPoint(lat = lat, lng = lng)
    }

    private fun formatDisplayName(raw: String): String {
        return raw.substringBeforeLast('.')
            .replace('_', ' ')
            .split(' ')
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
    }
}
