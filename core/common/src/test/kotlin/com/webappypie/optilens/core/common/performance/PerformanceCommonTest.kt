package com.webappypie.optilens.core.common.performance

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PerformanceCommonTest {

    private lateinit var startupTracker: StartupMetricsTracker
    private lateinit var memoryManager: MemoryProfileManager
    private lateinit var frameCollector: FrameMetricsCollector
    private lateinit var concurrencyLimiter: ProcessingConcurrencyLimiter

    @Before
    fun setUp() {
        startupTracker = StartupMetricsTracker()
        memoryManager = MemoryProfileManager()
        frameCollector = FrameMetricsCollector()
        concurrencyLimiter = ProcessingConcurrencyLimiter()
    }

    // ==========================================
    // 1. StartupMetricsTracker Tests
    // ==========================================

    @Test
    fun `startupTracker computes latency breakdown accurately`() = runBlocking {
        val t0 = 1000L
        val tApp = 1050L
        val tActivity = 1120L
        val tFirstDraw = 1250L
        val tCameraFrame = 1400L

        startupTracker.recordProcessStart(t0)
        startupTracker.recordAppCreate(tApp)
        startupTracker.recordActivityCreate(tActivity)
        startupTracker.recordFirstDraw(tFirstDraw)
        startupTracker.recordCameraFirstFrame(tCameraFrame)

        val metrics = startupTracker.metrics.value
        assertTrue(metrics.isStartupComplete)
        assertTrue(metrics.isCameraReady)
        assertEquals(250L, metrics.appStartupLatencyMs)        // 1250 - 1000
        assertEquals(400L, metrics.cameraReadyLatencyMs)      // 1400 - 1000
        assertEquals(280L, metrics.viewfinderReadyLatencyMs)  // 1400 - 1120
    }

    @Test
    fun `startupTracker reset clears state`() {
        startupTracker.recordProcessStart(500L)
        startupTracker.recordFirstDraw(900L)
        assertTrue(startupTracker.metrics.value.isStartupComplete)

        startupTracker.reset()
        assertFalse(startupTracker.metrics.value.isStartupComplete)
        assertEquals(0L, startupTracker.metrics.value.appStartupLatencyMs)
    }

    // ==========================================
    // 2. MemoryProfileManager Tests
    // ==========================================

    @Test
    fun `memoryManager samples memory and triggers pressure when above 80 percent`() {
        var pressureReported: Boolean? = null
        memoryManager.addPressureListener { isPressure -> pressureReported = isPressure }

        // 1. Normal state: 100MB allocated of 256MB max (39% utilization)
        val snapshotNormal = memoryManager.sampleMemorySnapshot(
            customTotalMb = 120f,
            customFreeMb = 20f,
            customMaxMb = 256f,
            customNativeMb = 15f,
        )

        assertEquals(100f, snapshotNormal.javaHeapAllocatedMb, 0.01f)
        assertEquals(256f, snapshotNormal.javaHeapMaxMb, 0.01f)
        assertFalse(snapshotNormal.isUnderPressure)
        assertNull(pressureReported)

        // 2. High memory pressure: 220MB allocated of 256MB max (85.9% utilization)
        val snapshotHigh = memoryManager.sampleMemorySnapshot(
            customTotalMb = 230f,
            customFreeMb = 10f,
            customMaxMb = 256f,
            customNativeMb = 30f,
        )

        assertEquals(220f, snapshotHigh.javaHeapAllocatedMb, 0.01f)
        assertTrue(snapshotHigh.isUnderPressure)
        assertEquals(true, pressureReported)
        assertEquals(220f, snapshotHigh.peakHeapObservedMb, 0.01f)

        // 3. Pressure subsides back to normal
        val snapshotRecovered = memoryManager.sampleMemorySnapshot(
            customTotalMb = 120f,
            customFreeMb = 20f,
            customMaxMb = 256f,
            customNativeMb = 15f,
        )
        assertFalse(snapshotRecovered.isUnderPressure)
        assertEquals(false, pressureReported)
    }

    @Test
    fun `memoryManager responds to system onTrimMemory signals`() {
        memoryManager.onTrimMemory(level = 15) // TRIM_MEMORY_RUNNING_CRITICAL
        assertTrue(memoryManager.currentSnapshot.value.isUnderPressure)
        assertEquals(15, memoryManager.currentSnapshot.value.lastTrimLevel)
    }

    // ==========================================
    // 3. FrameMetricsCollector Tests
    // ==========================================

    @Test
    fun `frameCollector computes jank percentage and percentiles`() {
        // Feed 10 frames: 8 smooth frames (10ms) and 2 janky frames (25ms, 40ms)
        repeat(8) { frameCollector.recordFrameDuration(10.0f) }
        frameCollector.recordFrameDuration(25.0f) // Jank (> 16.6ms)
        frameCollector.recordFrameDuration(40.0f) // Severe Jank (> 33.3ms)

        val metrics = frameCollector.metrics.value
        assertEquals(10L, metrics.totalFrames)
        assertEquals(2L, metrics.jankFrames)
        assertEquals(1L, metrics.severeJankFrames)
        assertEquals(20.0f, metrics.jankRatePercent, 0.01f) // 2 / 10 = 20%
        assertEquals(10.0f, metrics.p50FrameDurationMs, 0.01f)
        assertTrue("p99 should capture the tail latency: ${metrics.p99FrameDurationMs}", metrics.p99FrameDurationMs >= 25.0f)
    }

    // ==========================================
    // 4. ProcessingConcurrencyLimiter Tests
    // ==========================================

    @Test
    fun `concurrencyLimiter enforces bounded parallel executions`() = runBlocking {
        concurrencyLimiter.setMaxConcurrency(1)

        val deferred1 = CompletableDeferred<Unit>()
        val deferred2 = CompletableDeferred<Unit>()
        var op1Executed = false
        var op2Executed = false

        val job1 = async {
            concurrencyLimiter.withPermit {
                op1Executed = true
                deferred1.await()
            }
        }

        // Give job1 a moment to acquire the permit
        delay(20)
        assertTrue("Op1 must have acquired permit", op1Executed)
        assertEquals(1, concurrencyLimiter.activeOperations.value)

        val job2 = async {
            concurrencyLimiter.withPermit {
                op2Executed = true
                deferred2.await()
            }
        }

        delay(20)
        assertFalse("Op2 must wait in queue because limit is 1", op2Executed)
        assertEquals(1, concurrencyLimiter.queuedOperations.value)

        // Release job1
        deferred1.complete(Unit)
        job1.await()

        delay(20)
        assertTrue("Op2 should now execute after Op1 released", op2Executed)
        deferred2.complete(Unit)
        job2.await()

        assertEquals(0, concurrencyLimiter.activeOperations.value)
        assertEquals(0, concurrencyLimiter.queuedOperations.value)
    }
}
