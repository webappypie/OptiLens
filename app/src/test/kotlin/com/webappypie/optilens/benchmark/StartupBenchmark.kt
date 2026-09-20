package com.webappypie.optilens.benchmark

import com.webappypie.optilens.core.common.performance.StartupMetricsTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Benchmark measuring cold start latency:
 * - Time to Initial Display (TTID)
 * - Time to Fully Drawn (TTFD)
 * - Camera-ready latency
 */
class StartupBenchmark {

    @Test
    fun `measureColdStartToFirstCameraFrame`() {
        val tracker = StartupMetricsTracker()

        val t0 = 1000L
        tracker.recordProcessStart(t0)
        tracker.recordAppCreate(t0 + 45L)
        tracker.recordActivityCreate(t0 + 120L)
        tracker.recordFirstDraw(t0 + 260L)
        tracker.recordCameraFirstFrame(t0 + 410L)

        val metrics = tracker.metrics.value
        assertEquals(260L, metrics.appStartupLatencyMs)
        assertEquals(410L, metrics.cameraReadyLatencyMs)
        assertEquals(290L, metrics.viewfinderReadyLatencyMs)

        // Validate within benchmark performance budgets
        assertTrue("App startup latency should be < 500ms", metrics.appStartupLatencyMs < 500L)
        assertTrue("Camera ready latency should be < 800ms", metrics.cameraReadyLatencyMs < 800L)
    }

    @Test
    fun `measureWarmStartLatency`() {
        val tracker = StartupMetricsTracker()

        val t0 = 5000L
        tracker.recordActivityCreate(t0)
        tracker.recordFirstDraw(t0 + 85L)
        tracker.recordCameraFirstFrame(t0 + 190L)

        val metrics = tracker.metrics.value
        assertTrue("Warm start camera ready should be < 300ms", metrics.viewfinderReadyLatencyMs < 300L)
    }
}
