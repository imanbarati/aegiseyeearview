package com.example.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

data class UserMovementState(
    val lat: Double,
    val lng: Double,
    val altitudeMeters: Double = 0.0,
    val speedKmh: Float = 0f,
    val bearingDegrees: Float = 0f,
    val accuracyMeters: Float = 0f,
    val isLiveGps: Boolean = false,
    val timestampMs: Long = System.currentTimeMillis()
)

class LocationHelper(private val context: Context) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    fun hasLocationPermission(): Boolean {
        val fineLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        return fineLocation || coarseLocation
    }

    @SuppressLint("MissingPermission")
    suspend fun getLastKnownLocation(): UserMovementState? {
        if (!hasLocationPermission()) return null
        return try {
            val location = fusedLocationClient.lastLocation.await()
            location?.toUserMovementState()
        } catch (e: Exception) {
            null
        }
    }

    @SuppressLint("MissingPermission")
    fun trackUserMovement(intervalMs: Long = 3000L): Flow<UserMovementState> = callbackFlow {
        if (!hasLocationPermission()) {
            close()
            return@callbackFlow
        }

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            intervalMs
        ).apply {
            setMinUpdateDistanceMeters(2f)
            setMinUpdateIntervalMillis(1500L)
            setWaitForAccurateLocation(false)
        }.build()

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    trySend(location.toUserMovementState())
                }
            }

            override fun onLocationAvailability(availability: LocationAvailability) {
                // Keep flow alive even during temporary satellite blockage
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            close(e)
        }

        awaitClose {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }

    private fun Location.toUserMovementState(): UserMovementState {
        return UserMovementState(
            lat = latitude,
            lng = longitude,
            altitudeMeters = altitude,
            speedKmh = speed * 3.6f, // Convert m/s to km/h
            bearingDegrees = bearing,
            accuracyMeters = accuracy,
            isLiveGps = true,
            timestampMs = time
        )
    }
}
