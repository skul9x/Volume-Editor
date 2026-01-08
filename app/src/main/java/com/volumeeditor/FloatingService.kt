package com.volumeeditor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.app.NotificationCompat
import android.os.Handler
import android.os.Looper
import kotlin.math.pow
import kotlin.math.roundToInt

class FloatingService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var audioManager: AudioManager
    private var floatingView: View? = null
    private var expandedView: View? = null
    
    private val handler = Handler(Looper.getMainLooper())
    
    private var maxSystemVolume: Int = 15
    private var curveExponent = 2.0
    private var sliderTimeout = 5000L
    private var widgetOpacity = 1.0f
    
    // Tracking cho gesture
    private var lastTapTime: Long = 0
    private var tapCount = 0
    private val doubleTapTimeout = 300L // ms
    
    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "volume_editor_channel"
    }
    
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        
        // Start as foreground service
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        maxSystemVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        
        createFloatingButton()
        refreshSettings()
        
        // Register Broadcast Receiver for realtime settings updates
        val filter = android.content.IntentFilter().apply {
            addAction("com.volumeeditor.UPDATE_OPACITY")
            addAction("com.volumeeditor.UPDATE_SETTINGS")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(settingsReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(settingsReceiver, filter)
        }
    }
    
    private val settingsReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
            when (intent?.action) {
                "com.volumeeditor.UPDATE_OPACITY" -> {
                    val opacity = intent.getFloatExtra("opacity", 1.0f)
                    widgetOpacity = opacity
                    floatingView?.alpha = widgetOpacity
                }
                "com.volumeeditor.UPDATE_SETTINGS" -> {
                    refreshSettings()
                }
            }
        }
    }
    
    private fun refreshSettings() {
        val prefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        curveExponent = prefs.getFloat("curve_exponent", 2.0f).toDouble()
        sliderTimeout = prefs.getLong("slider_timeout", 5000L)
        widgetOpacity = prefs.getFloat("widget_opacity", 1.0f)
        
        // Update opacity immediately
        floatingView?.alpha = widgetOpacity
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Ensure we are in foreground (required if started via startForegroundService)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForeground(NOTIFICATION_ID, createNotification())
        }
        refreshSettings() // Reload settings
        return super.onStartCommand(intent, flags, startId)
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Volume Editor",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Điều khiển âm lượng nhanh"
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
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Volume Editor")
            .setContentText("Nhấn: Mute • Nhấn đúp: Mở app • Giữ: Quick slider")
            .setSmallIcon(R.drawable.ic_volume_on)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createFloatingButton() {
        // Inflate floating button layout
        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_button, null)
        
        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            x = 20
            y = 0
            
            // Apply initial opacity
            alpha = widgetOpacity // Note: window params alpha vs view alpha. View alpha is safer for complex layouts.
        }

        windowManager.addView(floatingView, params)
        floatingView?.alpha = widgetOpacity // Apply View Alpha
        
        // Setup touch listener cho floating button
        setupFloatingButtonTouch(params)
    }

    private fun setupFloatingButtonTouch(params: WindowManager.LayoutParams) {
        val floatingBtn = floatingView?.findViewById<ImageView>(R.id.floatingButton)
        
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isClick = true
        var longPressTriggered = false
        
        val longPressRunnable = Runnable {
            longPressTriggered = true
            // CHỨC NĂNG 3: Long press - Hiện quick slider
            showExpandedVolume()
        }
        
        floatingBtn?.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isClick = true
                    longPressTriggered = false
                    
                    // Schedule long press
                    handler.postDelayed(longPressRunnable, 500)
                    true
                }
                
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = (initialTouchX - event.rawX).toInt()
                    val deltaY = (event.rawY - initialTouchY).toInt()
                    
                    // Nếu di chuyển > 30px thì không phải click (Tăng ngưỡng để tránh rung tay trên xe)
                    if (kotlin.math.abs(deltaX) > 30 || kotlin.math.abs(deltaY) > 30) {
                        isClick = false
                        handler.removeCallbacks(longPressRunnable)
                    }
                    
                    params.x = initialX + deltaX
                    params.y = initialY + deltaY
                    windowManager.updateViewLayout(floatingView, params)
                    true
                }
                
                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(longPressRunnable)
                    
                    if (isClick && !longPressTriggered) {
                        handleTap()
                    }
                    true
                }
                
                else -> false
            }
        }
    }

    private fun handleTap() {
        val currentTime = System.currentTimeMillis()
        
        if (currentTime - lastTapTime < doubleTapTimeout) {
            tapCount++
        } else {
            tapCount = 1
        }
        lastTapTime = currentTime
        
        // Delay để check double tap
        handler.postDelayed({
            if (System.currentTimeMillis() - lastTapTime >= doubleTapTimeout) {
                when (tapCount) {
                    1 -> {
                        // CHỨC NĂNG 1: Single tap - Mute/Unmute
                        toggleMute()
                    }
                    2 -> {
                        // CHỨC NĂNG 2: Double tap - Mở app chính
                        openMainApp()
                    }
                }
                tapCount = 0
            }
        }, doubleTapTimeout + 50)
    }

    /**
     * CHỨC NĂNG 1: Toggle Mute/Unmute
     */
    private fun toggleMute() {
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        
        if (currentVolume > 0) {
            // Save current volume and mute
            getSharedPreferences("volume_prefs", MODE_PRIVATE)
                .edit()
                .putInt("saved_volume", currentVolume)
                .apply()
            
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
            updateFloatingIcon(true)
        } else {
            // Restore saved volume
            val savedVolume = getSharedPreferences("volume_prefs", MODE_PRIVATE)
                .getInt("saved_volume", maxSystemVolume / 2)
            
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, savedVolume, 0)
            updateFloatingIcon(false)
        }
    }

    /**
     * CHỨC NĂNG 2: Mở Main App
     */
    private fun openMainApp() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(intent)
    }

    /**
     * CHỨC NĂNG 3: Hiện Quick Volume Slider
     */
    private fun showExpandedVolume() {
        if (expandedView != null) {
            hideExpandedVolume()
            return
        }
        
        expandedView = LayoutInflater.from(this).inflate(R.layout.floating_expanded, null)
        
        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        windowManager.addView(expandedView, params)
        
        // Setup expanded view
        setupExpandedView()
        
        // Auto hide
        handler.postDelayed({
            hideExpandedVolume()
        }, sliderTimeout)
    }

    private fun setupExpandedView() {
        val seekBar = expandedView?.findViewById<SeekBar>(R.id.quickSeekBar)
        val volumeText = expandedView?.findViewById<TextView>(R.id.quickVolumeText)
        val closeBtn = expandedView?.findViewById<ImageView>(R.id.closeExpanded)
        
        // Set current volume
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val currentPercent = systemToPercent(currentVolume)
        seekBar?.progress = currentPercent
        volumeText?.text = "$currentPercent%"
        
        seekBar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val systemVolume = percentToSystem(progress)
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, systemVolume, 0)
                    updateFloatingIcon(progress == 0)
                }
                volumeText?.text = "$progress%"
                
                // Reset auto-hide timer
                handler.removeCallbacksAndMessages(null) // Clear previous hide tasks (simplification, careful not to clear gestures if overlapping)
                // Better approach: just post logic
                handler.postDelayed({
                    hideExpandedVolume()
                }, sliderTimeout)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        closeBtn?.setOnClickListener {
            hideExpandedVolume()
        }
        
        // Preset buttons
        expandedView?.findViewById<TextView>(R.id.preset25)?.setOnClickListener {
            setQuickPreset(25, seekBar, volumeText)
        }
        expandedView?.findViewById<TextView>(R.id.preset50)?.setOnClickListener {
            setQuickPreset(50, seekBar, volumeText)
        }
        expandedView?.findViewById<TextView>(R.id.preset75)?.setOnClickListener {
            setQuickPreset(75, seekBar, volumeText)
        }
    }

    private fun setQuickPreset(percent: Int, seekBar: SeekBar?, volumeText: TextView?) {
        seekBar?.progress = percent
        volumeText?.text = "$percent%"
        val systemVolume = percentToSystem(percent)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, systemVolume, 0)
        updateFloatingIcon(false)
    }

    private fun hideExpandedVolume() {
        expandedView?.let {
            windowManager.removeView(it)
            expandedView = null
        }
    }

    private fun updateFloatingIcon(isMuted: Boolean) {
        val floatingBtn = floatingView?.findViewById<ImageView>(R.id.floatingButton)
        floatingBtn?.setImageResource(
            if (isMuted) R.drawable.ic_volume_off else R.drawable.ic_volume_on
        )
    }

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
        try {
            unregisterReceiver(settingsReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        handler.removeCallbacksAndMessages(null)
        floatingView?.let { windowManager.removeView(it) }
        expandedView?.let { windowManager.removeView(it) }
    }
}
