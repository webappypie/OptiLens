package com.webappypie.optilens.core.ui.camera.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Sensor-driven horizon level detector providing smoothed device tilt/roll
 * angle in degrees.
 *
 * Utilizes [Sensor.TYPE_ROTATION_VECTOR] with fallback to [Sensor.TYPE_GRAVITY]
 * or [Sensor.TYPE_ACCELEROMETER], filtered through a low-pass filter for jitter-free UI rendering.
 */
class HorizonSensor(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    private val sensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        ?: sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
        ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val _rollDegrees = MutableStateFlow(0f)
    val rollDegrees: StateFlow<Float> = _rollDegrees.asStateFlow()

    private var smoothedRoll = 0f
    private val alpha = 0.2f // Low-pass filter smoothing coefficient
    private var isListening = false

    private val rotationMatrix = FloatArray(9)
    private val orientation = FloatArray(3)

    fun start() {
        if (isListening || sensor == null || sensorManager == null) return
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
        isListening = true
    }

    fun stop() {
        if (!isListening || sensorManager == null) return
        sensorManager.unregisterListener(this)
        isListening = false
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        val rawRoll: Float = when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientation)
                // orientation[2] is roll in radians
                Math.toDegrees(orientation[2].toDouble()).toFloat()
            }
            Sensor.TYPE_GRAVITY, Sensor.TYPE_ACCELEROMETER -> {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                // Roll = atan2(x, sqrt(y^2 + z^2))
                Math.toDegrees(atan2(x.toDouble(), sqrt((y * y + z * z).toDouble()))).toFloat()
            }
            else -> 0f
        }

        // Apply low-pass smoothing
        smoothedRoll = (alpha * rawRoll) + ((1f - alpha) * smoothedRoll)
        _rollDegrees.value = smoothedRoll
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
