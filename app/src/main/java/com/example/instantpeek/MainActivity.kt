package com.example.instantpeek

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ImageView
import android.graphics.PorterDuff
import kotlin.math.abs

class MainActivity : Activity(), SensorEventListener {
    
    // UI Components
    private lateinit var rootLayout: LinearLayout
    private lateinit var textView: TextView
    private lateinit var thumbsIcon: ImageView
    
    // Sensors
    private lateinit var sensorManager: SensorManager
    private lateinit var accelerometer: Sensor
    
    // State management
    private var isRed = true
    private var currentBrightness = 0.2f
    private var lastMovementTime = System.currentTimeMillis()
    private val handler = Handler(Looper.getMainLooper())

    // Pixel shift for burn-in protection
    private var shiftX = 0
    private var shiftY = 0

    // Inversion cycle for burn-in protection
    private var isInverted = false
    
    // Configuration
    companion object {
        const val DIM_BRIGHTNESS = 0.2f      // 20% when idle
        const val FULL_BRIGHTNESS = 1.0f     // 100% when active
        const val MOTION_THRESHOLD = 2.0f    // Sensitivity to movement
        const val PEEK_DURATION = 10000L     // 10 seconds at full brightness
        const val SHIFT_INTERVAL = 60000L    // Shift pixels every minute
        const val COLOR_VARIANCE = 15        // ±15 RGB variation
        const val INVERSION_INTERVAL = 20000L // 20 seconds between inversions
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Critical: Keep screen on always
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        // Initialize sensors
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)!!
        
        // Setup UI
        setupUI()
        
        // Start with dim mode
        setBrightness(DIM_BRIGHTNESS)
        
        // Start pixel shifting timer
        startPixelShiftTimer()
        
        // Start color variation timer
        startColorVariationTimer()

        // Start inversion cycle for burn-in protection
        startInversionCycle()
    }
    
    private fun setupUI() {
        // Root layout
        rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getVariedColor(Color.RED))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            setPadding(shiftX, shiftY, 0, 0)
            setOnClickListener { toggleColors() }
        }
        
        // Full screen thumbs icon - no text
        thumbsIcon = ImageView(this).apply {
            setImageResource(if (isRed) R.drawable.thumbs_down else R.drawable.thumbs_up)
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }

        rootLayout.addView(thumbsIcon)
        setContentView(rootLayout)
    }
    
    private fun toggleColors() {
        isRed = !isRed
        thumbsIcon.setImageResource(if (isRed) R.drawable.thumbs_down else R.drawable.thumbs_up)

        // Update colors based on current inversion state
        if (isInverted) {
            // Currently inverted: black background, colored thumbs
            rootLayout.setBackgroundColor(Color.BLACK)
            thumbsIcon.setColorFilter(if (isRed) Color.RED else Color.GREEN, PorterDuff.Mode.SRC_IN)
        } else {
            // Currently normal: colored background, white thumbs
            rootLayout.setBackgroundColor(getVariedColor(if (isRed) Color.RED else Color.GREEN))
            thumbsIcon.clearColorFilter()
        }

        // Also trigger instant brightness on tap
        instantWakeUp()
    }
    
    private fun instantWakeUp() {
        lastMovementTime = System.currentTimeMillis()
        setBrightness(FULL_BRIGHTNESS)

        // Cancel any pending dim operations
        handler.removeCallbacksAndMessages(null)

        // Schedule dim after peek duration
        handler.postDelayed({
            dimScreen()
        }, PEEK_DURATION)
    }

    private fun dimScreen() {
        setBrightness(DIM_BRIGHTNESS)
    }
    
    private fun setBrightness(brightness: Float) {
        currentBrightness = brightness
        window.attributes = window.attributes.apply {
            screenBrightness = brightness
        }

        // Also adjust background alpha for additional dimming effect
        val alpha = (brightness * 255).toInt()
        rootLayout.background?.alpha = alpha
    }

    // Sensor handling
    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            // Calculate total movement
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            val movement = abs(x) + abs(y) + abs(z) - 9.8f // Subtract gravity

            // If significant movement detected
            if (abs(movement) > MOTION_THRESHOLD) {
                val now = System.currentTimeMillis()
                // Debounce - only trigger if we're currently dim
                if (currentBrightness < FULL_BRIGHTNESS &&
                    now - lastMovementTime > 1000) { // 1 second debounce
                    instantWakeUp()
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed
    }

    // Burn-in protection: Pixel shifting
    private fun startPixelShiftTimer() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                // Shift by small random amount
                shiftX = (Math.random() * 20 - 10).toInt() // -10 to +10 pixels
                shiftY = (Math.random() * 20 - 10).toInt()

                rootLayout.setPadding(
                    40 + shiftX,  // Base padding + shift
                    80 + shiftY,
                    40 - shiftX,
                    80 - shiftY
                )

                // Schedule next shift
                handler.postDelayed(this, SHIFT_INTERVAL)
            }
        }, SHIFT_INTERVAL)
    }

    // Burn-in protection: Color variation
    private fun startColorVariationTimer() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                // Update background with varied color
                val baseColor = if (isRed) Color.RED else Color.GREEN
                rootLayout.setBackgroundColor(getVariedColor(baseColor))

                // Schedule next variation
                handler.postDelayed(this, 5000) // Every 5 seconds
            }
        }, 5000)
    }

    private fun getVariedColor(baseColor: Int): Int {
        val variance = (Math.random() * COLOR_VARIANCE * 2 - COLOR_VARIANCE).toInt()

        return when(baseColor) {
            Color.RED -> {
                // Vary red channel slightly, add tiny amounts to others
                Color.rgb(
                    (255 - abs(variance)).coerceIn(200, 255),
                    abs(variance / 2).coerceIn(0, 30),
                    abs(variance / 2).coerceIn(0, 30)
                )
            }
            Color.GREEN -> {
                // Vary green channel slightly, add tiny amounts to others
                Color.rgb(
                    abs(variance / 2).coerceIn(0, 30),
                    (255 - abs(variance)).coerceIn(200, 255),
                    abs(variance / 2).coerceIn(0, 30)
                )
            }
            else -> baseColor
        }
    }

    // Burn-in protection: Inversion cycle
    private fun startInversionCycle() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                if (isInverted) {
                    // State 1: Normal - colored background, white thumbs
                    rootLayout.setBackgroundColor(getVariedColor(if (isRed) Color.RED else Color.GREEN))
                    thumbsIcon.clearColorFilter()
                } else {
                    // State 2: Inverted - black background, colored thumbs
                    rootLayout.setBackgroundColor(Color.BLACK)
                    thumbsIcon.setColorFilter(if (isRed) Color.RED else Color.GREEN, PorterDuff.Mode.SRC_IN)
                }

                isInverted = !isInverted
                handler.postDelayed(this, INVERSION_INTERVAL)
            }
        }, INVERSION_INTERVAL)
    }

    override fun onResume() {
        super.onResume()
        // Register sensor listener
        sensorManager.registerListener(
            this,
            accelerometer,
            SensorManager.SENSOR_DELAY_UI  // ~60ms updates
        )

        // Ensure we start in correct state
        instantWakeUp()
    }

    override fun onPause() {
        super.onPause()
        // Unregister sensor to save battery
        sensorManager.unregisterListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
