package com.example.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.view.Surface
import android.view.WindowManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

data class CompassReading(
    val headingDegrees: Float = 0f,
    val cardinal: String = "N",
    val accuracy: Int = SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM,
    val hasHardwareSensor: Boolean = true
)

/**
 * Pure-Kotlin circular angle smoothing and cardinal/bearing math so orientation filtering
 * behaves smoothly across 359° -> 0° boundaries and is fully testable on JVM.
 */
object CompassMath {

    fun normalizeDegrees(deg: Float): Float {
        var norm = deg % 360f
        if (norm < 0f) norm += 360f
        return norm
    }

    fun shortestAngleDelta(fromDeg: Float, toDeg: Float): Float {
        var diff = (toDeg - fromDeg) % 360f
        if (diff > 180f) diff -= 360f
        if (diff < -180f) diff += 360f
        return diff
    }

    fun smoothHeadingDegrees(previousDeg: Float?, targetDeg: Float, alpha: Float = 0.22f): Float {
        val cleanTarget = normalizeDegrees(targetDeg)
        if (previousDeg == null) return cleanTarget
        val delta = shortestAngleDelta(previousDeg, cleanTarget)
        return normalizeDegrees(previousDeg + delta * alpha.coerceIn(0.05f, 1f))
    }

    fun headingToCardinal(deg: Float): String {
        val idx = ((normalizeDegrees(deg) + 22.5f) / 45f).toInt() % 8
        return when (idx) {
            0 -> "N"
            1 -> "NE"
            2 -> "E"
            3 -> "SE"
            4 -> "S"
            5 -> "SW"
            6 -> "W"
            else -> "NW"
        }
    }

    /**
     * Calculates initial forward azimuth (degrees clockwise from True North) between two GPS points.
     */
    fun bearingBetweenDegrees(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Float {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dLambda = Math.toRadians(lng2 - lng1)
        val y = sin(dLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLambda)
        val theta = Math.toDegrees(atan2(y, x)).toFloat()
        return normalizeDegrees(theta)
    }
}

/**
 * Streams real-time device azimuth from [Sensor.TYPE_ROTATION_VECTOR] (with automatic
 * [Sensor.TYPE_ACCELEROMETER] + [Sensor.TYPE_MAGNETIC_FIELD] fallback) for tactical compass rotation.
 */
class DeviceOrientationHelper(private val context: Context) {

    fun observeHeading(): Flow<CompassReading> = callbackFlow {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        if (sensorManager == null) {
            trySend(CompassReading(headingDegrees = 0f, cardinal = "N", hasHardwareSensor = false))
            close()
            return@callbackFlow
        }

        val rotationVector = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        if (rotationVector == null && (accelerometer == null || magnetometer == null)) {
            trySend(CompassReading(headingDegrees = 0f, cardinal = "N", hasHardwareSensor = false))
            close()
            return@callbackFlow
        }

        var smoothedHeading: Float? = null
        val rotationMatrix = FloatArray(9)
        val remappedMatrix = FloatArray(9)
        val orientation = FloatArray(3)
        val gravity = FloatArray(3)
        val geomagnetic = FloatArray(3)
        var hasGravity = false
        var hasGeomagnetic = false
        var currentAccuracy = SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM

        fun emitAzimuth(azimuthRad: Float) {
            val rawDeg = Math.toDegrees(azimuthRad.toDouble()).toFloat()
            val clean = CompassMath.normalizeDegrees(rawDeg)
            val prev = smoothedHeading
            val next = CompassMath.smoothHeadingDegrees(prev, clean, alpha = 0.25f)
            if (prev == null || kotlin.math.abs(CompassMath.shortestAngleDelta(prev, next)) >= 0.4f) {
                smoothedHeading = next
                val rounded = (next * 10f).roundToInt() / 10f
                trySend(
                    CompassReading(
                        headingDegrees = rounded,
                        cardinal = CompassMath.headingToCardinal(rounded),
                        accuracy = currentAccuracy,
                        hasHardwareSensor = true
                    )
                )
            }
        }

        @Suppress("DEPRECATION")
        fun remapForDisplayRotation(inR: FloatArray, outR: FloatArray): FloatArray {
            val rotation = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    context.display?.rotation ?: Surface.ROTATION_0
                } else {
                    (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)
                        ?.defaultDisplay?.rotation ?: Surface.ROTATION_0
                }
            } catch (_: Exception) {
                Surface.ROTATION_0
            }

            return when (rotation) {
                Surface.ROTATION_90 -> {
                    SensorManager.remapCoordinateSystem(inR, SensorManager.AXIS_Y, SensorManager.AXIS_MINUS_X, outR)
                    outR
                }
                Surface.ROTATION_180 -> {
                    SensorManager.remapCoordinateSystem(inR, SensorManager.AXIS_MINUS_X, SensorManager.AXIS_MINUS_Y, outR)
                    outR
                }
                Surface.ROTATION_270 -> {
                    SensorManager.remapCoordinateSystem(inR, SensorManager.AXIS_MINUS_Y, SensorManager.AXIS_X, outR)
                    outR
                }
                else -> inR
            }
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ROTATION_VECTOR -> {
                        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                        val activeMatrix = remapForDisplayRotation(rotationMatrix, remappedMatrix)
                        SensorManager.getOrientation(activeMatrix, orientation)
                        emitAzimuth(orientation[0])
                    }
                    Sensor.TYPE_ACCELEROMETER -> {
                        val a = 0.8f
                        gravity[0] = a * gravity[0] + (1 - a) * event.values[0]
                        gravity[1] = a * gravity[1] + (1 - a) * event.values[1]
                        gravity[2] = a * gravity[2] + (1 - a) * event.values[2]
                        hasGravity = true
                        if (hasGeomagnetic && SensorManager.getRotationMatrix(rotationMatrix, null, gravity, geomagnetic)) {
                            val activeMatrix = remapForDisplayRotation(rotationMatrix, remappedMatrix)
                            SensorManager.getOrientation(activeMatrix, orientation)
                            emitAzimuth(orientation[0])
                        }
                    }
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        val a = 0.8f
                        geomagnetic[0] = a * geomagnetic[0] + (1 - a) * event.values[0]
                        geomagnetic[1] = a * geomagnetic[1] + (1 - a) * event.values[1]
                        geomagnetic[2] = a * geomagnetic[2] + (1 - a) * event.values[2]
                        hasGeomagnetic = true
                        if (hasGravity && SensorManager.getRotationMatrix(rotationMatrix, null, gravity, geomagnetic)) {
                            val activeMatrix = remapForDisplayRotation(rotationMatrix, remappedMatrix)
                            SensorManager.getOrientation(activeMatrix, orientation)
                            emitAzimuth(orientation[0])
                        }
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                currentAccuracy = accuracy
            }
        }

        if (rotationVector != null) {
            sensorManager.registerListener(listener, rotationVector, SensorManager.SENSOR_DELAY_UI)
        } else {
            accelerometer?.let { sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
            magnetometer?.let { sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
        }

        awaitClose {
            sensorManager.unregisterListener(listener)
        }
    }
}
