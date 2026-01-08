package com.volumeeditor

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.pow
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var audioManager: AudioManager
    private lateinit var volumeSeekBar: SeekBar
    private lateinit var volumeText: TextView
    private lateinit var floatingToggle: Button
    
    private var maxSystemVolume: Int = 15
    private var curveExponent = 2.0
    private lateinit var btnSettings: LinearLayout
    private lateinit var btnHome: LinearLayout
    
    companion object {
        private const val OVERLAY_PERMISSION_CODE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        applyImmersiveMode()

        // Khởi tạo AudioManager
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        maxSystemVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

        // Liên kết UI
        volumeSeekBar = findViewById(R.id.volumeSeekBar)
        volumeText = findViewById(R.id.volumeText)
        floatingToggle = findViewById(R.id.floatingToggle)
        btnSettings = findViewById(R.id.btnSettings)
        btnHome = findViewById(R.id.btnHome)
        
        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        
        // Home button - minimize app to background
        btnHome.setOnClickListener {
            moveTaskToBack(true)
        }

        // Cấu hình SeekBar với 100 bước
        volumeSeekBar.max = 100
        
        // Đọc âm lượng hiện tại và chuyển đổi ngược
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val currentPercent = systemToPercent(currentVolume)
        volumeSeekBar.progress = currentPercent
        updateVolumeText(currentPercent)

        // Xử lý khi người dùng kéo slider
        volumeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    setVolumePercent(progress)
                }
                updateVolumeText(progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Thiết lập các nút preset
        setupPresetButtons()
        
        // Thiết lập nút floating toggle
        setupFloatingToggle()
        
        // Update floating toggle text
        updateFloatingToggleText()
    }

    override fun onResume() {
        super.onResume()
        applyImmersiveMode() // Failsafe for Android Box
        // Refresh preferences
        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        curveExponent = prefs.getFloat("curve_exponent", 2.0f).toDouble()
        
        // Refresh volume khi quay lại app
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val currentPercent = systemToPercent(currentVolume)
        volumeSeekBar.progress = currentPercent
        updateVolumeText(currentPercent)
        updateFloatingToggleText()
    }

    private fun setupFloatingToggle() {
        floatingToggle.setOnClickListener {
            if (isFloatingServiceRunning()) {
                stopFloatingService()
            } else {
                checkOverlayPermissionAndStart()
            }
        }
    }

    private fun checkOverlayPermissionAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                // Request permission
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivityForResult(intent, OVERLAY_PERMISSION_CODE)
                Toast.makeText(this, "Vui lòng cấp quyền hiển thị trên ứng dụng khác", Toast.LENGTH_LONG).show()
            } else {
                startFloatingService()
            }
        } else {
            startFloatingService()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OVERLAY_PERMISSION_CODE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
                startFloatingService()
            } else {
                Toast.makeText(this, "Cần quyền overlay để hiển thị nút nổi", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startFloatingService() {
        val intent = Intent(this, FloatingService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        updateFloatingToggleText()
        Toast.makeText(this, "Đã bật nút nổi", Toast.LENGTH_SHORT).show()
    }

    private fun stopFloatingService() {
        stopService(Intent(this, FloatingService::class.java))
        updateFloatingToggleText()
        Toast.makeText(this, "Đã tắt nút nổi", Toast.LENGTH_SHORT).show()
    }

    private fun isFloatingServiceRunning(): Boolean {
        val manager = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
        @Suppress("DEPRECATION")
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (FloatingService::class.java.name == service.service.className) {
                return true
            }
        }
        return false
    }

    private fun updateFloatingToggleText() {
        floatingToggle.text = if (isFloatingServiceRunning()) {
            getString(R.string.label_float_widget_off)
        } else {
            getString(R.string.label_float_widget_on)
        }
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

    private fun setVolumePercent(percent: Int) {
        val systemVolume = percentToSystem(percent)
        audioManager.setStreamVolume(
            AudioManager.STREAM_MUSIC,
            systemVolume,
            0
        )
    }

    private fun updateVolumeText(percent: Int) {
        val systemVolume = percentToSystem(percent)
        val text = "$percent%"
        volumeText.text = text
    }

    private fun setupPresetButtons() {
        findViewById<Button>(R.id.btn0).setOnClickListener { setPreset(0) }
        findViewById<Button>(R.id.btn10).setOnClickListener { setPreset(10) }
        findViewById<Button>(R.id.btn25).setOnClickListener { setPreset(25) }
        findViewById<Button>(R.id.btn50).setOnClickListener { setPreset(50) }
        findViewById<Button>(R.id.btn75).setOnClickListener { setPreset(75) }
        findViewById<Button>(R.id.btn100).setOnClickListener { setPreset(100) }
        
        findViewById<Button>(R.id.btnMinus).setOnClickListener {
            val newProgress = (volumeSeekBar.progress - 1).coerceAtLeast(0)
            volumeSeekBar.progress = newProgress
            setVolumePercent(newProgress)
            updateVolumeText(newProgress)
        }
        
        findViewById<Button>(R.id.btnPlus).setOnClickListener {
            val newProgress = (volumeSeekBar.progress + 1).coerceAtMost(100)
            volumeSeekBar.progress = newProgress
            setVolumePercent(newProgress)
            updateVolumeText(newProgress)
        }
    }

    private fun setPreset(percent: Int) {
        volumeSeekBar.progress = percent
        setVolumePercent(percent)
        updateVolumeText(percent)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            applyImmersiveMode()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Minimize app instead of closing - keep running in background
        moveTaskToBack(true)
    }
}
