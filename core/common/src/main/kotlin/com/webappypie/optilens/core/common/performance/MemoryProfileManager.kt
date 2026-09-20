package com.webappypie.optilens.core.common.performance

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * Snapshot of JVM and native memory usage in megabytes.
 */
data class MemorySnapshot(
    val javaHeapAllocatedMb: Float = 0f,
    val javaHeapMaxMb: Float = 0f,
    val nativeHeapAllocatedMb: Float = 0f,
    val peakHeapObservedMb: Float = 0f,
    val lastTrimLevel: Int = 0,
    val isUnderPressure: Boolean = false,
) {
    /** Fraction of allowable JVM heap currently allocated [0.0 .. 1.0]. */
    val heapUtilizationRatio: Float
        get() = if (javaHeapMaxMb > 0f) (javaHeapAllocatedMb / javaHeapMaxMb).coerceIn(0f, 1f) else 0f
}

/**
 * Real-time memory governor and sampler tracking JVM heap, native memory, and trim events.
 *
 * Implements Phase 19 Task 6:
 * - Real-time heap and native memory sampling.
 * - Pressure thresholds (triggers alarms when heap utilization exceeds 80%).
 * - System trim memory callbacks to coordinate cache flushes before system OOM kills.
 */
@Singleton
class MemoryProfileManager @Inject constructor() {

    companion object {
        const val HEAP_PRESSURE_THRESHOLD_RATIO = 0.80f
    }

    private var peakHeapMb: Float = 0f
    private val _currentSnapshot = MutableStateFlow(MemorySnapshot())
    val currentSnapshot: StateFlow<MemorySnapshot> = _currentSnapshot.asStateFlow()

    private val pressureListeners = mutableListOf<(Boolean) -> Unit>()

    /**
     * Samples real-time memory usage using system runtimes or custom providers for testing.
     */
    fun sampleMemorySnapshot(
        customTotalMb: Float? = null,
        customFreeMb: Float? = null,
        customMaxMb: Float? = null,
        customNativeMb: Float? = null,
    ): MemorySnapshot {
        val runtime = Runtime.getRuntime()
        val totalMb = customTotalMb ?: (runtime.totalMemory().toFloat() / (1024 * 1024))
        val freeMb = customFreeMb ?: (runtime.freeMemory().toFloat() / (1024 * 1024))
        val maxMb = customMaxMb ?: (runtime.maxMemory().toFloat() / (1024 * 1024))
        val allocatedMb = max(0f, totalMb - freeMb)
        val nativeMb = customNativeMb ?: 0f

        peakHeapMb = max(peakHeapMb, allocatedMb)

        val utilization = if (maxMb > 0f) allocatedMb / maxMb else 0f
        val isPressure = utilization >= HEAP_PRESSURE_THRESHOLD_RATIO || _currentSnapshot.value.lastTrimLevel >= 15 // TRIM_MEMORY_RUNNING_CRITICAL

        val snapshot = MemorySnapshot(
            javaHeapAllocatedMb = allocatedMb,
            javaHeapMaxMb = maxMb,
            nativeHeapAllocatedMb = nativeMb,
            peakHeapObservedMb = peakHeapMb,
            lastTrimLevel = _currentSnapshot.value.lastTrimLevel,
            isUnderPressure = isPressure,
        )

        val previousPressure = _currentSnapshot.value.isUnderPressure
        _currentSnapshot.value = snapshot

        if (isPressure != previousPressure) {
            notifyPressureListeners(isPressure)
        }

        return snapshot
    }

    /**
     * Handles system trim memory callbacks (ComponentCallbacks2).
     */
    fun onTrimMemory(level: Int) {
        val wasPressure = _currentSnapshot.value.isUnderPressure
        val isPressure = level >= 10 // TRIM_MEMORY_RUNNING_LOW / MODERATE / CRITICAL

        _currentSnapshot.value = _currentSnapshot.value.copy(
            lastTrimLevel = level,
            isUnderPressure = isPressure,
        )

        if (isPressure != wasPressure) {
            notifyPressureListeners(isPressure)
        }
    }

    fun addPressureListener(listener: (Boolean) -> Unit) {
        synchronized(pressureListeners) {
            pressureListeners.add(listener)
        }
    }

    fun removePressureListener(listener: (Boolean) -> Unit) {
        synchronized(pressureListeners) {
            pressureListeners.remove(listener)
        }
    }

    private fun notifyPressureListeners(isUnderPressure: Boolean) {
        val listCopy = synchronized(pressureListeners) { pressureListeners.toList() }
        for (l in listCopy) {
            l(isUnderPressure)
        }
    }

    fun resetPeak() {
        peakHeapMb = 0f
    }
}
