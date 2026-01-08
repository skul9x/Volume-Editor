package com.volumeeditor

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import android.app.ActivityManager
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class SettingsActivity : AppCompatActivity() {

    companion object {
        private const val LOCATION_PERMISSION_CODE = 2001
    }
    
    // Flag to prevent recursive toggle listener
    private var isUpdatingSdvToggle = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        applyImmersiveMode()

        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)

        // Back Button
        findViewById<ImageView>(R.id.btnBack).setOnClickListener {
            finish()
        }

        // Helper to update FloatingService if running
        fun updateFloatingServiceIfRunning() {
            if (isFloatingServiceRunning()) {
                val intent = Intent(this, FloatingService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
            }
        }

        // --- 1. Audio Curve ---
        val currentCurve = prefs.getFloat("curve_exponent", 2.0f)
        val radioGroupCurve = findViewById<RadioGroup>(R.id.radioGroupCurve)
        
        when (currentCurve) {
            1.0f -> radioGroupCurve.check(R.id.radioLinear)
            2.0f -> radioGroupCurve.check(R.id.radioBalanced)
            3.0f -> radioGroupCurve.check(R.id.radioDeep)
            else -> radioGroupCurve.check(R.id.radioBalanced)
        }

        radioGroupCurve.setOnCheckedChangeListener { _, checkedId ->
            val value = when (checkedId) {
                R.id.radioLinear -> 1.0f
                R.id.radioBalanced -> 2.0f
                R.id.radioDeep -> 3.0f
                else -> 2.0f
            }
            prefs.edit().putFloat("curve_exponent", value).apply()
            updateFloatingServiceIfRunning()
        }

        // --- 2. Timeout ---
        val currentTimeout = prefs.getLong("slider_timeout", 5000L)
        val radioGroupTimeout = findViewById<RadioGroup>(R.id.radioGroupTimeout)
        
        when (currentTimeout) {
            3000L -> radioGroupTimeout.check(R.id.radio3s)
            5000L -> radioGroupTimeout.check(R.id.radio5s)
            10000L -> radioGroupTimeout.check(R.id.radio10s)
            else -> radioGroupTimeout.check(R.id.radio5s)
        }

        radioGroupTimeout.setOnCheckedChangeListener { _, checkedId ->
            val value = when (checkedId) {
                R.id.radio3s -> 3000L
                R.id.radio5s -> 5000L
                R.id.radio10s -> 10000L
                else -> 5000L
            }
            prefs.edit().putLong("slider_timeout", value).apply()
            updateFloatingServiceIfRunning()
        }
        
        // --- 3. Opacity ---
        val currentOpacity = prefs.getFloat("widget_opacity", 1.0f)
        val opacitySeekBar = findViewById<SeekBar>(R.id.opacitySeekBar)
        val opacityValueText = findViewById<TextView>(R.id.opacityValueText)
        
        opacitySeekBar.progress = (currentOpacity * 100).toInt()
        opacityValueText?.text = "${(currentOpacity * 100).toInt()}%"
        
        opacitySeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val opacity = progress / 100.0f
                    val safeOpacity = if (opacity < 0.2f) 0.2f else opacity
                    
                    // Update Text UI
                    opacityValueText?.text = "${progress}%"
                    
                    // Save and Broadcast immediately
                    prefs.edit().putFloat("widget_opacity", safeOpacity).apply()
                    
                    val intent = Intent("com.volumeeditor.UPDATE_OPACITY")
                    intent.putExtra("opacity", safeOpacity)
                    sendBroadcast(intent)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // --- 4. Speed-Dependent Volume (SDV) ---
        val sdvToggle = findViewById<SwitchCompat>(R.id.sdvToggle)
        val sdvStatusText = findViewById<TextView>(R.id.sdvStatusText)
        val sdvSensitivityContainer = findViewById<LinearLayout>(R.id.sdvSensitivityContainer)
        val radioGroupSdvSensitivity = findViewById<RadioGroup>(R.id.radioGroupSdvSensitivity)

        // Load current SDV state
        val sdvEnabled = prefs.getBoolean("sdv_enabled", false)
        val currentSensitivity = prefs.getString("sdv_sensitivity", "mid") ?: "mid"

        // Initialize UI state
        sdvToggle.isChecked = sdvEnabled && isSpeedServiceRunning()
        updateSdvUiState(sdvToggle.isChecked, sdvStatusText, sdvSensitivityContainer)

        // Set sensitivity radio
        when (currentSensitivity) {
            "low" -> radioGroupSdvSensitivity.check(R.id.radioSdvLow)
            "mid" -> radioGroupSdvSensitivity.check(R.id.radioSdvMid)
            "high" -> radioGroupSdvSensitivity.check(R.id.radioSdvHigh)
            else -> radioGroupSdvSensitivity.check(R.id.radioSdvMid)
        }

        // SDV Toggle listener
        sdvToggle.setOnCheckedChangeListener { _, isChecked ->
            // Prevent recursive calls when programmatically setting isChecked
            if (isUpdatingSdvToggle) return@setOnCheckedChangeListener
            
            if (isChecked) {
                // Check location permission
                if (hasLocationPermission()) {
                    startSpeedVolumeService()
                    prefs.edit().putBoolean("sdv_enabled", true).apply()
                    updateSdvUiState(true, sdvStatusText, sdvSensitivityContainer)
                } else {
                    // Request permission - temporarily disable listener
                    isUpdatingSdvToggle = true
                    sdvToggle.isChecked = false
                    isUpdatingSdvToggle = false
                    requestLocationPermission()
                }
            } else {
                stopSpeedVolumeService()
                prefs.edit().putBoolean("sdv_enabled", false).apply()
                updateSdvUiState(false, sdvStatusText, sdvSensitivityContainer)
            }
        }

        // Sensitivity change listener
        radioGroupSdvSensitivity.setOnCheckedChangeListener { _, checkedId ->
            val sensitivity = when (checkedId) {
                R.id.radioSdvLow -> "low"
                R.id.radioSdvMid -> "mid"
                R.id.radioSdvHigh -> "high"
                else -> "mid"
            }
            prefs.edit().putString("sdv_sensitivity", sensitivity).apply()
            
            // Update service if running
            if (isSpeedServiceRunning()) {
                startSpeedVolumeService() // Restart to apply new settings
            }
        }
    }

    private fun updateSdvUiState(enabled: Boolean, statusText: TextView, sensitivityContainer: LinearLayout) {
        if (enabled) {
            statusText.text = getString(R.string.sdv_status_on)
            statusText.setTextColor(0xFF00FF00.toInt()) // Green
            sensitivityContainer.visibility = View.VISIBLE
        } else {
            statusText.text = getString(R.string.sdv_status_off)
            statusText.setTextColor(0xFF888888.toInt()) // Gray
            sensitivityContainer.visibility = View.GONE
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, 
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            LOCATION_PERMISSION_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == LOCATION_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, enable SDV - use flag to prevent loop
                val sdvToggle = findViewById<SwitchCompat>(R.id.sdvToggle)
                isUpdatingSdvToggle = true
                sdvToggle.isChecked = true
                isUpdatingSdvToggle = false
                
                // Manually trigger service start since listener was bypassed
                startSpeedVolumeService()
                val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                prefs.edit().putBoolean("sdv_enabled", true).apply()
                val sdvStatusText = findViewById<TextView>(R.id.sdvStatusText)
                val sdvSensitivityContainer = findViewById<LinearLayout>(R.id.sdvSensitivityContainer)
                updateSdvUiState(true, sdvStatusText, sdvSensitivityContainer)
            } else {
                Toast.makeText(this, R.string.sdv_permission_required, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startSpeedVolumeService() {
        val intent = Intent(this, SpeedVolumeService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopSpeedVolumeService() {
        stopService(Intent(this, SpeedVolumeService::class.java))
    }

    private fun isSpeedServiceRunning(): Boolean {
        val manager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        @Suppress("DEPRECATION")
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (SpeedVolumeService::class.java.name == service.service.className) {
                return true
            }
        }
        return false
    }
    
    private fun isFloatingServiceRunning(): Boolean {
        val manager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        @Suppress("DEPRECATION")
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (FloatingService::class.java.name == service.service.className) {
                return true
            }
        }
        return false
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            applyImmersiveMode()
        }
    }
}
