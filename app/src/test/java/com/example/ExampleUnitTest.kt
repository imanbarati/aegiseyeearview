package com.example

import com.example.data.TacticalDataProvider
import com.example.data.local.SosBroadcastEntity
import com.example.data.repository.BreadcrumbDistanceTracker
import com.example.data.repository.EmergencyYellowPagePublisher
import com.example.location.UserMovementState
import com.example.model.CameraVulnerabilityStatus
import com.example.model.GeoPoint
import com.example.model.IntelSourceType
import com.example.model.MapVisualMode
import com.example.model.ThreatLevel
import com.example.routing.TacticalPathfinder
import com.example.sensor.CompassMath
import com.example.service.GeoJsonThreatPolygon
import com.example.service.ThreatGeofenceEvaluator
import com.example.ui.map.DistanceMeasurementState
import com.example.ui.map.FreeMapProvider
import com.example.ui.map.MercatorViewport
import com.example.ui.map.OfflineMapBackupExporter
import com.example.ui.map.OsmTileDiskCacheManager
import com.example.ui.map.TacticalRangefinderMath
import com.example.ui.map.TacticalTileRepository
import com.example.ui.map.TileCoord
import com.example.ui.map.mvt.GeoJsonTacticalLoader
import com.example.ui.map.mvt.MvtEncoder
import com.example.ui.map.mvt.MvtGeometryType
import com.example.ui.map.mvt.MvtInputFeature
import com.example.ui.map.mvt.MvtInputLayer
import com.example.ui.map.mvt.MvtParser
import org.junit.Assert.*
import org.junit.Test
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.MapTileIndex

class ExampleUnitTest {
  @Test
  fun testEvacuationRouteCalculation() {
    val route = TacticalDataProvider.calculateEvacuationRoute(
      originCityName = "Tehran",
      destinationHavenId = "haven_kapikoy",
      standoffBufferKm = 30.0
    )
    assertNotNull(route)
    assertTrue("Route must have waypoints", route.path.size >= 2)
    assertTrue("Distance must be greater than zero", route.distanceKm > 0)
    assertTrue("Risk score should be computed", route.riskScore in 0..100)
    assertTrue("Route should avoid dead zones", route.deadZonesAvoided.isNotEmpty())
  }

  @Test
  fun testSafestWalkingPathfinderToNearestSafeZone() {
    val route = TacticalPathfinder.findSafestWalkingRoute(
      startLat = 35.6892,
      startLng = 51.3890,
      safeHavens = TacticalDataProvider.SAFE_HAVENS,
      heatmapPoints = TacticalDataProvider.THREAT_HEATMAP_POINTS,
      cameras = TacticalDataProvider.SURVEILLANCE_CAMERAS,
      targetSafeHavenId = null,
      avoidCctv = true,
      threatWeight = 22.0
    )
    assertTrue("Walking route flag must be true", route.isWalkingRoute)
    assertTrue("Path should contain multiple A* waypoints", route.path.size >= 3)
    assertTrue("Estimated walking time should be positive", route.estWalkMinutes > 0)
    assertTrue("Should target a walkable Tehran Safe Zone", route.targetSafeHavenId.startsWith("safezone_tehran"))
  }

  @Test
  fun testHeatmapIntensityEvaluation() {
    val centerIntensity = TacticalPathfinder.evaluateHeatmapIntensity(
      lat = 33.5119,
      lng = 51.9167,
      heatmapPoints = TacticalDataProvider.THREAT_HEATMAP_POINTS
    )
    val farIntensity = TacticalPathfinder.evaluateHeatmapIntensity(
      lat = 25.2919,
      lng = 60.6430, // Chabahar far from threat centers
      heatmapPoints = TacticalDataProvider.THREAT_HEATMAP_POINTS
    )
    assertTrue("Epicenter intensity should be high", centerIntensity >= 0.9f)
    assertTrue("Far safe port intensity should be near zero", farIntensity < 0.05f)
  }

