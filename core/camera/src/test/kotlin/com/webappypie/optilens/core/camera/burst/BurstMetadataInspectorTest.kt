package com.webappypie.optilens.core.camera.burst

import com.webappypie.optilens.core.camera.burst.model.BurstDiagnostics
import com.webappypie.optilens.core.camera.burst.model.BurstResult
import com.webappypie.optilens.core.camera.burst.model.FrameMetadata
import com.webappypie.optilens.core.camera.burst.model.FramePacket
import com.webappypie.optilens.core.camera.burst.model.GyroSample
import com.webappypie.optilens.core.camera.burst.model.GyroWindow
import com.webappypie.optilens.core.camera.burst.pool.BoundedBufferPool
import com.webappypie.optilens.core.camera.strategy.CaptureStrategyMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BurstMetadataInspectorTest {

    private val inspector = BurstMetadataInspector()
    private val pool = BoundedBufferPool(maxCapacity = 4, defaultBufferSize = 1024)

    @Test
    fun `inspect returns empty summary for empty burst`() {
        val burst = BurstResult(packets = emptyList())
        val summary = inspector.inspect(burst)
        assertEquals(0, summary.totalFrames)
        assertFalse(summary.isExposureBracketed)
        assertFalse(summary.hasMotionBlurRisk)
    }

    @Test
    fun `inspect computes correct summary stats for multi-frame burst`() {
        val p1 = FramePacket(
            sequenceIndex = 0,
            totalSequenceCount = 2,
            buffer = pool.acquire(100),
            metadata = FrameMetadata(exposureTimeNs = 10_000_000L, iso = 200),
            gyroWindow = GyroWindow(samples = listOf(GyroSample(1L, 0.01f, 0.01f, 0f))),
        )
        val p2 = FramePacket(
            sequenceIndex = 1,
            totalSequenceCount = 2,
            buffer = pool.acquire(100),
            metadata = FrameMetadata(exposureTimeNs = 10_000_000L, iso = 400),
            gyroWindow = GyroWindow(samples = listOf(GyroSample(2L, 0.02f, 0.02f, 0f))),
        )

        val burst = BurstResult(
            packets = listOf(p1, p2),
            modeUsed = CaptureStrategyMode.MULTI_FRAME_HDR,
            diagnostics = BurstDiagnostics(totalBurstDurationMs = 150L),
        )

        val summary = inspector.inspect(burst)
        assertEquals(2, summary.totalFrames)
        assertEquals(150L, summary.totalDurationMs)
        assertEquals(200, summary.minIso)
        assertEquals(400, summary.maxIso)
        assertEquals(10_000_000L, summary.minExposureNs)
        assertEquals(10_000_000L, summary.maxExposureNs)
        assertFalse(summary.isExposureBracketed)
        assertFalse(summary.hasMotionBlurRisk)

        burst.close()
    }

    @Test
    fun `inspect detects exposure bracketing spread`() {
        val p1 = FramePacket(
            sequenceIndex = 0,
            totalSequenceCount = 3,
            buffer = pool.acquire(100),
            metadata = FrameMetadata(exposureTimeNs = 2_500_000L, iso = 100), // -2 EV
        )
        val p2 = FramePacket(
            sequenceIndex = 1,
            totalSequenceCount = 3,
            buffer = pool.acquire(100),
            metadata = FrameMetadata(exposureTimeNs = 10_000_000L, iso = 100), // 0 EV
        )
        val p3 = FramePacket(
            sequenceIndex = 2,
            totalSequenceCount = 3,
            buffer = pool.acquire(100),
            metadata = FrameMetadata(exposureTimeNs = 40_000_000L, iso = 100), // +2 EV
        )

        val burst = BurstResult(packets = listOf(p1, p2, p3))
        val summary = inspector.inspect(burst)

        assertTrue(summary.isExposureBracketed)
        burst.close()
    }

    @Test
    fun `inspect detects motion blur risk when gyro speed is high`() {
        val p1 = FramePacket(
            sequenceIndex = 0,
            totalSequenceCount = 1,
            buffer = pool.acquire(100),
            metadata = FrameMetadata(),
            gyroWindow = GyroWindow(
                samples = listOf(GyroSample(1L, 0.3f, 0.2f, 0.1f)) // speed > 0.35 rad/s
            ),
        )

        val burst = BurstResult(packets = listOf(p1))
        val summary = inspector.inspect(burst)

        assertTrue(summary.hasMotionBlurRisk)
        burst.close()
    }

    @Test
    fun `formatSummary outputs formatted diagnostic text`() {
        val p1 = FramePacket(
            sequenceIndex = 0,
            totalSequenceCount = 1,
            buffer = pool.acquire(100),
            metadata = FrameMetadata(exposureTimeNs = 16_666_666L, iso = 400),
        )
        val burst = BurstResult(packets = listOf(p1), modeUsed = CaptureStrategyMode.NIGHT_STACK)
        val summary = inspector.inspect(burst)
        val formatted = inspector.formatSummary(summary)

        assertTrue(formatted.contains("OptiLens Burst Metadata Inspector"))
        assertTrue(formatted.contains("NIGHT_STACK"))
        assertTrue(formatted.contains("1/60s"))
        assertTrue(formatted.contains("ISO: 400"))

        burst.close()
    }
}
