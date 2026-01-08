package com.volumeeditor

import android.content.Context
import android.os.Bundle
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.content.Intent
import android.app.ActivityManager
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)

        // Back Button
        findViewById<ImageView>(R.id.btnBack).setOnClickListener {
            finish()
        }

        // Helper to update service if running
        fun updateServiceIfRunning() {
            if (isServiceRunning()) {
                val intent = Intent(this, FloatingService::class.java)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
            }
        }

        // --- 1. Audio Curve ---
        // Default: 2.0 (Balanced)
        // Saved as float string "1.0", "2.0", "3.0"
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
            updateServiceIfRunning()
        }

        // --- 2. Timeout ---
        // Default: 5000 (5s)
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
            updateServiceIfRunning()
        }
        
        // --- 3. Opacity ---
        // Default: 1.0 (100%)
        // Saved as float 0.2 -> 1.0
        val currentOpacity = prefs.getFloat("widget_opacity", 1.0f)
        val opacitySeekBar = findViewById<SeekBar>(R.id.opacitySeekBar)
        
        // Convert 0.0-1.0 to 0-100 progress
        opacitySeekBar.progress = (currentOpacity * 100).toInt()
        
        opacitySeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val opacity = progress / 100.0f
                    // Ensure min opacity 0.2
                    val safeOpacity = if (opacity < 0.2f) 0.2f else opacity
                    prefs.edit().putFloat("widget_opacity", safeOpacity).apply()
                    updateServiceIfRunning()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }
    
    private fun isServiceRunning(): Boolean {
        val manager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        @Suppress("DEPRECATION")
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (FloatingService::class.java.name == service.service.className) {
                return true
            }
        }
        return false
    }
}
