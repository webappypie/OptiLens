package com.webappypie.optilens.core.camera.thermal

import android.content.Context
import android.os.Build
import android.os.PowerManager
import com.webappypie.optilens.core.logging.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "DeviceThermalMonitor"

/**
 * Monitors hardware thermal conditions in real-time.
 * Adapts computational workloads dynamically when thermal throttling is detected.
 */
@Singleton
class DeviceThermalMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: AppLogger,
) {
    private val _thermalState = MutableStateFlow(DeviceThermalState.NORMAL)
    val thermalState: StateFlow<DeviceThermalState> = _thermalState.asStateFlow()

    private val _policy = MutableStateFlow(ThermalDegradationPolicy.forThermalState(DeviceThermalState.NORMAL))
    val policy: StateFlow<ThermalDegradationPolicy> = _policy.asStateFlow()

    private var powerManager: PowerManager? = null
    private var thermalListener: Any? = null
    private var isMonitoring = false

    init {
        try {
            powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            startMonitoring()
        } catch (e: Throwable) {
            logger.w(TAG, "Thermal monitoring unavailable on this hardware/platform: ${e.message}")
        }
    }

    /**
     * Starts listening to system thermal status changes on API 29+.
     */
    fun startMonitoring() {
        if (isMonitoring) return
        val pm = powerManager ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val currentStatus = pm.currentThermalStatus
                updateThermalStatus(currentStatus)

                val listener = PowerManager.OnThermalStatusChangedListener { status ->
                    updateThermalStatus(status)
                }
                val directExecutor = Executor { it.run() }
                pm.addThermalStatusListener(directExecutor, listener)
                thermalListener = listener
                isMonitoring = true
                logger.i(TAG, "DeviceThermalMonitor started with initial status: ${_thermalState.value}")
            } catch (e: Exception) {
                logger.w(TAG, "Failed to register thermal status listener: ${e.message}")
            }
        } else {
            logger.d(TAG, "DeviceThermalMonitor: Platform API < 29, running in NORMAL baseline state")
        }
    }

    /**
     * Stops listening to system thermal status changes.
     */
    fun stopMonitoring() {
        if (!isMonitoring) return
        val pm = powerManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && pm != null && thermalListener != null) {
            try {
                pm.removeThermalStatusListener(thermalListener as PowerManager.OnThermalStatusChangedListener)
            } catch (e: Exception) {
                logger.w(TAG, "Failed to unregister thermal status listener: ${e.message}")
            }
        }
        thermalListener = null
        isMonitoring = false
    }

    /**
     * Manually override thermal status (e.g. for testing or thermal simulation).
     */
    fun setSimulatedThermalState(state: DeviceThermalState) {
        logger.i(TAG, "Thermal state simulated: $state")
        _thermalState.value = state
        _policy.value = ThermalDegradationPolicy.forThermalState(state)
    }

    private fun updateThermalStatus(status: Int) {
        val state = DeviceThermalState.fromPowerManagerStatus(status)
        if (_thermalState.value != state) {
            logger.w(TAG, "Device thermal transition: ${_thermalState.value} -> $state (HAL status code: $status)")
            _thermalState.value = state
            _policy.value = ThermalDegradationPolicy.forThermalState(state)
        }
    }
}
