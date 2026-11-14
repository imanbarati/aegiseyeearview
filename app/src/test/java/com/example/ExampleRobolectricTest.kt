package com.example

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.TacticalDataProvider
import com.example.data.local.AegisLocalDatabase
import com.example.data.local.BreadcrumbEntity
import com.example.data.local.SosBroadcastEntity
import com.example.model.MapVisualMode
import com.example.model.ThreatLevel
import com.example.routing.TacticalPathfinder
import com.example.service.GeoJsonThreatZoneService
import com.example.service.ThreatGeofenceEvaluator
import com.example.service.ThreatProximityBus
import com.example.ui.map.FreeMapProvider
import com.example.ui.map.OfflineMapBackupExporter
import com.example.ui.map.OsmDroidConfig
import com.example.ui.map.OsmTileDiskCacheManager
import com.example.ui.map.TacticalBackupSnapshot
import com.example.ui.map.mvt.GeoJsonTacticalLoader
import com.example.ui.map.mvt.MvtGeometryType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.osmdroid.config.Configuration
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.MapTileIndex
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipInputStream
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Aegis Eye", appName)
  }

  @Test
  fun `bundled tactical geojson assets compile to MVT tiles`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val overlays = GeoJsonTacticalLoader.loadBundledOverlays(context)
    assertEquals(4, overlays.size)
    assertTrue("All overlays should contain parsed MVT features", overlays.all { it.featureCount >= 3 })
    assertTrue("All overlays should produce non-empty MVT binary bytes", overlays.all { it.mvtByteSize > 0 })
  }

  @Test
  fun `custom geojson compiles to valid MVT tile`() {
    val sampleGeoJson = """
      {
        "type": "FeatureCollection",
        "name": "test_sector_overlay",
        "features": [
          {
            "type": "Feature",
            "properties": { "name": "Test Poly", "sector_code": "TST-01", "threat_level": "HIGH" },
            "geometry": {
              "type": "Polygon",
              "coordinates": [[[51.37, 35.68], [51.40, 35.68], [51.40, 35.71], [51.37, 35.71], [51.37, 35.68]]]
            }
          },
          {
            "type": "Feature",
            "properties": { "name": "Test Beacon", "sector_code": "BCN-01" },
            "geometry": { "type": "Point", "coordinates": [51.39, 35.70] }
          }
        ]
      }
    """.trimIndent()

    val overlay = GeoJsonTacticalLoader.parseGeoJsonToMvtOverlay(
      id = "test_overlay",
      fallbackName = "Test Overlay",
      fileName = "test_sector_overlay.geojson",
      rawJson = sampleGeoJson,
      isCustomImported = true
    )

    assertNotNull("GeoJSON should compile cleanly into MVT overlay", overlay)
    assertEquals(2, overlay!!.featureCount)
    assertTrue("MVT binary size must be positive", overlay.mvtByteSize > 0)
    assertEquals(MvtGeometryType.POLYGON, overlay.mvtTile.layers.first().features[0].type)
    assertEquals(MvtGeometryType.POINT, overlay.mvtTile.layers.first().features[1].type)
  }

  @Test
  fun `osmdroid initializes strictly inside private app directories`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    OsmDroidConfig.ensureInitialized(context)
    val basePath = org.osmdroid.config.Configuration.getInstance().osmdroidBasePath
    assertTrue(
      "OsmDroid base path must stay inside app filesDir",
      basePath.absolutePath.startsWith(context.filesDir.absolutePath)
    )
  }

  @Test
  fun `geojson threat zone service loads all bundled threat polygons`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val polygons = GeoJsonThreatZoneService.loadThreatPolygonsFromAssets(context)
    // tehran_exclusion_sectors.geojson (3) + iran_theater_threat_zones.geojson (4)
    assertEquals(7, polygons.size)
    assertTrue("Every polygon should compute positive area", polygons.all { it.approxAreaKm2 > 0.0 })
    assertTrue("Every polygon should map cleanly to a ThreatZone", polygons.all { it.toThreatZone().radiusKm >= 3.0 })
  }

  @Test
  fun `geojson threat zone service parses MultiPolygon and inner holes`() {
    val geoJson = """
      {
        "type": "FeatureCollection",
        "features": [
          {
            "type": "Feature",
            "properties": {
              "name": "Composite Exclusion Belt",
              "sector_code": "CMP-9",
              "threat_level": "CRITICAL",
              "category": "AIR_DEFENSE",
              "bypass_buffer_km": 28
            },
            "geometry": {
              "type": "MultiPolygon",
              "coordinates": [
                [
                  [[51.30, 35.60], [51.50, 35.60], [51.50, 35.80], [51.30, 35.80], [51.30, 35.60]],
                  [[51.36, 35.66], [51.44, 35.66], [51.44, 35.74], [51.36, 35.74], [51.36, 35.66]]
                ],
                [
                  [[51.60, 35.70], [51.70, 35.70], [51.70, 35.78], [51.60, 35.78], [51.60, 35.70]]
                ]
              ]
            }
          }
        ]
      }
    """.trimIndent()

    val parsed = GeoJsonThreatZoneService.parseThreatPolygons(geoJson, "composite.geojson")
    assertEquals(2, parsed.size)
    assertEquals(ThreatLevel.CRITICAL_ALPHA, parsed[0].threatLevel)
    assertEquals(1, parsed[0].holes.size)
    assertEquals("CMP-9", parsed[0].sectorCode)
    assertEquals(28.0, parsed[0].bypassBufferKm, 0.01)
  }

  @Test
  fun `osmdroid disk cache manager configures policy inside private cacheDir`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    OsmTileDiskCacheManager.configureCachePolicy(context, maxCacheMb = 250L)
    val stats = OsmTileDiskCacheManager.inspectCache(context)
    assertEquals(250.0, stats.maxMb, 0.1)
    assertEquals(30, stats.retentionDays)
    assertTrue(
      "Tile cache path must stay in app private cacheDir",
      stats.cachePath.startsWith(context.cacheDir.absolutePath)
    )
  }

  @Test
  fun `threat proximity bus evaluates bundled high-intensity geojson zones and deduplicates re-entry`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val polygons = GeoJsonThreatZoneService.loadThreatPolygonsFromAssets(context)
    ThreatProximityBus.updatePolygons(polygons)

    // Safe point outside high-intensity sectors
    assertFalse(ThreatProximityBus.evaluateCoordinates(35.8600, 51.4500))
    assertEquals(null, ThreatProximityBus.activeGeofenceAlert.value)

    // Enter Central Command Armored Cordon (MVT-SEC-02, HIGH_BRAVO)
    val firstEntry = ThreatProximityBus.evaluateCoordinates(35.6892, 51.3890)
    assertTrue("First entry into high-intensity GeoJSON polygon must trigger new alert", firstEntry)
    val alert = ThreatProximityBus.activeGeofenceAlert.value
    assertNotNull(alert)
    assertEquals("MVT-SEC-02", alert!!.sectorCode)

    // Staying inside the same polygon must not re-fire haptic waveform repeatedly
    val repeatStay = ThreatProximityBus.evaluateCoordinates(35.6900, 51.3910)
    assertFalse("Repeated coordinates inside same polygon must deduplicate haptic trigger", repeatStay)

    // Exit polygon resets trigger, re-entering fires again
    ThreatProximityBus.evaluateCoordinates(35.8600, 51.4500)
    assertTrue(ThreatProximityBus.evaluateCoordinates(35.6892, 51.3890))
  }

  @Test
  fun `osmdroid applies custom gods eye night mode filters and dark loading grid`() {
    assertNotNull(OsmDroidConfig.createNightModeColorMatrix(MapVisualMode.TACTICAL_HUD, FreeMapProvider.CARTO_DARK))
    assertNotNull(OsmDroidConfig.createNightModeColorMatrix(MapVisualMode.TACTICAL_HUD, FreeMapProvider.OSM_STANDARD))
    assertNotNull(OsmDroidConfig.createNightModeColorMatrix(MapVisualMode.TACTICAL_HUD, FreeMapProvider.ESRI_SATELLITE))
    assertNotNull(OsmDroidConfig.createNightModeColorMatrix(MapVisualMode.SATELLITE_RECON, FreeMapProvider.ESRI_SATELLITE))
    assertNotNull(OsmDroidConfig.createNightModeColorMatrix(MapVisualMode.FLIR_THERMAL, FreeMapProvider.CARTO_DARK))

    val osmDarkArr = OsmDroidConfig.createNightModeColorMatrix(
      MapVisualMode.TACTICAL_HUD,
      FreeMapProvider.OSM_STANDARD
    )!!.array
    // Verify negative diagonal inversion coefficients convert daylight tiles into obsidian dark mode
    assertTrue("OSM Standard night matrix must invert RGB channels", osmDarkArr[0] < 0f && osmDarkArr[6] < 0f && osmDarkArr[12] < 0f)
  }

  @Test
  fun `room database persists sos broadcasts and prunes expired temporary breadcrumbs`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = Room.inMemoryDatabaseBuilder(context, AegisLocalDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    try {
      val sosDao = db.sosBroadcastDao()
      val breadcrumbDao = db.breadcrumbDao()

      val insertedId = sosDao.insertBroadcast(
        SosBroadcastEntity(
          callsign = "ALPHA-7",
          lat = 35.6892,
          lng = 51.3890,
          accuracyMeters = 5.0f,
          emergencyMessage = "Trapped near Sector North",
          yellowPageEndpoint = "https://example.org/sos",
          onlineSyncStatus = "SAVED LOCAL DB • PUBLISHING ONLINE...",
          onlineRecordUrl = null
        )
      )
      sosDao.updateOnlineStatus(insertedId, "PUBLISHED ONLINE (HTTP 201)", "https://example.org/sos")

      val broadcasts = sosDao.observeAllBroadcasts().first()
      assertEquals(1, broadcasts.size)
      assertEquals("ALPHA-7", broadcasts.first().callsign)
      assertEquals("PUBLISHED ONLINE (HTTP 201)", broadcasts.first().onlineSyncStatus)

      val now = System.currentTimeMillis()
      breadcrumbDao.insertBreadcrumb(
        BreadcrumbEntity(
          sequenceNumber = 1,
          lat = 35.6892,
          lng = 51.3890,
          speedKmh = 4.5f,
          bearingDegrees = 0f,
          distanceFromPreviousMeters = 0.0,
          timestampMs = now - 3 * 60 * 60 * 1000L, // expired (> 2h)
          isTemporary = true
        )
      )
      breadcrumbDao.insertBreadcrumb(
        BreadcrumbEntity(
          sequenceNumber = 2,
          lat = 35.6898,
          lng = 51.3890,
          speedKmh = 4.8f,
          bearingDegrees = 10f,
          distanceFromPreviousMeters = 66.7,
          timestampMs = now - 60_000L, // recent
          isTemporary = true
        )
      )

      breadcrumbDao.deleteExpiredTemporary(cutoffTimestampMs = now - 2 * 60 * 60 * 1000L)
      val remaining = breadcrumbDao.observeAllBreadcrumbs().first()
      assertEquals(1, remaining.size)
      assertEquals(2, remaining.first().sequenceNumber)
    } finally {
      db.close()
    }
  }

  @Test
  fun `offline backup exporter archives current map area tiles and overlay data into local zip`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    OsmTileDiskCacheManager.configureCachePolicy(context)
    val cacheDir = Configuration.getInstance().osmdroidTileCache ?: File(context.cacheDir, "osmdroid/tiles")
    cacheDir.mkdirs()
    val cacheDbFile = File(cacheDir, "cache.db")
    if (cacheDbFile.exists()) cacheDbFile.delete()

    val inAreaTileKey = MapTileIndex.getTileIndex(10, 658, 403)
    val outAreaTileKey = MapTileIndex.getTileIndex(10, 100, 200)
    SQLiteDatabase.openOrCreateDatabase(cacheDbFile, null).use { db ->
      db.execSQL("CREATE TABLE IF NOT EXISTS tiles (key INTEGER, provider TEXT, tile BLOB, expires INTEGER)")
      db.execSQL(
        "INSERT INTO tiles (key, provider, tile, expires) VALUES (?, ?, ?, ?)",
        arrayOf(inAreaTileKey, "CartoDark", byteArrayOf(1, 2, 3, 4), System.currentTimeMillis() + 86_400_000L)
      )
      db.execSQL(
        "INSERT INTO tiles (key, provider, tile, expires) VALUES (?, ?, ?, ?)",
        arrayOf(outAreaTileKey, "CartoDark", byteArrayOf(5, 6, 7, 8), System.currentTimeMillis() + 86_400_000L)
      )
    }

    val overlays = GeoJsonTacticalLoader.loadBundledOverlays(context)
    val polygons = GeoJsonThreatZoneService.loadThreatPolygonsFromAssets(context)
    val route = TacticalPathfinder.findSafestWalkingRoute(
      startLat = 35.6892,
      startLng = 51.3890,
      safeHavens = TacticalDataProvider.SAFE_HAVENS,
      heatmapPoints = TacticalDataProvider.THREAT_HEATMAP_POINTS,
      cameras = TacticalDataProvider.SURVEILLANCE_CAMERAS
    )
    val snapshot = TacticalBackupSnapshot(
      bounds = BoundingBox(35.80, 51.50, 35.60, 51.30),
      userLat = 35.6892,
      userLng = 51.3890,
      mapMode = MapVisualMode.TACTICAL_HUD,
      freeMapProvider = FreeMapProvider.CARTO_DARK,
      threatZones = TacticalDataProvider.THREAT_ZONES,
      heatmapPoints = TacticalDataProvider.THREAT_HEATMAP_POINTS,
      safeHavens = TacticalDataProvider.SAFE_HAVENS,
      cameras = TacticalDataProvider.SURVEILLANCE_CAMERAS,
      geoJsonThreatPolygons = polygons,
      geoJsonOverlays = overlays,
      activeRoute = route,
      breadcrumbs = listOf(
        BreadcrumbEntity(
          id = 1L,
          sequenceNumber = 1,
          lat = 35.6892,
          lng = 51.3890,
          speedKmh = 4.2f,
          bearingDegrees = 0f,
          distanceFromPreviousMeters = 0.0
        )
      ),
      sosBroadcasts = listOf(
        SosBroadcastEntity(
          id = 1L,
          callsign = "AEGIS-1",
          lat = 35.6892,
          lng = 51.3890,
          accuracyMeters = 5f,
          emergencyMessage = "Backup test",
          yellowPageEndpoint = "",
          onlineSyncStatus = "SAVED LOCAL DB",
          onlineRecordUrl = null
        )
      ),
      liveIncidents = TacticalDataProvider.LIVE_INCIDENTS,
      intelSources = TacticalDataProvider.DEFAULT_INTEL_SOURCES
    )

    val summary = OfflineMapBackupExporter.exportToAppBackupFile(context, snapshot)
    assertEquals("Should export only cached tiles inside viewport bounds", 1, summary.tilesExportedCount)
    assertEquals(2, summary.totalCacheTilesCount)
    assertNotNull(summary.localAppBackupPath)
    val zipFile = File(summary.localAppBackupPath!!)
    assertTrue("Exported ZIP must exist and be non-empty", zipFile.exists() && zipFile.length() > 0L)

    val entries = mutableMapOf<String, ByteArray>()
    ZipInputStream(FileInputStream(zipFile)).use { zis ->
      var entry = zis.nextEntry
      while (entry != null) {
        entries[entry.name] = zis.readBytes()
        zis.closeEntry()
        entry = zis.nextEntry
      }
    }

    assertTrue(entries.containsKey("manifest.json"))
    assertTrue(entries.containsKey("tiles/tile_index.json"))
    assertTrue(entries.containsKey("tiles/CartoDark/10/658/403.png"))
    assertFalse("Out-of-bounds tile must not be included", entries.containsKey("tiles/CartoDark/10/100/200.png"))
    assertTrue(entries.containsKey("overlays/tactical_overlays.geojson"))
    assertTrue(entries.containsKey("overlays/tactical_state_snapshot.json"))
    assertTrue("Compiled .mvt tiles must be exported", entries.keys.any { it.startsWith("overlays/mvt/") && it.endsWith(".mvt") })

    val geoJson = JSONObject(String(entries["overlays/tactical_overlays.geojson"]!!, Charsets.UTF_8))
    assertEquals("FeatureCollection", geoJson.getString("type"))
    assertTrue(geoJson.getJSONArray("features").length() > 10)

    val listed = OfflineMapBackupExporter.listLocalBackups(context)
    assertTrue(listed.any { it.fileName == summary.archiveName })
  }
}
