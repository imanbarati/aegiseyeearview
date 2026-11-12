package com.example

import com.example.data.TacticalDataProvider
import com.example.location.UserMovementState
import com.example.model.IntelSourceType
import org.junit.Assert.*
import org.junit.Test

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
  fun testDistanceCalculation() {
    // Distance between Tehran (35.6892, 51.3890) and Isfahan (32.6546, 51.6680) is ~340 km
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
}
