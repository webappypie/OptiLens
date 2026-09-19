package com.webappypie.optilens.core.imaging.alignment

import com.webappypie.optilens.core.camera.burst.model.FrameMetadata
import com.webappypie.optilens.core.camera.burst.model.FramePacket
import com.webappypie.optilens.core.camera.burst.model.GyroSample
import com.webappypie.optilens.core.camera.burst.model.GyroWindow
import com.webappypie.optilens.core.camera.burst.pool.BoundedBufferPool
import org.junit.Assert.assertEquals
import org.junit.Test

class ReferenceFrameSelectorTest {

    private val pool = BoundedBufferPool(maxCapacity = 6, defaultBufferSize = 512)
    private val selector = ReferenceFrameSelector()

    @Test
    fun `single packet sequence selects index 0`() {
        val packet = FramePacket(0, 1, pool.acquire(100), FrameMetadata())
        val score = FrameScore(50f, 0.1f, 0f, 0.8f, 0f, 45f)

        val selected = selector.selectReferenceIndex(listOf(packet), listOf(score))
        assertEquals(0, selected)
        packet.close()
    }

    @Test
    fun `selects sharpest frame with lowest motion`() {
        val p1 = FramePacket(
            sequenceIndex = 0,
            totalSequenceCount = 2,
            buffer = pool.acquire(100),
            metadata = FrameMetadata(),
            gyroWindow = GyroWindow(samples = listOf(GyroSample(1L, 0.25f, 0.25f, 0f))), // high motion
        )
        val score1 = FrameScore(20f, 0.1f, 0f, 0.4f, 0.35f, 10f)

        val p2 = FramePacket(
            sequenceIndex = 1,
            totalSequenceCount = 2,
            buffer = pool.acquire(100),
            metadata = FrameMetadata(),
            gyroWindow = GyroWindow(samples = listOf(GyroSample(1L, 0.01f, 0.01f, 0f))), // very stable
        )
        val score2 = FrameScore(80f, 0.1f, 0f, 0.9f, 0.014f, 75f)

        val selected = selector.selectReferenceIndex(listOf(p1, p2), listOf(score1, score2))
        assertEquals(1, selected)

        p1.close()
        p2.close()
    }

    @Test
    fun `bracketed exposure selects 0 EV baseline over clipped frames`() {
        // -2 EV (underexposed)
        val p1 = FramePacket(0, 3, pool.acquire(100), FrameMetadata(exposureTimeNs = 2_500_000L))
        val score1 = FrameScore(40f, 0.7f, 0f, 0.6f, 0.02f, 15f)

        // 0 EV (balanced exposure)
        val p2 = FramePacket(1, 3, pool.acquire(100), FrameMetadata(exposureTimeNs = 10_000_000L))
        val score2 = FrameScore(65f, 0.05f, 0f, 0.85f, 0.02f, 60f)

        // +2 EV (overexposed, highlight clipping)
        val p3 = FramePacket(2, 3, pool.acquire(100), FrameMetadata(exposureTimeNs = 40_000_000L))
        val score3 = FrameScore(50f, 0.85f, 0f, 0.7f, 0.02f, 12f)

        val selected = selector.selectReferenceIndex(listOf(p1, p2, p3), listOf(score1, score2, score3))
        assertEquals(1, selected)

        p1.close()
        p2.close()
        p3.close()
    }
}
