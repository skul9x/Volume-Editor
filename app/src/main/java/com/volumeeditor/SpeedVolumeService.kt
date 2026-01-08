package com.volumeeditor

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Speed-Dependent Volume (SDV) Service
 * 
 * Tính năng tự động điều chỉnh âm lượng theo tốc độ xe thông qua GPS.
 * Khi xe chạy nhanh → tiếng ồn tăng → App tự động tăng âm lượng.
 * Khi xe dừng/chạy chậm → âm lượng trở về mức cơ bản.
 */
class SpeedVolumeService : Service(), LocationListener {

    private lateinit var locationManager: LocationManager
    private lateinit var audioManager: AudioManager
    
    private var maxSystemVolume: Int = 15
    private var curveExponent = 2.0
    
    // SDV Settings
    private var sensitivity: String = "mid" // low, mid, high
    private var baseVolumePercent: Int = -1 // Volume khi bắt đầu service
    private var currentSpeedKmh: Float = 0f
    private var lastVolumeBoost: Int = 0
    
    companion object {
        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "sdv_channel"
        
        // Sensitivity configurations: km/h required for each +5% boost
        private const val SENSITIVITY_LOW_SPEED_PER_BOOST = 30f   // +5% mỗi 30 km/h
        private const val SENSITIVITY_MID_SPEED_PER_BOOST = 20f   // +5% mỗi 20 km/h
        private const val SENSITIVITY_HIGH_SPEED_PER_BOOST = 10f  // +5% mỗi 10 km/h
        
        private const val BOOST_STEP = 5       // +5% per step
        private const val MAX_BOOST = 20       // Maximum 20% boost
        
        // Location update settings
        private const val MIN_TIME_MS = 2000L       // Update every 2 seconds
        private const val MIN_DISTANCE_M = 5f       // Update every 5 meters
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        
        // Initialize managers
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        maxSystemVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        
        // Load settings
        loadSettings()
        
        // Start foreground
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        
        // Start GPS listening
        startLocationUpdates()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Ensure foreground
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForeground(NOTIFICATION_ID, createNotification())
        }
        
        // Reload settings
        loadSettings()
        
        // Capture base volume if not set
        if (baseVolumePercent < 0) {
            val currentSystemVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            baseVolumePercent = systemToPercent(currentSystemVol)
        }
        
        return START_STICKY
    }
    
    private fun loadSettings() {
        val prefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        curveExponent = prefs.getFloat("curve_exponent", 2.0f).toDouble()
        sensitivity = prefs.getString("sdv_sensitivity", "mid") ?: "mid"
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Speed Volume",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Âm lượng theo tốc độ xe"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val speedText = "%.0f km/h".format(currentSpeedKmh)
        val boostText = if (lastVolumeBoost > 0) " (+$lastVolumeBoost%)" else ""
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SDV Active")
            .setContentText("$speedText$boostText • Sensitivity: ${sensitivity.uppercase()}")
            .setSmallIcon(R.drawable.ic_speed)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
    
    private fun updateNotification() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, createNotification())
    }
    
    private fun startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
            != PackageManager.PERMISSION_GRANTED) {
            // No permission, stop service
            stopSelf()
            return
        }
        
        try {
            // Try GPS provider first
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    MIN_TIME_MS,
                    MIN_DISTANCE_M,
                    this
                )
            }
            
            // Also use network provider as fallback
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    MIN_TIME_MS,
                    MIN_DISTANCE_M,
                    this
                )
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
            stopSelf()
        }
    }
    
    // LocationListener callbacks
    override fun onLocationChanged(location: Location) {
        // Get speed in m/s, convert to km/h
        val speedMs = if (location.hasSpeed()) location.speed else 0f
        currentSpeedKmh = speedMs * 3.6f
        
        // Calculate volume boost
        val boost = calculateVolumeBoost(currentSpeedKmh)
        
        // Apply volume if boost changed
        if (boost != lastVolumeBoost) {
            lastVolumeBoost = boost
            applyVolumeBoost(boost)
        }
        
        // Update notification
        updateNotification()
    }
    
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}
    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    
    /**
     * Tính volume boost dựa trên tốc độ và sensitivity
     * 
     * Formula: boost = (speed / speedPerBoost) * BOOST_STEP
     * Capped at MAX_BOOST
     */
    private fun calculateVolumeBoost(speedKmh: Float): Int {
        val speedPerBoost = when (sensitivity) {
            "low" -> SENSITIVITY_LOW_SPEED_PER_BOOST
            "mid" -> SENSITIVITY_MID_SPEED_PER_BOOST
            "high" -> SENSITIVITY_HIGH_SPEED_PER_BOOST
            else -> SENSITIVITY_MID_SPEED_PER_BOOST
        }
        
        // Calculate boost steps
        val boostSteps = (speedKmh / speedPerBoost).toInt()
        val totalBoost = boostSteps * BOOST_STEP
        
        // Cap at max boost
        return totalBoost.coerceIn(0, MAX_BOOST)
    }
    
    /**
     * Apply volume boost to system
     */
    private fun applyVolumeBoost(boostPercent: Int) {
        if (baseVolumePercent < 0) return
        
        // Calculate new volume percent
        val newPercent = (baseVolumePercent + boostPercent).coerceIn(0, 100)
        
        // Convert to system volume and apply
        val systemVolume = percentToSystem(newPercent)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, systemVolume, 0)
    }
    
    /**
     * Reset volume to base level (when service stops)
     */
    private fun resetVolume() {
        if (baseVolumePercent >= 0) {
            val systemVolume = percentToSystem(baseVolumePercent)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, systemVolume, 0)
        }
    }
    
    // Volume conversion functions (same as MainActivity/FloatingService)
    private fun percentToSystem(percent: Int): Int {
        if (percent <= 0) return 0
        if (percent >= 100) return maxSystemVolume
        val normalizedPercent = percent / 100.0
        val curvedValue = normalizedPercent.pow(curveExponent)
        return (curvedValue * maxSystemVolume).roundToInt().coerceIn(0, maxSystemVolume)
    }

    private fun systemToPercent(systemVolume: Int): Int {
        if (systemVolume <= 0) return 0
        if (systemVolume >= maxSystemVolume) return 100
        val normalizedSystem = systemVolume.toDouble() / maxSystemVolume
        val percent = normalizedSystem.pow(1.0 / curveExponent) * 100
        return percent.roundToInt().coerceIn(0, 100)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // Stop location updates
        locationManager.removeUpdates(this)
        
        // Reset volume to base level
        resetVolume()
    }
}
