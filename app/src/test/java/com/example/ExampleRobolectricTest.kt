package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ui.map.OsmDroidConfig
import com.example.ui.map.mvt.GeoJsonTacticalLoader
import com.example.ui.map.mvt.MvtGeometryType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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
    assertEquals(3, overlays.size)
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
}
