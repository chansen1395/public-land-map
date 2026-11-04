package com.connorhansen.publiclandmap.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.connorhansen.publiclandmap.app.MainActivity
import com.connorhansen.publiclandmap.domain.usecase.RecordTrackUseCase
import com.connorhansen.publiclandmap.util.DistanceCalculator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Timer
import java.util.TimerTask
import javax.inject.Inject

@AndroidEntryPoint
class TrackingService : Service() {

    companion object {
        const val CHANNEL_ID = "tracking_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.connorhansen.publiclandmap.action.START_TRACKING"
        const val ACTION_STOP = "com.connorhansen.publiclandmap.action.STOP_TRACKING"
        const val EXTRA_TRACK_ID = "extra_track_id"
        const val EXTRA_TRACK_NAME = "extra_track_name"

        private val _trackingState = MutableStateFlow(TrackingState())
        val trackingState: StateFlow<TrackingState> = _trackingState.asStateFlow()
    }

    data class TrackingState(
        val isRecording: Boolean = false,
        val activeTrackId: String? = null,
        val trackName: String? = null,
        val elapsedMs: Long = 0L,
        val distanceMeters: Double = 0.0,
        val pointCount: Int = 0
    )

    @Inject
    lateinit var recordTrackUseCase: RecordTrackUseCase

    @Inject
    lateinit var distanceCalculator: DistanceCalculator

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null
    private var elapsedTimer: Timer? = null
    private var startTimeMs: Long = 0L
    private var trackId: String? = null
    private var lastLat: Double? = null
    private var lastLng: Double? = null
    private var totalDistance: Double = 0.0
    private var pointCount: Int = 0

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val tId = intent.getStringExtra(EXTRA_TRACK_ID) ?: return START_NOT_STICKY
                val tName = intent.getStringExtra(EXTRA_TRACK_NAME) ?: "Track"
                startTracking(tId, tName)
            }
            ACTION_STOP -> {
                stopTracking()
            }
            else -> {
                // If restarted by system without action, stop ourselves
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopLocationUpdates()
        elapsedTimer?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    @SuppressLint("MissingPermission")
    private fun startTracking(tId: String, tName: String) {
        trackId = tId
        startTimeMs = System.currentTimeMillis()
        totalDistance = 0.0
        pointCount = 0
        lastLat = null
        lastLng = null

        _trackingState.update {
            TrackingState(
                isRecording = true,
                activeTrackId = tId,
                trackName = tName,
                elapsedMs = 0L,
                distanceMeters = 0.0,
                pointCount = 0
            )
        }

        // Start foreground with notification
        val notification = buildNotification("Recording: $tName", "Starting...")
        startForeground(NOTIFICATION_ID, notification)

        // Elapsed time timer
        elapsedTimer = Timer().apply {
            scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    val elapsed = System.currentTimeMillis() - startTimeMs
                    _trackingState.update { it.copy(elapsedMs = elapsed) }
                    updateNotification()
                }
            }, 1000L, 1000L)
        }

        // Location updates
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            5000L
        ).setMinUpdateIntervalMillis(3000L).build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    onNewLocation(
                        location.latitude,
                        location.longitude,
                        if (location.hasAltitude()) location.altitude else null,
                        if (location.hasAccuracy()) location.accuracy else null,
                        location.time
                    )
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback!!,
            Looper.getMainLooper()
        )
    }

    private fun onNewLocation(
        lat: Double,
        lng: Double,
        elevation: Double?,
        accuracy: Float?,
        timestamp: Long
    ) {
        val currentTrackId = trackId ?: return

        // Calculate distance increment
        val prevLat = lastLat
        val prevLng = lastLng
        if (prevLat != null && prevLng != null) {
            val segmentDistance = distanceCalculator.haversineMeters(prevLat, prevLng, lat, lng)
            // Filter out GPS jitter: ignore points that moved less than ~2m with >15m accuracy
            if (accuracy != null && accuracy > 15f && segmentDistance < 2.0) {
                return
            }
            totalDistance += segmentDistance
        }

        lastLat = lat
        lastLng = lng
        pointCount++

        _trackingState.update {
            it.copy(
                distanceMeters = totalDistance,
                pointCount = pointCount
            )
        }

        // Save to database
        serviceScope.launch {
            recordTrackUseCase.addPoint(
                trackId = currentTrackId,
                latitude = lat,
                longitude = lng,
                elevation = elevation,
                accuracy = accuracy,
                timestamp = timestamp
            )
        }
    }

    private fun stopTracking() {
        stopLocationUpdates()
        elapsedTimer?.cancel()
        elapsedTimer = null

        val finalTrackId = trackId
        if (finalTrackId != null) {
            serviceScope.launch {
                recordTrackUseCase.stopRecording(finalTrackId)
            }
        }

        _trackingState.update { TrackingState() }
        trackId = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun stopLocationUpdates() {
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
        }
        locationCallback = null
    }

    private fun updateNotification() {
        val state = _trackingState.value
        val elapsed = formatDuration(state.elapsedMs)
        val distance = formatDistance(state.distanceMeters)
        val title = "Recording: ${state.trackName ?: "Track"}"
        val text = "$distance · $elapsed · ${state.pointCount} pts"

        val notification = buildNotification(title, text)
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(title: String, text: String): Notification {
        // Tap notification → open app
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val mainPending = PendingIntent.getActivity(
            this, 0, mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Stop action
        val stopIntent = Intent(this, TrackingService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setContentIntent(mainPending)
            .addAction(
                android.R.drawable.ic_media_pause,
                "Stop",
                stopPending
            )
            .setSilent(true)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Track Recording",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows when a GPS track is being recorded"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun formatDuration(ms: Long): String {
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return when {
            hours > 0 -> "%d:%02d:%02d".format(hours, minutes, seconds)
            else -> "%d:%02d".format(minutes, seconds)
        }
    }

    private fun formatDistance(meters: Double): String {
        val miles = distanceCalculator.metersToMiles(meters)
        return if (miles < 0.1) {
            "%.0f ft".format(distanceCalculator.metersToFeet(meters))
        } else {
            "%.2f mi".format(miles)
        }
    }
}
