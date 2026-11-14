package com.example.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-process reactive bus connecting the background/foreground [ThreatProximityService]
 * with [com.example.ui.viewmodel.TacticalViewModel] so custom imported GeoJSON polygons
 * and active intrusion alerts synchronize seamlessly with visual HUD banners & haptics.
 */
object ThreatProximityBus {
    private val _monitoredPolygons = MutableStateFlow<List<GeoJsonThreatPolygon>>(emptyList())
    val monitoredPolygons: StateFlow<List<GeoJsonThreatPolygon>> = _monitoredPolygons.asStateFlow()

    private val _activeGeofenceAlert = MutableStateFlow<ThreatGeofenceAlert?>(null)
    val activeGeofenceAlert: StateFlow<ThreatGeofenceAlert?> = _activeGeofenceAlert.asStateFlow()

    private val _isSentinelRunning = MutableStateFlow(false)
    val isSentinelRunning: StateFlow<Boolean> = _isSentinelRunning.asStateFlow()

    private val _lastTriggeredPolygonId = MutableStateFlow<String?>(null)

    fun updatePolygons(polygons: List<GeoJsonThreatPolygon>) {
        _monitoredPolygons.value = polygons
    }

    fun setSentinelRunning(running: Boolean) {
        _isSentinelRunning.value = running
    }

    fun evaluateCoordinates(lat: Double, lng: Double): Boolean {
        val polygons = _monitoredPolygons.value
        val alert = ThreatGeofenceEvaluator.evaluateGeoJsonEntry(lat, lng, polygons)
        _activeGeofenceAlert.value = alert
        if (alert == null) {
            _lastTriggeredPolygonId.value = null
            return false
        }
        val isNewZoneEntry = _lastTriggeredPolygonId.value != alert.polygonId
        if (isNewZoneEntry) {
            _lastTriggeredPolygonId.value = alert.polygonId
        }
        return isNewZoneEntry
    }

    fun dismissVisualAlert() {
        _activeGeofenceAlert.value = null
    }
}

object ThreatHapticTrigger {
    // Double-pulse urgent SOS tactile alert: wait 0, vibrate 220ms, pause 110ms, vibrate 360ms, pause 100ms, vibrate 220ms
    private val ALERT_PATTERN = longArrayOf(0L, 220L, 110L, 360L, 100L, 220L)

    @Suppress("DEPRECATION")
    fun triggerThreatEntryHaptic(context: Context) {
        try {
            val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (vibrator == null || !vibrator.hasVibrator()) return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(ALERT_PATTERN, -1))
            } else {
                vibrator.vibrate(ALERT_PATTERN, -1)
            }
        } catch (_: Exception) {
        }
    }
}

/**
 * Play-Policy compliant foreground location service (`foregroundServiceType="location"`)
 * that monitors GPS coordinates even when the app is backgrounded and triggers a tactical
 * haptic vibration waveform + high-priority alert when entering a high-intensity GeoJSON threat zone.
 */
class ThreatProximityService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        ensureNotificationChannels()
        if (ThreatProximityBus.monitoredPolygons.value.isEmpty()) {
            ThreatProximityBus.updatePolygons(
                GeoJsonThreatZoneService.loadThreatPolygonsFromAssets(applicationContext)
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (!hasLocationPermission()) {
            ThreatProximityBus.setSentinelRunning(false)
            stopSelf()
            return START_NOT_STICKY
        }

        val ongoingNotification = buildForegroundNotification(
            title = "ISAC Threat Perimeter Sentinel Active",
            content = "Monitoring GeoJSON exclusion polygons in background."
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    FG_NOTIFICATION_ID,
                    ongoingNotification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                )
            } else {
                startForeground(FG_NOTIFICATION_ID, ongoingNotification)
            }
            ThreatProximityBus.setSentinelRunning(true)
            startLocationMonitoring()
        } catch (_: Exception) {
            ThreatProximityBus.setSentinelRunning(false)
            stopSelf()
        }

        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startLocationMonitoring() {
        if (locationCallback != null) return

        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 5000L)
            .setMinUpdateDistanceMeters(15f)
            .setMinUpdateIntervalMillis(2500L)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                val enteredNewZone = ThreatProximityBus.evaluateCoordinates(loc.latitude, loc.longitude)
                val alert = ThreatProximityBus.activeGeofenceAlert.value
                if (enteredNewZone && alert != null) {
                    ThreatHapticTrigger.triggerThreatEntryHaptic(applicationContext)
                    postIntrusionAlertNotification(alert)
                }
            }
        }
        locationCallback = callback
        try {
            fusedLocationClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
        } catch (_: SecurityException) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        locationCallback?.let {
            try {
                fusedLocationClient.removeLocationUpdates(it)
            } catch (_: Exception) {
            }
        }
        locationCallback = null
        ThreatProximityBus.setSentinelRunning(false)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    private fun ensureNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val fgChannel = NotificationChannel(
            CHANNEL_FOREGROUND,
            "Threat Sentinel Status",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Persistent status for background GeoJSON threat polygon monitoring."
        }
        val alertChannel = NotificationChannel(
            CHANNEL_INTRUSION_ALERT,
            "High-Intensity Threat Zone Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Triggered when GPS coordinates enter a high-intensity GeoJSON threat polygon."
            enableVibration(true)
        }
        manager.createNotificationChannel(fgChannel)
        manager.createNotificationChannel(alertChannel)
    }

    private fun buildForegroundNotification(title: String, content: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_FOREGROUND)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(pending)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .build()
    }

    private fun postIntrusionAlertNotification(alert: ThreatGeofenceAlert) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val launchIntent = Intent(this, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            this,
            101,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pct = (alert.intensity * 100).toInt()
        val notification = NotificationCompat.Builder(this, CHANNEL_INTRUSION_ALERT)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("BREACH: ${alert.sectorCode} (${alert.threatLevel.code})")
            .setContentText("Entered ${alert.zoneName} (${pct}% intensity). Tap to calculate safe bypass.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(ALERT_NOTIFICATION_ID, notification)
    }

    companion object {
        private const val ACTION_START = "com.example.action.START_THREAT_SENTINEL"
        private const val ACTION_STOP = "com.example.action.STOP_THREAT_SENTINEL"
        private const val CHANNEL_FOREGROUND = "aegis_threat_sentinel_fg"
        private const val CHANNEL_INTRUSION_ALERT = "aegis_threat_intrusion_alert"
        private const val FG_NOTIFICATION_ID = 4101
        private const val ALERT_NOTIFICATION_ID = 4102

        fun start(context: Context) {
            val fine = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            val coarse = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            if (!fine && !coarse) return

            val intent = Intent(context, ThreatProximityService::class.java).apply {
                action = ACTION_START
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (_: Exception) {
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, ThreatProximityService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.stopService(intent)
            } catch (_: Exception) {
            }
        }
    }
}