  @Test
  fun testCctvVulnerabilityExposure() {
    // Directly inside Azadi Square Public OSINT camera (35.6997, 51.3380)
    val exposed = TacticalPathfinder.evaluateCctvExposure(
      lat = 35.6997,
      lng = 51.3380,
      cameras = TacticalDataProvider.SURVEILLANCE_CAMERAS
    )
    assertTrue("Should detect exposure inside Azadi Square camera FOV", exposed.isExposed)
    assertTrue(
      "Should report vulnerable public OSINT camera",
      exposed.camerasInFov.any { it.vulnerabilityStatus == CameraVulnerabilityStatus.VULNERABLE_PUBLIC_OSINT }
    )

    // Near Tajrish offline jammed camera (35.7880, 51.4380) -> confirmed blind spot
    val blindSpot = TacticalPathfinder.evaluateCctvExposure(
      lat = 35.7880,
      lng = 51.4380,
      cameras = TacticalDataProvider.SURVEILLANCE_CAMERAS
    )
    assertFalse("Offline jammed camera should yield invulnerable blind spot", blindSpot.isExposed)
    assertTrue("Should detect offline blind spot nearby", blindSpot.offlineBlindSpotsNearbyCount >= 1)
  }

  @Test
  fun testDistanceCalculation() {
    val dist = TacticalDataProvider.calculateDistanceKm(35.6892, 51.3890, 32.6546, 51.6680)
    assertTrue("Distance should be approximately 330-360km", dist in 320.0..370.0)
  }

  @Test
  fun testIntelSourcesDefaults() {
    val sources = TacticalDataProvider.DEFAULT_INTEL_SOURCES
    assertTrue("Should have multiple default intel sources", sources.size >= 5)
    assertTrue("Should include @hey_itsmyturn OSINT source", sources.any { it.type == IntelSourceType.X_TWITTER && it.endpointUrl.contains("hey_itsmyturn") })
    assertTrue("Should include Fediverse source", sources.any { it.type == IntelSourceType.FEDIVERSE_MASTODON })
    assertTrue("Should include RSS feed", sources.any { it.type == IntelSourceType.RSS_FEED })
    assertTrue("Should include Homemade SDR", sources.any { it.type == IntelSourceType.HOMEMADE_SDR })
    assertTrue("Should include Radio Radar", sources.any { it.type == IntelSourceType.RADIO_RADAR })
  }

  @Test
  fun testUserMovementState() {
    val movement = UserMovementState(
      lat = 35.6892,
      lng = 51.3890,
      altitudeMeters = 1200.0,
      speedKmh = 45.5f,
      bearingDegrees = 180f,
      accuracyMeters = 3.5f,
      isLiveGps = true
    )
    assertEquals(35.6892, movement.lat, 0.0001)
    assertEquals(51.3890, movement.lng, 0.0001)
    assertEquals(45.5f, movement.speedKmh, 0.1f)
    assertTrue(movement.isLiveGps)
  }

  @Test
  fun testMercatorViewportProjectionRoundTripAndScaling() {
    val vp = MercatorViewport(
      centerLat = 35.6892,
      centerLng = 51.3890,
      zoom = 12.0,
      widthPx = 1080f,
      heightPx = 1920f
    )
    // Center lat/lng must project to exact center of viewport
    val centerScreen = vp.latLngToScreen(35.6892, 51.3890)
    assertEquals(540f, centerScreen.x, 0.1f)
    assertEquals(960f, centerScreen.y, 0.1f)

    // Round-trip world pixel <-> lat/lng
    val (wx, wy) = vp.latLngToWorldPixel(32.6546, 51.6680)
    val (rtLat, rtLng) = vp.worldPixelToLatLng(wx, wy)
    assertEquals(32.6546, rtLat, 0.0001)
    assertEquals(51.6680, rtLng, 0.0001)

    // Round-trip screen <-> lat/lng
    val offCenterScreen = vp.latLngToScreen(35.7500, 51.4500)
    val (scrLat, scrLng) = vp.screenToLatLng(offCenterScreen.x, offCenterScreen.y)
    assertEquals(35.7500, scrLat, 0.0001)
    assertEquals(51.4500, scrLng, 0.0001)

    // metersToPixels scales 2x per zoom level
    val pxAtZ12 = vp.metersToPixels(1000.0)
    val vpZ13 = vp.copy(zoom = 13.0)
    val pxAtZ13 = vpZ13.metersToPixels(1000.0)
    assertTrue("metersToPixels should be positive", pxAtZ12 > 0f)
    assertEquals(pxAtZ12 * 2f, pxAtZ13, 0.01f)
  }

