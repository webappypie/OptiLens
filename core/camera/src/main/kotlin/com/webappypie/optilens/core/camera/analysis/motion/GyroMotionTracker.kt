package com.webappypie.optilens.core.camera.analysis.motion

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.webappypie.optilens.core.camera.model.CameraShakeLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sqrt

/**
 * Tracks physical device rotational motion and camera shake via the hardware gyroscope.
 *
 * Employs exponential moving average (EMA) smoothing to eliminate noise and categorize
 * stability for computational multi-frame stacking.
 */
class GyroMotionTracker(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val gyroSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    private val _angularVelocity = MutableStateFlow(0.0f)
    val angularVelocity: StateFlow<Float> = _angularVelocity.asStateFlow()

    private val _shakeLevel = MutableStateFlow(CameraShakeLevel.STABLE)
    val shakeLevel: StateFlow<CameraShakeLevel> = _shakeLevel.asStateFlow()

    private var smoothedMagnitude = 0.0f
    private val smoothingAlpha = 0.25f

    @Volatile
    var isRunning = false
        private set

    fun start() {
        if (isRunning || sensorManager == null || gyroSensor == null) return
        sensorManager.registerListener(this, gyroSensor, SensorManager.SENSOR_DELAY_GAME)
        isRunning = true
    }

    fun stop() {
        if (!isRunning) return
        sensorManager?.unregisterListener(this)
        isRunning = false
        smoothedMagnitude = 0.0f
        _angularVelocity.value = 0.0f
        _shakeLevel.value = CameraShakeLevel.STABLE
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_GYROSCOPE) return

        val wx = event.values[0]
        val wy = event.values[1]
        val wz = event.values[2]

        val rawMagnitude = sqrt(wx * wx + wy * wy + wz * wz)
        smoothedMagnitude = (smoothingAlpha * rawMagnitude) + ((1.0f - smoothingAlpha) * smoothedMagnitude)

        _angularVelocity.value = smoothedMagnitude
        _shakeLevel.value = when {
            smoothedMagnitude < 0.08f -> CameraShakeLevel.STABLE
            smoothedMagnitude < 0.25f -> CameraShakeLevel.MODERATE
            else -> CameraShakeLevel.HIGH
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
