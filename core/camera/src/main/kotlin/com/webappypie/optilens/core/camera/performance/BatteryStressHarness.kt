package com.webappypie.optilens.core.camera.performance

import com.webappypie.optilens.core.camera.thermal.DeviceThermalMonitor
import com.webappypie.optilens.core.camera.thermal.DeviceThermalState
import com.webappypie.optilens.core.common.performance.MemoryProfileManager
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * Battery and thermal stress harness simulating repeated capture loops.
 * Measures battery drain rate (%/hour), thermal status progression, and native/JVM heap stability.
 */
@Singleton
class BatteryStressHarness @Inject constructor(
    private val thermalMonitor: DeviceThermalMonitor,
    private val memoryManager: MemoryProfileManager,
) {

    data class StressTestConfig(
        val shotCount: Int = 50,
        val intervalMs: Long = 2000L,
    )

    data class CaptureSample(
        val shotIndex: Int,
        val timestampMs: Long,
        val heapAllocatedMb: Float,
        val thermalState: DeviceThermalState,
        val batteryPct: Float,
    )

    data class StressTestReport(
        val completedShots: Int,
        val durationMs: Long,
        val initialBatteryPct: Float,
        val finalBatteryPct: Float,
        val drainPerHourPct: Float,
        val initialHeapMb: Float,
        val peakHeapMb: Float,
        val finalHeapMb: Float,
        val thermalProgression: List<DeviceThermalState>,
        val isHeapStable: Boolean,
        val isSuccess: Boolean,
        val errorReason: String? = null,
    )

    /**
     * Executes an automated stress loop with the specified capture simulator.
     *
     * @param config Stress test loop configuration
     * @param batteryProvider Function returning current battery percentage (0..100)
     * @param captureSimulator Lambda invoked on each shot
     */
    suspend fun runStressLoop(
        config: StressTestConfig = StressTestConfig(),
        batteryProvider: () -> Float = { 100f },
        captureSimulator: suspend (Int) -> Unit = { delay(50L) },
    ): StressTestReport {
        val samples = mutableListOf<CaptureSample>()
        val startTimestamp = System.currentTimeMillis()
        val initialBattery = batteryProvider()
        val initialSnapshot = memoryManager.sampleMemorySnapshot()
        val initialHeap = initialSnapshot.javaHeapAllocatedMb
        var peakHeap = initialHeap
        var currentBattery = initialBattery

        val thermalProgression = mutableListOf<DeviceThermalState>()
        thermalProgression.add(thermalMonitor.thermalState.value)

        var completedShots = 0
        var failureReason: String? = null

        try {
            for (i in 1..config.shotCount) {
                // Simulate capture
                captureSimulator(i)
                completedShots++

                val now = System.currentTimeMillis()
                val snapshot = memoryManager.sampleMemorySnapshot()
                peakHeap = max(peakHeap, snapshot.javaHeapAllocatedMb)
                val currentThermal = thermalMonitor.thermalState.value

                if (thermalProgression.lastOrNull() != currentThermal) {
                    thermalProgression.add(currentThermal)
                }

                currentBattery = batteryProvider()
                samples.add(
                    CaptureSample(
                        shotIndex = i,
                        timestampMs = now,
                        heapAllocatedMb = snapshot.javaHeapAllocatedMb,
                        thermalState = currentThermal,
                        batteryPct = currentBattery,
                    )
                )

                if (i < config.shotCount && config.intervalMs > 0L) {
                    delay(config.intervalMs)
                }
            }
        } catch (e: Throwable) {
            failureReason = e.message ?: "Unknown stress loop failure"
        }

        val endTimestamp = System.currentTimeMillis()
        val totalDurationMs = max(1L, endTimestamp - startTimestamp)
        val durationHours = totalDurationMs.toFloat() / (1000f * 3600f)

        val totalBatteryDrop = max(0f, initialBattery - currentBattery)
        val drainPerHour = if (durationHours > 0.0001f) totalBatteryDrop / durationHours else 0f

        val finalSnapshot = memoryManager.sampleMemorySnapshot()
        val finalHeap = finalSnapshot.javaHeapAllocatedMb

        // Heap is considered stable if final heap does not explode beyond 2.5x initial heap
        val isHeapStable = finalHeap <= max(initialHeap * 2.5f, 64f)
        val isSuccess = failureReason == null && completedShots == config.shotCount

        return StressTestReport(
            completedShots = completedShots,
            durationMs = totalDurationMs,
            initialBatteryPct = initialBattery,
            finalBatteryPct = currentBattery,
            drainPerHourPct = drainPerHour,
            initialHeapMb = initialHeap,
            peakHeapMb = peakHeap,
            finalHeapMb = finalHeap,
            thermalProgression = thermalProgression,
            isHeapStable = isHeapStable,
            isSuccess = isSuccess,
            errorReason = failureReason,
        )
    }
}