  @Test
  fun testFreeTileProvidersRequireNoApiKey() {
    assertEquals(
      FreeMapProvider.BUILT_IN_OFFLINE,
      TacticalTileRepository.providerForMode(MapVisualMode.TACTICAL_HUD, offlineOnly = true)
    )
    assertEquals(
      FreeMapProvider.CARTO_DARK,
      TacticalTileRepository.providerForMode(MapVisualMode.TACTICAL_HUD, offlineOnly = false)
    )
    assertEquals(
      FreeMapProvider.ESRI_SATELLITE,
      TacticalTileRepository.providerForMode(MapVisualMode.SATELLITE_RECON, offlineOnly = false)
    )
    assertNull(TileCoord(10, 658, 405, FreeMapProvider.BUILT_IN_OFFLINE).url())
    val cartoUrl = TileCoord(10, 658, 405, FreeMapProvider.CARTO_DARK).url()
    assertNotNull(cartoUrl)
    assertFalse("Tile URL must not require an API key parameter", cartoUrl!!.contains("key=", ignoreCase = true))
  }

  @Test
  fun testMapBoxVectorTileBinaryEncodeAndDecodeRoundTrip() {
    val tileZ = 10
    val tileX = 658
    val tileY = 403

    val polygonFeature = MvtInputFeature(
      id = 101L,
      type = MvtGeometryType.POLYGON,
      properties = mapOf(
        "name" to "Sector Alpha Exclusion",
        "threat_level" to "CRITICAL",
        "active" to true,
        "priority" to 1
      ),
      geoRingsOrLines = listOf(
        listOf(
          GeoPoint(35.68, 51.37),
          GeoPoint(35.68, 51.42),
          GeoPoint(35.71, 51.42),
          GeoPoint(35.71, 51.37),
          GeoPoint(35.68, 51.37)
        )
      )
    )
    val lineFeature = MvtInputFeature(
      id = 102L,
      type = MvtGeometryType.LINESTRING,
      properties = mapOf("name" to "Safe Walk Corridor", "est_minutes" to 32),
      geoRingsOrLines = listOf(
        listOf(
          GeoPoint(35.6892, 51.3890),
          GeoPoint(35.7400, 51.4100),
          GeoPoint(35.8045, 51.4275)
        )
      )
    )
    val pointFeature = MvtInputFeature(
      id = 103L,
      type = MvtGeometryType.POINT,
      properties = mapOf("name" to "LoRa Relay"),
      geoRingsOrLines = listOf(listOf(GeoPoint(35.8045, 51.4275)))
    )

    val mvtBytes = MvtEncoder.encodeTile(
      layers = listOf(MvtInputLayer("tactical_sectors", 4096, listOf(polygonFeature, lineFeature, pointFeature))),
      tileZ = tileZ,
      tileX = tileX,
      tileY = tileY
    )
    assertTrue("Encoded MVT binary must be non-empty", mvtBytes.isNotEmpty())

    val decoded = MvtParser.parse(mvtBytes, tileZ, tileX, tileY)
    assertEquals(1, decoded.layers.size)
    val layer = decoded.layers.first()
    assertEquals("tactical_sectors", layer.name)
    assertEquals(4096, layer.extent)
    assertEquals(3, layer.features.size)

    val decodedPoly = layer.features[0]
    assertEquals(MvtGeometryType.POLYGON, decodedPoly.type)
    assertEquals("Sector Alpha Exclusion", decodedPoly.properties["name"])
    assertEquals("CRITICAL", decodedPoly.properties["threat_level"])
    assertEquals(true, decodedPoly.properties["active"])
    assertEquals(1L, decodedPoly.properties["priority"])
    assertTrue("Closed polygon ring should have >= 4 vertices", decodedPoly.geoGeometries.first().size >= 4)
    assertEquals(35.68, decodedPoly.geoGeometries.first().first().lat, 0.002)
    assertEquals(51.37, decodedPoly.geoGeometries.first().first().lng, 0.002)

    val decodedLine = layer.features[1]
    assertEquals(MvtGeometryType.LINESTRING, decodedLine.type)
    assertEquals(3, decodedLine.geoGeometries.first().size)

    val decodedPoint = layer.features[2]
    assertEquals(MvtGeometryType.POINT, decodedPoint.type)
    assertEquals(35.8045, decodedPoint.geoGeometries.first().first().lat, 0.002)
    assertEquals(51.4275, decodedPoint.geoGeometries.first().first().lng, 0.002)
  }

