package com.example.ui.map

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import com.example.data.local.BreadcrumbEntity
import com.example.data.local.SosBroadcastEntity
import com.example.model.*
import com.example.service.GeoJsonThreatPolygon
import com.example.ui.map.mvt.MvtEncoder
import com.example.ui.map.mvt.MvtInputFeature
import com.example.ui.map.mvt.MvtInputLayer
import com.example.ui.map.mvt.TacticalGeoJsonOverlay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.MapTileIndex
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.FilterOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.tan

data class TacticalBackupSnapshot(
    val bounds: BoundingBox,
    val userLat: Double,
    val userLng: Double,
    val mapMode: MapVisualMode,
    val freeMapProvider: FreeMapProvider,
    val threatZones: List<ThreatZone>,
    val heatmapPoints: List<ThreatHeatmapPoint>,
    val safeHavens: List<SafeHaven>,
    val cameras: List<SurveillanceCamera>,
    val geoJsonThreatPolygons: List<GeoJsonThreatPolygon>,
    val geoJsonOverlays: List<TacticalGeoJsonOverlay>,
    val activeRoute: TacticalRoute?,
    val breadcrumbs: List<BreadcrumbEntity>,
    val sosBroadcasts: List<SosBroadcastEntity>,
    val liveIncidents: List<LiveIncident>,
    val intelSources: List<IntelSource>
)

data class OfflineBackupSummary(
    val archiveName: String,
    val archiveSizeBytes: Long,
    val tilesExportedCount: Int,
    val totalCacheTilesCount: Int,
    val geoJsonPolygonsCount: Int,
    val mvtOverlaysCount: Int,
    val threatZonesCount: Int,
    val safeHavensCount: Int,
    val camerasCount: Int,
    val breadcrumbsCount: Int,
    val sosBroadcastsCount: Int,
    val routeIncluded: Boolean,
    val localAppBackupPath: String? = null,
    val exportedUriString: String? = null,
    val timestampMs: Long = System.currentTimeMillis()
) {
    val archiveSizeKb: Double get() = archiveSizeBytes / 1024.0
    val archiveSizeMb: Double get() = archiveSizeBytes / (1024.0 * 1024.0)
}

data class BackupExportProgress(
    val isExporting: Boolean = false,
    val statusMessage: String = "READY TO EXPORT",
    val lastSummary: OfflineBackupSummary? = null,
    val errorMessage: String? = null
)

data class LocalBackupFileInfo(
    val fileName: String,
    val filePath: String,
    val sizeBytes: Long,
    val lastModifiedMs: Long
) {
    val sizeKb: Double get() = sizeBytes / 1024.0
    val sizeMb: Double get() = sizeBytes / (1024.0 * 1024.0)
}

private class ByteCountingOutputStream(delegate: OutputStream) : FilterOutputStream(delegate) {
    var bytesWritten: Long = 0L
        private set

    override fun write(b: Int) {
        out.write(b)
        bytesWritten += 1
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        out.write(b, off, len)
        bytesWritten += len
    }
}

/**
 * Exports the currently cached OsmDroid map area tiles (from SQLite `cache.db`) together with
 * all active tactical overlays (GeoJSON threat polygons, compiled MVT vector tiles, safe zones,
 * CCTV exposure nodes, heatmap intensities, A* evacuation route, 50m movement breadcrumbs, and
 * SOS emergency logs) into a self-contained `.zip` emergency offline backup archive.
 */
object OfflineMapBackupExporter {

    private const val BACKUP_SUBDIR = "backups"
    private const val MAX_EXPORT_TILES = 2500

