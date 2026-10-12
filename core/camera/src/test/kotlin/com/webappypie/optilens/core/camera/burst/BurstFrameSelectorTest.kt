package com.webappypie.optilens.core.camera.burst

import com.webappypie.optilens.core.camera.burst.model.FrameMetadata
import com.webappypie.optilens.core.camera.burst.model.FramePacket
import com.webappypie.optilens.core.camera.burst.model.GyroSample
import com.webappypie.optilens.core.camera.burst.model.GyroWindow
import com.webappypie.optilens.core.camera.burst.pool.BoundedBufferPool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BurstFrameSelectorTest {

    private val pool = BoundedBufferPool(maxCapacity = 16, defaultBufferSize = 64)

    private fun frame(index: Int, angularSpeed: Float): FramePacket = FramePacket(
        sequenceIndex = index,
        totalSequenceCount = 8,
        buffer = pool.acquire(16),
        metadata = FrameMetadata(orientationDegrees = 90),
        gyroWindow = GyroWindow(
            samples = listOf(
                GyroSample(0L, 0f, angularSpeed, 0f),
                GyroSample(1L, 0f, angularSpeed, 0f),
            ),
        ),
    )

    @Test
    fun `selects the frame with the lowest gyro motion`() {
        val frames = listOf(frame(0, 0.30f), frame(1, 0.05f), frame(2, 0.20f))
        assertEquals(1, BurstFrameSelector.selectSteadiest(frames)?.sequenceIndex)
    }

    @Test
    fun `prefers base exposure frames over bracketed ones`() {
        // EV pattern 0, -2, +2 repeats: indices 0 and 3 are at EV 0.
        val frames = listOf(frame(0, 0.40f), frame(1, 0.01f), frame(2, 0.01f), frame(3, 0.10f))
        val selected = BurstFrameSelector.selectSteadiest(frames, evOffsets = listOf(0, -2, 2))
        assertEquals(3, selected?.sequenceIndex)
    }

    @Test
    fun `falls back to all frames when none are at base exposure`() {
        val frames = listOf(frame(0, 0.30f), frame(1, 0.02f))
        val selected = BurstFrameSelector.selectSteadiest(frames, evOffsets = listOf(-1, 1))
        assertEquals(1, selected?.sequenceIndex)
    }

    @Test
    fun `skips released frames and returns null when none remain`() {
        val steady = frame(0, 0.01f)
        val shaky = frame(1, 0.50f)
        steady.close()
        assertEquals(1, BurstFrameSelector.selectSteadiest(listOf(steady, shaky))?.sequenceIndex)

        shaky.close()
        assertNull(BurstFrameSelector.selectSteadiest(listOf(steady, shaky)))
    }

    @Test
    fun `ties keep the earliest frame`() {
        val frames = listOf(frame(0, 0.10f), frame(1, 0.10f))
        assertEquals(0, BurstFrameSelector.selectSteadiest(frames)?.sequenceIndex)
    }
}