  @Test
  fun testOsmTileDiskCacheBoundsCalculation() {
    val tehranBounds = BoundingBox(35.82, 51.55, 35.58, 51.25)
    val indices = OsmTileDiskCacheManager.calculateTileIndicesForBounds(
      bounds = tehranBounds,
      minZoom = 6,
      maxZoom = 11,
      maxTiles = 120
    )
    assertTrue("Should calculate tile indices for Tehran sector", indices.isNotEmpty())
    assertTrue("Should enforce maxTiles cap", indices.size <= 120)
    assertTrue(
      "All tile indices must sit within requested zoom band Z6..Z11",
      indices.all { MapTileIndex.getZoom(it) in 6..11 }
    )
  }

  @Test
  fun testCompassMathCircularSmoothingAndCardinals() {
    // Wrap-around from 358 -> 2 degrees should step through 0 (not 180 South)
    val wrapped = CompassMath.smoothHeadingDegrees(358f, 2f, alpha = 0.5f)
    assertEquals(0f, wrapped, 0.01f)

    val wrappedBack = CompassMath.smoothHeadingDegrees(2f, 358f, alpha = 0.5f)
    assertEquals(0f, wrappedBack, 0.01f)

    assertEquals("N", CompassMath.headingToCardinal(359f))
    assertEquals("NE", CompassMath.headingToCardinal(45f))
    assertEquals("E", CompassMath.headingToCardinal(90f))
    assertEquals("SW", CompassMath.headingToCardinal(225f))

    val northBearing = CompassMath.bearingBetweenDegrees(35.0, 51.0, 36.0, 51.0)
    assertEquals(0f, northBearing, 1.0f)
    val eastBearing = CompassMath.bearingBetweenDegrees(35.0, 51.0, 35.0, 52.0)
    assertEquals(90f, eastBearing, 1.0f)
  }

  @Test
  fun testThreatGeofencePointInPolygonAndHoleExclusion() {
    val highZoneWithHole = GeoJsonThreatPolygon(
      id = "gj_high_ring",
      name = "High Intensity Ring",
      sectorCode = "GEO-TST-1",
      threatLevel = ThreatLevel.CRITICAL_ALPHA,
      category = "AIR_DEFENSE",
      notes = "Test ring with safe interior cutout",
      casualtyRisk = "EXTREME",
      bypassBufferKm = 25.0,
      sourceFileName = "test.geojson",
      outerRing = listOf(
        GeoPoint(35.60, 51.30),
        GeoPoint(35.60, 51.50),
        GeoPoint(35.80, 51.50),
        GeoPoint(35.80, 51.30),
        GeoPoint(35.60, 51.30)
      ),
      holes = listOf(
        listOf(
          GeoPoint(35.66, 51.36),
          GeoPoint(35.66, 51.44),
          GeoPoint(35.74, 51.44),
          GeoPoint(35.74, 51.36),
          GeoPoint(35.66, 51.36)
        )
      ),
      centerLat = 35.70,
      centerLng = 51.40,
      approxRadiusKm = 14.0,
      approxAreaKm2 = 300.0,
      strokeColorHex = "#FF2A2A",
      fillColorHex = "#FF2A2A"
    )

    val moderateZone = highZoneWithHole.copy(
      id = "gj_mod_zone",
      sectorCode = "GEO-MOD-2",
      threatLevel = ThreatLevel.MODERATE_CHARLIE,
      holes = emptyList(),
      intensity = 0.50f
    )

    // Inside outer ring of highZoneWithHole, outside hole -> triggers alert
    val hitAlert = ThreatGeofenceEvaluator.evaluateGeoJsonEntry(35.62, 51.32, listOf(highZoneWithHole))
    assertNotNull("Entering high-intensity GeoJSON perimeter should raise alert", hitAlert)
    assertEquals("GEO-TST-1", hitAlert!!.sectorCode)

    // Inside interior safe-corridor hole -> excluded cleanly
    val insideHole = ThreatGeofenceEvaluator.evaluateGeoJsonEntry(35.70, 51.40, listOf(highZoneWithHole))
    assertNull("Point inside safe polygon hole must not trigger alert", insideHole)

    // Inside low/moderate zone only -> no high-intensity alert
    val modAlert = ThreatGeofenceEvaluator.evaluateGeoJsonEntry(35.70, 51.40, listOf(moderateZone))
    assertNull("Moderate intensity zone below threshold must not trigger high-intensity breach alert", modAlert)
  }

