package com.example

import com.example.data.TacticalDataProvider
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
}
