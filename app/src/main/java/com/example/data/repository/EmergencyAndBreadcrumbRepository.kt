package com.example.data.repository

import android.content.Context
import android.content.Intent
import com.example.data.local.BreadcrumbDao
import com.example.data.local.BreadcrumbEntity
import com.example.data.local.SosBroadcastDao
import com.example.data.local.SosBroadcastEntity
import com.example.model.GeoPoint
import com.example.ui.map.TacticalRangefinderMath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class TacticalDatabaseRepository(
    private val sosDao: SosBroadcastDao,
    private val breadcrumbDao: BreadcrumbDao
) {
    val sosBroadcasts: Flow<List<SosBroadcastEntity>> = sosDao.observeAllBroadcasts()
    val breadcrumbs: Flow<List<BreadcrumbEntity>> = breadcrumbDao.observeAllBreadcrumbs()

    suspend fun insertSosBroadcast(entity: SosBroadcastEntity): Long {
        return sosDao.insertBroadcast(entity)
    }

    suspend fun updateSosOnlineStatus(id: Long, status: String, recordUrl: String?) {
        sosDao.updateOnlineStatus(id, status, recordUrl)
    }

    suspend fun deleteSosBroadcast(id: Long) {
        sosDao.deleteById(id)
    }

    suspend fun clearAllSosBroadcasts() {
        sosDao.clearAll()
    }

    suspend fun insertBreadcrumb(entity: BreadcrumbEntity): Long {
        return breadcrumbDao.insertBreadcrumb(entity)
    }

    suspend fun pruneExpiredBreadcrumbs(maxAgeMs: Long = TWO_HOURS_MS) {
        val cutoff = System.currentTimeMillis() - maxAgeMs
        breadcrumbDao.deleteExpiredTemporary(cutoff)
    }

    suspend fun clearBreadcrumbs() {
        breadcrumbDao.clearAll()
    }

    companion object {
        const val TWO_HOURS_MS = 2L * 60L * 60L * 1000L
    }
}

/**
 * Pure-Kotlin 50-meter threshold detector governing when temporary breadcrumb markers
 * are dropped along the agent's movement track.
 */
object BreadcrumbDistanceTracker {
    const val BREADCRUMB_INTERVAL_METERS = 50.0

    fun shouldDropBreadcrumb(
        lastPoint: GeoPoint?,
        candidateLat: Double,
        candidateLng: Double,
        thresholdMeters: Double = BREADCRUMB_INTERVAL_METERS
    ): Pair<Boolean, Double> {
        if (lastPoint == null) return true to 0.0
        val distMeters = TacticalRangefinderMath.calculateDistanceMeters(
            lastPoint,
            GeoPoint(candidateLat, candidateLng)
        )
        return (distMeters >= thresholdMeters) to distMeters
    }
}

sealed interface YellowPagePublishResult {
    data class Published(val recordUrl: String, val httpCode: Int) : YellowPagePublishResult
    data class OfflineOrUnconfigured(val reason: String) : YellowPagePublishResult
    data class Failed(val error: String) : YellowPagePublishResult
}

/**
 * Publishes emergency SOS telemetry to a user-configured Online Yellow Page webhook/directory
 * endpoint via OkHttp and prepares formatted emergency share intents for quick dispatch.
 */
object EmergencyYellowPagePublisher {

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    suspend fun publish(
        endpointUrl: String,
        entry: SosBroadcastEntity
    ): YellowPagePublishResult = withContext(Dispatchers.IO) {
        val trimmed = endpointUrl.trim()
        if (trimmed.isBlank()) {
            return@withContext YellowPagePublishResult.OfflineOrUnconfigured(
                "Saved to Local DB; no Online Yellow Page URL configured."
            )
        }
        if (!trimmed.startsWith("https://", ignoreCase = true) &&
            !trimmed.startsWith("http://", ignoreCase = true)
        ) {
            return@withContext YellowPagePublishResult.OfflineOrUnconfigured(
                "Invalid URL scheme; saved locally for quick sharing."
            )
        }

        val payload = JSONObject().apply {
            put("type", "AEGIS_SOS_YELLOW_PAGE_ENTRY")
            put("callsign", entry.callsign)
            put("lat", entry.lat)
            put("lng", entry.lng)
            put("accuracy_meters", entry.accuracyMeters.toDouble())
            put("message", entry.emergencyMessage)
            put("map_url", "https://www.openstreetmap.org/?mlat=${entry.lat}&mlon=${entry.lng}#map=16/${entry.lat}/${entry.lng}")
            put("timestamp_ms", entry.timestampMs)
        }.toString()

        try {
            val request = Request.Builder()
                .url(trimmed)
                .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                .header("Accept", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    YellowPagePublishResult.Published(trimmed, response.code)
                } else {
                    YellowPagePublishResult.Failed("HTTP ${response.code} ${response.message}")
                }
            }
        } catch (e: Exception) {
            YellowPagePublishResult.Failed(e.localizedMessage ?: "Network unavailable")
        }
    }

    fun formatShareText(entry: SosBroadcastEntity): String {
        val osmLink = "https://www.openstreetmap.org/?mlat=${entry.lat}&mlon=${entry.lng}#map=16/${entry.lat}/${entry.lng}"
        return buildString {
            appendLine("🚨 SOS EMERGENCY BROADCAST // AEGIS EYE")
            appendLine("Callsign: ${entry.callsign}")
            appendLine("GPS: ${String.format("%.5f", entry.lat)}, ${String.format("%.5f", entry.lng)} (±${entry.accuracyMeters.toInt()}m)")
            appendLine("Note: ${entry.emergencyMessage}")
            append("Map Link: $osmLink")
        }
    }

    fun launchQuickShare(context: Context, entry: SosBroadcastEntity) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "SOS Emergency Location — ${entry.callsign}")
            putExtra(Intent.EXTRA_TEXT, formatShareText(entry))
        }
        val chooser = Intent.createChooser(shareIntent, "Share SOS Emergency Telemetry").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}
