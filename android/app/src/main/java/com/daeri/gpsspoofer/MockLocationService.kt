package com.daeri.gpsspoofer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import java.util.Timer
import java.util.TimerTask

class MockLocationService : Service() {

    companion object {
        private const val TAG = "MockLocationService"
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_UPDATE = "ACTION_UPDATE"
        const val EXTRA_LAT = "lat"
        const val EXTRA_LNG = "lng"
        const val EXTRA_ACCURACY = "accuracy"

        private const val CHANNEL_ID = "gps_spoofer_channel"
        private const val NOTIF_ID = 4242
        private const val UPDATE_INTERVAL_MS = 1000L

        @Volatile var running: Boolean = false
            private set
        @Volatile var currentLat: Double = 0.0
            private set
        @Volatile var currentLng: Double = 0.0
            private set
    }

    private lateinit var locationManager: LocationManager
    private var timer: Timer? = null
    private val providers = listOf(
        LocationManager.GPS_PROVIDER,
        LocationManager.NETWORK_PROVIDER
    )

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val lat = intent.getDoubleExtra(EXTRA_LAT, Double.NaN)
                val lng = intent.getDoubleExtra(EXTRA_LNG, Double.NaN)
                val acc = intent.getFloatExtra(EXTRA_ACCURACY, 3.0f)
                if (lat.isNaN() || lng.isNaN()) {
                    Log.w(TAG, "Invalid coords, ignoring start")
                    return START_NOT_STICKY
                }
                startForegroundCompat(lat, lng)
                setupProviders()
                currentLat = lat
                currentLng = lng
                startInjection(acc)
                running = true
            }
            ACTION_UPDATE -> {
                val lat = intent.getDoubleExtra(EXTRA_LAT, Double.NaN)
                val lng = intent.getDoubleExtra(EXTRA_LNG, Double.NaN)
                if (!lat.isNaN() && !lng.isNaN()) {
                    currentLat = lat
                    currentLng = lng
                    updateNotification(lat, lng)
                }
            }
            ACTION_STOP -> {
                stopInjection()
                teardownProviders()
                running = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun setupProviders() {
        for (p in providers) {
            try {
                runCatching { locationManager.removeTestProvider(p) }
                locationManager.addTestProvider(
                    p,
                    /* requiresNetwork = */ false,
                    /* requiresSatellite = */ false,
                    /* requiresCell = */ false,
                    /* hasMonetaryCost = */ false,
                    /* supportsAltitude = */ true,
                    /* supportsSpeed = */ true,
                    /* supportsBearing = */ true,
                    android.location.Criteria.POWER_LOW,
                    android.location.Criteria.ACCURACY_FINE
                )
                locationManager.setTestProviderEnabled(p, true)
            } catch (e: SecurityException) {
                Log.e(TAG, "ACCESS_MOCK_LOCATION denied. 개발자 옵션 > 모의 위치 앱 선택 필요", e)
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "Provider $p not available", e)
            }
        }
    }

    private fun teardownProviders() {
        for (p in providers) {
            runCatching {
                locationManager.setTestProviderEnabled(p, false)
                locationManager.removeTestProvider(p)
            }
        }
    }

    private fun startInjection(accuracy: Float) {
        stopInjection()
        timer = Timer().apply {
            scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    pushLocation(currentLat, currentLng, accuracy)
                }
            }, 0, UPDATE_INTERVAL_MS)
        }
    }

    private fun stopInjection() {
        timer?.cancel()
        timer = null
    }

    private fun pushLocation(lat: Double, lng: Double, accuracy: Float) {
        for (p in providers) {
            try {
                val loc = Location(p).apply {
                    latitude = lat
                    longitude = lng
                    altitude = 0.0
                    this.accuracy = accuracy
                    time = System.currentTimeMillis()
                    elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        verticalAccuracyMeters = accuracy
                        bearingAccuracyDegrees = 0.1f
                        speedAccuracyMetersPerSecond = 0.01f
                    }
                }
                locationManager.setTestProviderLocation(p, loc)
            } catch (e: SecurityException) {
                Log.e(TAG, "setTestProviderLocation failed (mock app not selected?)", e)
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "Provider not added: $p", e)
            }
        }
    }

    private fun startForegroundCompat(lat: Double, lng: Double) {
        ensureChannel()
        val notif = buildNotification(lat, lng)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val ch = NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notif_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                )
                nm.createNotificationChannel(ch)
            }
        }
    }

    private fun buildNotification(lat: Double, lng: Double): android.app.Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, MockLocationService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText("%.6f, %.6f".format(lat, lng))
            .setContentIntent(openIntent)
            .addAction(0, getString(R.string.stop), stopIntent)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun updateNotification(lat: Double, lng: Double) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildNotification(lat, lng))
    }

    override fun onDestroy() {
        stopInjection()
        teardownProviders()
        running = false
        super.onDestroy()
    }
}