    fun defaultBackupFileName(timestampMs: Long = System.currentTimeMillis()): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(timestampMs))
        return "aegis_eye_offline_backup_$stamp.zip"
    }

    /**
     * Checks whether a cached OsmDroid [tileIndex] falls inside [bounds] (with a slight edge buffer
     * so boundary tiles partially visible in the map viewport are preserved).
     */
    fun isTileInsideBounds(
        tileIndex: Long,
        bounds: BoundingBox,
        edgeMarginFraction: Double = 0.05
    ): Boolean {
        val z = MapTileIndex.getZoom(tileIndex)
        val x = MapTileIndex.getX(tileIndex)
        val y = MapTileIndex.getY(tileIndex)
        if (z < 0 || z > 22) return false

        val latSpan = max(bounds.latNorth - bounds.latSouth, 0.01)
        val lonSpan = max(bounds.lonEast - bounds.lonWest, 0.01)
        val north = (bounds.latNorth + latSpan * edgeMarginFraction).coerceIn(-85.05112878, 85.05112878)
        val south = (bounds.latSouth - latSpan * edgeMarginFraction).coerceIn(-85.05112878, 85.05112878)
        val west = (bounds.lonWest - lonSpan * edgeMarginFraction).coerceIn(-180.0, 180.0)
        val east = (bounds.lonEast + lonSpan * edgeMarginFraction).coerceIn(-180.0, 180.0)

        val (x1, y1) = latLngToTileXY(north, west, z)
        val (x2, y2) = latLngToTileXY(south, east, z)
        val minX = min(x1, x2)
        val maxX = max(x1, x2)
        val minY = min(y1, y2)
        val maxY = max(y1, y2)

        return x in minX..maxX && y in minY..maxY
    }

    private fun latLngToTileXY(lat: Double, lng: Double, zoom: Int): Pair<Int, Int> {
        val n = 2.0.pow(zoom)
        val clampedLat = lat.coerceIn(-85.05112878, 85.05112878)
        val latRad = Math.toRadians(clampedLat)
        val maxCoord = (n.toInt() - 1).coerceAtLeast(0)
        val x = floor(((lng + 180.0) / 360.0) * n).toInt().coerceIn(0, maxCoord)
        val y = floor(((1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0) * n)
            .toInt().coerceIn(0, maxCoord)
        return x to y
    }

    /**
     * Builds an RFC 7946 GeoJSON FeatureCollection containing all tactical overlays, polygons,
     * safe havens, cameras, heatmap nodes, active A* route, breadcrumbs, and SOS logs.
     */
    fun buildOverlayGeoJsonString(snapshot: TacticalBackupSnapshot): String {
        val features = JSONArray()

        // 1. GeoJSON Threat Polygons (with inner safe-corridor holes preserved)
        for (poly in snapshot.geoJsonThreatPolygons) {
            if (poly.outerRing.size < 3) continue
            val ringsArr = JSONArray()
            ringsArr.put(ringToJsonArray(poly.outerRing, ensureClosed = true))
            for (hole in poly.holes) {
                if (hole.size >= 3) {
                    ringsArr.put(ringToJsonArray(hole, ensureClosed = true))
                }
            }
            val props = JSONObject().apply {
                put("layer", "geojson_threat_polygon")
                put("id", poly.id)
                put("name", poly.name)
                put("sector_code", poly.sectorCode)
                put("threat_level", poly.threatLevel.name)
                put("category", poly.category)
                put("intensity", poly.intensity.toDouble())
                put("casualty_risk", poly.casualtyRisk)
                put("bypass_buffer_km", poly.bypassBufferKm)
                put("source_file", poly.sourceFileName)
                put("stroke", poly.strokeColorHex)
                put("fill", poly.fillColorHex)
                put("enabled", poly.isEnabled)
                put("notes", poly.notes)
            }
            features.put(
                JSONObject().apply {
                    put("type", "Feature")
                    put("properties", props)
                    put(
                        "geometry",
                        JSONObject().apply {
                            put("type", "Polygon")
                            put("coordinates", ringsArr)
                        }
                    )
                }
            )
        }

        // 2. Threat Zones
        for (zone in snapshot.threatZones) {
            features.put(
                pointFeature(
                    lat = zone.centerLat,
                    lng = zone.centerLng,
                    props = JSONObject().apply {
                        put("layer", "threat_zone")
                        put("id", zone.id)
                        put("name", zone.name)
                        put("sector_code", zone.sectorCode)
                        put("threat_level", zone.level.name)
                        put("threat_type", zone.type.name)
                        put("radius_km", zone.radiusKm)
                        put("casualty_risk", zone.casualtyRisk)
                        put("bypass_buffer_km", zone.bypassBufferKm)
                        put("description", zone.description)
                    }
                )
            )
        }

        // 3. Safe Havens & Designated Walking Safe Zones
        for (haven in snapshot.safeHavens) {
            features.put(
                pointFeature(
                    lat = haven.lat,
                    lng = haven.lng,
                    props = JSONObject().apply {
                        put("layer", "safe_haven")
                        put("id", haven.id)
                        put("name", haven.name)
                        put("country_or_region", haven.countryOrRegion)
                        put("type", haven.type.name)
                        put("border_status", haven.borderStatus)
                        put("clearance_level", haven.clearanceLevel)
                        put("contact_freq", haven.contactFreq)
                        put("is_walking_safe_zone", haven.isDesignatedWalkingSafeZone)
                        put("advisories", haven.advisories)
                    }
                )
            )
        }

        // 4. OSINT & State Surveillance Cameras
        for (cam in snapshot.cameras) {
            features.put(
                pointFeature(
                    lat = cam.lat,
                    lng = cam.lng,
                    props = JSONObject().apply {
                        put("layer", "surveillance_camera")
                        put("id", cam.id)
                        put("name", cam.name)
                        put("operator_type", cam.operatorType.name)
                        put("vulnerability_status", cam.vulnerabilityStatus.name)
                        put("coverage_radius_meters", cam.coverageRadiusMeters)
                        put("azimuth_degrees", cam.azimuthDegrees.toDouble())
                        put("fov_angle_degrees", cam.fovAngleDegrees.toDouble())
                        put("stream_protocol", cam.streamProtocol)
                    }
                )
            )
        }

        // 5. Threat Heatmap Intensity Points
        for (hp in snapshot.heatmapPoints) {
            features.put(
                pointFeature(
                    lat = hp.lat,
                    lng = hp.lng,
                    props = JSONObject().apply {
                        put("layer", "threat_heatmap_point")
                        put("id", hp.id)
                        put("label", hp.label)
                        put("intensity", hp.intensity.toDouble())
                        put("radius_km", hp.radiusKm)
                        put("category", hp.category.name)
                    }
                )
            )
        }

        // 6. Active Safe Walking / Evacuation Route
        snapshot.activeRoute?.let { route ->
            if (route.path.size >= 2) {
                features.put(
                    JSONObject().apply {
                        put("type", "Feature")
                        put(
                            "properties",
                            JSONObject().apply {
                                put("layer", "active_route")
                                put("id", route.id)
                                put("name", route.name)
                                put("origin", route.origin)
                                put("destination", route.destination)
                                put("distance_km", route.distanceKm)
                                put("risk_score", route.riskScore)
                                put("is_walking_route", route.isWalkingRoute)
                                put("est_walk_minutes", route.estWalkMinutes)
                                put("cctv_cameras_avoided", route.cctvCamerasAvoided)
                            }
                        )
                        put(
                            "geometry",
                            JSONObject().apply {
                                put("type", "LineString")
                                put("coordinates", ringToJsonArray(route.path, ensureClosed = false))
                            }
                        )
                    }
                )
            }
        }

        // 7. 50-Meter Temporary Movement Breadcrumbs (track LineString + individual markers)
        if (snapshot.breadcrumbs.size >= 2) {
            val pts = snapshot.breadcrumbs.map { GeoPoint(it.lat, it.lng) }
            features.put(
                JSONObject().apply {
                    put("type", "Feature")
                    put(
                        "properties",
                        JSONObject().apply {
                            put("layer", "breadcrumb_track")
                            put("marker_count", snapshot.breadcrumbs.size)
                        }
                    )
                    put(
                        "geometry",
                        JSONObject().apply {
                            put("type", "LineString")
                            put("coordinates", ringToJsonArray(pts, ensureClosed = false))
                        }
                    )
                }
            )
        }
        for (crumb in snapshot.breadcrumbs) {
            features.put(
                pointFeature(
                    lat = crumb.lat,
                    lng = crumb.lng,
                    props = JSONObject().apply {
                        put("layer", "breadcrumb_marker")
                        put("sequence_number", crumb.sequenceNumber)
                        put("speed_kmh", crumb.speedKmh.toDouble())
                        put("bearing_degrees", crumb.bearingDegrees.toDouble())
                        put("distance_from_prev_meters", crumb.distanceFromPreviousMeters)
                        put("timestamp_ms", crumb.timestampMs)
                        put("is_temporary", crumb.isTemporary)
                    }
                )
            )
        }

        // 8. Saved SOS Emergency Broadcasts
        for (sos in snapshot.sosBroadcasts) {
            features.put(
                pointFeature(
                    lat = sos.lat,
                    lng = sos.lng,
                    props = JSONObject().apply {
                        put("layer", "sos_broadcast")
                        put("id", sos.id)
                        put("callsign", sos.callsign)
                        put("emergency_message", sos.emergencyMessage)
                        put("online_sync_status", sos.onlineSyncStatus)
                        put("yellow_page_endpoint", sos.yellowPageEndpoint)
                        put("timestamp_ms", sos.timestampMs)
                    }
                )
            )
        }

        return JSONObject().apply {
            put("type", "FeatureCollection")
            put("name", "Aegis Eye Emergency Offline Overlay Backup")
            put("features", features)
        }.toString(2)
    }

    fun buildStateSnapshotJson(
        snapshot: TacticalBackupSnapshot,
        exportedTilesCount: Int,
        totalCacheTilesCount: Int,
        timestampMs: Long
    ): String {
        val boundsJson = JSONObject().apply {
            put("north", snapshot.bounds.latNorth)
            put("south", snapshot.bounds.latSouth)
            put("east", snapshot.bounds.lonEast)
            put("west", snapshot.bounds.lonWest)
        }

        val incidentsArr = JSONArray().apply {
            snapshot.liveIncidents.forEach { inc ->
                put(
                    JSONObject().apply {
                        put("id", inc.id)
                        put("sector", inc.sector)
                        put("title", inc.title)
                        put("description", inc.description)
                        put("severity", inc.severity.name)
                        put("source_agency", inc.sourceAgency)
                        put("lat", inc.lat)
                        put("lng", inc.lng)
                    }
                )
            }
        }

        val sourcesArr = JSONArray().apply {
            snapshot.intelSources.forEach { src ->
                put(
                    JSONObject().apply {
                        put("id", src.id)
                        put("name", src.name)
                        put("type", src.type.name)
                        put("endpoint_url", src.endpointUrl)
                        put("frequency_mhz", src.frequencyMhz)
                        put("enabled", src.isEnabled)
                        put("status", src.status)
                    }
                )
            }
        }

        val mvtMetaArr = JSONArray().apply {
            snapshot.geoJsonOverlays.forEach { ov ->
                put(
                    JSONObject().apply {
                        put("id", ov.id)
                        put("name", ov.name)
                        put("file_name", ov.fileName)
                        put("enabled", ov.isEnabled)
                        put("tile_z", ov.tileZ)
                        put("tile_x", ov.tileX)
                        put("tile_y", ov.tileY)
                        put("feature_count", ov.featureCount)
                        put("mvt_byte_size", ov.mvtByteSize)
                    }
                )
            }
        }

        return JSONObject().apply {
            put("format_version", 1)
            put("exported_at_ms", timestampMs)
            put("map_area_bounds", boundsJson)
            put("user_position", JSONObject().apply {
                put("lat", snapshot.userLat)
                put("lng", snapshot.userLng)
            })
            put("map_mode", snapshot.mapMode.name)
            put("tile_provider", snapshot.freeMapProvider.name)
            put("tiles_exported_in_area", exportedTilesCount)
            put("total_tiles_in_disk_cache", totalCacheTilesCount)
            put("mvt_overlays", mvtMetaArr)
            put("live_incidents", incidentsArr)
            put("intel_sources", sourcesArr)
            put("active_route_directives", JSONArray(snapshot.activeRoute?.tacticalDirectives ?: emptyList<String>()))
        }.toString(2)
    }

    suspend fun exportToAppBackupFile(
        context: Context,
        snapshot: TacticalBackupSnapshot
    ): OfflineBackupSummary = withContext(Dispatchers.IO) {
        OsmTileDiskCacheManager.configureCachePolicy(context)
        val backupsDir = File(context.filesDir, BACKUP_SUBDIR).apply { mkdirs() }
        val fileName = defaultBackupFileName()
        val outFile = File(backupsDir, fileName)

        val summary = FileOutputStream(outFile).use { fos ->
            exportToOutputStream(
                context = context,
                outputStream = fos,
                snapshot = snapshot,
                archiveName = fileName
            )
        }
        summary.copy(localAppBackupPath = outFile.absolutePath)
    }

    suspend fun exportToDocumentUri(
        context: Context,
        uri: Uri,
        snapshot: TacticalBackupSnapshot,
        archiveName: String = defaultBackupFileName()
    ): OfflineBackupSummary = withContext(Dispatchers.IO) {
        OsmTileDiskCacheManager.configureCachePolicy(context)
        val stream = context.contentResolver.openOutputStream(uri)
            ?: throw IllegalStateException("Unable to open destination file stream")

        val summary = stream.use { out ->
            exportToOutputStream(
                context = context,
                outputStream = out,
                snapshot = snapshot,
                archiveName = archiveName
            )
        }
        summary.copy(exportedUriString = uri.toString())
    }

    suspend fun listLocalBackups(context: Context): List<LocalBackupFileInfo> = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, BACKUP_SUBDIR)
        if (!dir.exists()) return@withContext emptyList()
        dir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".zip", ignoreCase = true) }
            ?.sortedByDescending { it.lastModified() }
            ?.map {
                LocalBackupFileInfo(
                    fileName = it.name,
                    filePath = it.absolutePath,
                    sizeBytes = it.length(),
                    lastModifiedMs = it.lastModified()
                )
            }
            ?: emptyList()
    }

    suspend fun deleteLocalBackup(filePath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            File(filePath).delete()
        } catch (_: Exception) {
            false
        }
    }

    fun exportToOutputStream(
        context: Context,
        outputStream: OutputStream,
        snapshot: TacticalBackupSnapshot,
        archiveName: String
    ): OfflineBackupSummary {
        val nowMs = System.currentTimeMillis()
        val counter = ByteCountingOutputStream(outputStream)
        val exportedTileEntries = JSONArray()
        var exportedTilesCount = 0
        var totalCacheTilesCount = 0

        ZipOutputStream(BufferedOutputStream(counter)).use { zip ->
            // 1. Export cached OsmDroid map tiles inside the current viewport bounds from SQLite cache.db
            val cacheDir = Configuration.getInstance().osmdroidTileCache
                ?: File(context.cacheDir, "osmdroid/tiles")
            val cacheDbFile = File(cacheDir, "cache.db")
            if (cacheDbFile.exists() && cacheDbFile.length() > 0L) {
                try {
                    SQLiteDatabase.openDatabase(
                        cacheDbFile.absolutePath,
                        null,
                        SQLiteDatabase.OPEN_READONLY
                    ).use { db ->
                        db.rawQuery("SELECT COUNT(*) FROM tiles", null).use { c ->
                            if (c.moveToFirst()) totalCacheTilesCount = c.getInt(0)
                        }

                        db.rawQuery("SELECT key, provider, tile, expires FROM tiles", null).use { cursor ->
                            val keyIdx = cursor.getColumnIndexOrThrow("key")
                            val providerIdx = cursor.getColumnIndexOrThrow("provider")
                            val tileIdx = cursor.getColumnIndexOrThrow("tile")
                            val writtenPaths = HashSet<String>()

                            while (cursor.moveToNext() && exportedTilesCount < MAX_EXPORT_TILES) {
                                val tileKey = cursor.getLong(keyIdx)
                                if (!isTileInsideBounds(tileKey, snapshot.bounds)) continue

                                val bytes = cursor.getBlob(tileIdx) ?: continue
                                if (bytes.isEmpty()) continue

                                val provider = sanitizeSegment(cursor.getString(providerIdx) ?: "osmdroid")
                                val z = MapTileIndex.getZoom(tileKey)
                                val x = MapTileIndex.getX(tileKey)
                                val y = MapTileIndex.getY(tileKey)
                                val entryPath = "tiles/$provider/$z/$x/$y.png"
                                if (!writtenPaths.add(entryPath)) continue

                                zip.putNextEntry(ZipEntry(entryPath))
                                zip.write(bytes)
                                zip.closeEntry()

                                exportedTilesCount += 1
                                exportedTileEntries.put(
                                    JSONObject().apply {
                                        put("provider", provider)
                                        put("z", z)
                                        put("x", x)
                                        put("y", y)
                                        put("bytes", bytes.size)
                                    }
                                )
                            }
                        }
                    }
                } catch (_: Exception) {
                    // Continue exporting overlays cleanly if SQLite cache is locked or empty
                }
            }

            // 2. Write tile index metadata
            val tileIndexJson = JSONObject().apply {
                put("bounds_north", snapshot.bounds.latNorth)
                put("bounds_south", snapshot.bounds.latSouth)
                put("bounds_east", snapshot.bounds.lonEast)
                put("bounds_west", snapshot.bounds.lonWest)
                put("exported_tiles_count", exportedTilesCount)
                put("total_disk_cache_tiles", totalCacheTilesCount)
                put("tiles", exportedTileEntries)
            }.toString(2)
            writeTextEntry(zip, "tiles/tile_index.json", tileIndexJson)

            // 3. Export GeoJSON FeatureCollection covering all tactical overlays
            writeTextEntry(zip, "overlays/tactical_overlays.geojson", buildOverlayGeoJsonString(snapshot))

            // 4. Export raw compiled Mapbox Vector Tile (.mvt) binaries for every GeoJSON overlay
            for (overlay in snapshot.geoJsonOverlays) {
                val inputLayers = overlay.mvtTile.layers.map { layer ->
                    MvtInputLayer(
                        name = layer.name,
                        extent = layer.extent,
                        features = layer.features.map { feat ->
                            MvtInputFeature(
                                id = feat.id,
                                type = feat.type,
                                properties = feat.properties,
                                geoRingsOrLines = feat.geoGeometries
                            )
                        }
                    )
                }
                if (inputLayers.isNotEmpty()) {
                    val mvtBytes = MvtEncoder.encodeTile(
                        layers = inputLayers,
                        tileZ = overlay.tileZ,
                        tileX = overlay.tileX,
                        tileY = overlay.tileY
                    )
                    val safeId = sanitizeSegment(overlay.id)
                    val entryPath = "overlays/mvt/${safeId}_z${overlay.tileZ}_${overlay.tileX}_${overlay.tileY}.mvt"
                    zip.putNextEntry(ZipEntry(entryPath))
                    zip.write(mvtBytes)
                    zip.closeEntry()
                }
            }

            // 5. Export full tactical JSON state snapshot
            val stateJson = buildStateSnapshotJson(
                snapshot = snapshot,
                exportedTilesCount = exportedTilesCount,
                totalCacheTilesCount = totalCacheTilesCount,
                timestampMs = nowMs
            )
            writeTextEntry(zip, "overlays/tactical_state_snapshot.json", stateJson)

            // 6. Export backup manifest
            val manifestJson = JSONObject().apply {
                put("app", "Aegis Eye")
                put("backup_type", "OSMDROID_AREA_AND_TACTICAL_OVERLAYS")
                put("archive_name", archiveName)
                put("exported_at_ms", nowMs)
                put("tiles_exported_count", exportedTilesCount)
                put("total_disk_cache_tiles", totalCacheTilesCount)
                put("geojson_threat_polygons_count", snapshot.geoJsonThreatPolygons.size)
                put("mvt_overlays_count", snapshot.geoJsonOverlays.size)
                put("threat_zones_count", snapshot.threatZones.size)
                put("safe_havens_count", snapshot.safeHavens.size)
                put("cameras_count", snapshot.cameras.size)
                put("breadcrumbs_count", snapshot.breadcrumbs.size)
                put("sos_broadcasts_count", snapshot.sosBroadcasts.size)
                put("route_included", snapshot.activeRoute != null)
            }.toString(2)
            writeTextEntry(zip, "manifest.json", manifestJson)

            zip.finish()
        }

        return OfflineBackupSummary(
            archiveName = archiveName,
            archiveSizeBytes = counter.bytesWritten,
            tilesExportedCount = exportedTilesCount,
            totalCacheTilesCount = totalCacheTilesCount,
            geoJsonPolygonsCount = snapshot.geoJsonThreatPolygons.size,
            mvtOverlaysCount = snapshot.geoJsonOverlays.size,
            threatZonesCount = snapshot.threatZones.size,
            safeHavensCount = snapshot.safeHavens.size,
            camerasCount = snapshot.cameras.size,
            breadcrumbsCount = snapshot.breadcrumbs.size,
            sosBroadcastsCount = snapshot.sosBroadcasts.size,
            routeIncluded = snapshot.activeRoute != null,
            timestampMs = nowMs
        )
    }

    private fun writeTextEntry(zip: ZipOutputStream, path: String, content: String) {
        zip.putNextEntry(ZipEntry(path))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun pointFeature(lat: Double, lng: Double, props: JSONObject): JSONObject {
        return JSONObject().apply {
            put("type", "Feature")
            put("properties", props)
            put(
                "geometry",
                JSONObject().apply {
                    put("type", "Point")
                    put("coordinates", JSONArray().apply {
                        put(lng)
                        put(lat)
                    })
                }
            )
        }
    }

    private fun ringToJsonArray(points: List<GeoPoint>, ensureClosed: Boolean): JSONArray {
        val arr = JSONArray()
        points.forEach { pt ->
            arr.put(JSONArray().apply {
                put(pt.lng)
                put(pt.lat)
            })
        }
        if (ensureClosed && points.size >= 3) {
            val first = points.first()
            val last = points.last()
            if (first.lat != last.lat || first.lng != last.lng) {
                arr.put(JSONArray().apply {
                    put(first.lng)
                    put(first.lat)
                })
            }
        }
        return arr
    }

    private fun sanitizeSegment(raw: String): String {
        return raw.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "layer" }
    }
}