  @Test
  fun testTwoPointRangefinderDistanceAndFormatting() {
    val tehran = GeoPoint(35.6892, 51.3890)
    val tajrish = GeoPoint(35.8045, 51.4275)
    val state = DistanceMeasurementState(isActive = true, pointA = tehran, pointB = tajrish)

    val distMeters = state.distanceMeters
    assertNotNull(distMeters)
    assertTrue("Tehran -> Tajrish geodesic distance should be ~12-15 km", distMeters!! in 12_000.0..15_000.0)
    assertNotNull(state.bearingDegrees)
    assertTrue("Northward leg should have bearing near 0..30 deg", state.bearingDegrees!! in 0f..30f)
    assertTrue("Estimated walking time should exceed 120 mins", (state.walkMinutes ?: 0) >= 120)

    assertEquals("750 M", TacticalRangefinderMath.formatPrimaryDistance(750.0))
    assertEquals("0.750 KM", TacticalRangefinderMath.formatSecondaryDistance(750.0))
    assertTrue(TacticalRangefinderMath.formatPrimaryDistance(13_450.0).endsWith("KM"))
  }

  @Test
  fun testBreadcrumb50MeterThresholdAndSosShareFormatting() {
    // 1. First anchor point always drops
    val (firstDrop, firstDist) = BreadcrumbDistanceTracker.shouldDropBreadcrumb(null, 35.6892, 51.3890)
    assertTrue("Initial point must drop breadcrumb", firstDrop)
    assertEquals(0.0, firstDist, 0.01)

    val anchor = GeoPoint(35.6892, 51.3890)
    // 2. ~22m north (0.0002 deg lat ~ 22.2m) -> below 50m threshold
    val (nearDrop, nearDist) = BreadcrumbDistanceTracker.shouldDropBreadcrumb(anchor, 35.6894, 51.3890)
    assertFalse("Movement under 50m must not drop breadcrumb", nearDrop)
    assertTrue("Distance should be between 15m and 35m", nearDist in 15.0..35.0)

    // 3. ~66m north (0.0006 deg lat ~ 66.7m) -> exceeds 50m threshold
    val (farDrop, farDist) = BreadcrumbDistanceTracker.shouldDropBreadcrumb(anchor, 35.6898, 51.3890)
    assertTrue("Movement >= 50m must drop breadcrumb", farDrop)
    assertTrue("Distance should exceed 50m", farDist >= 50.0)

    // 4. Emergency Yellow Page / Share text formatting
    val entry = SosBroadcastEntity(
      id = 7L,
      callsign = "VANGUARD-4",
      lat = 35.68920,
      lng = 51.38900,
      accuracyMeters = 4f,
      emergencyMessage = "Need immediate medical evac",
      yellowPageEndpoint = "",
      onlineSyncStatus = "SAVED LOCAL DB • SHARE READY",
      onlineRecordUrl = null
    )
    val shareText = EmergencyYellowPagePublisher.formatShareText(entry)
    assertTrue(shareText.contains("VANGUARD-4"))
    assertTrue(shareText.contains("35.68920"))
    assertTrue(shareText.contains("51.38900"))
    assertTrue(shareText.contains("Need immediate medical evac"))
    assertTrue(shareText.contains("openstreetmap.org"))
  }

  @Test
  fun testOfflineBackupTileBoundsFiltering() {
    val tehranBounds = BoundingBox(35.80, 51.50, 35.60, 51.30)
    // Z10 X=658, Y=403 corresponds to Tehran (verified in MVT test above)
    val tehranTile = MapTileIndex.getTileIndex(10, 658, 403)
    // Far away Atlantic Ocean tile (Z10 X=100, Y=200)
    val remoteTile = MapTileIndex.getTileIndex(10, 100, 200)

    assertTrue("Tehran tile must match Tehran viewport bounds", OfflineMapBackupExporter.isTileInsideBounds(tehranTile, tehranBounds))
    assertFalse("Remote ocean tile must be excluded from current area backup", OfflineMapBackupExporter.isTileInsideBounds(remoteTile, tehranBounds))
    assertTrue(OfflineMapBackupExporter.defaultBackupFileName().endsWith(".zip"))
  }
}
