package com.webappypie.optilens.core.camera.burst

import com.webappypie.optilens.core.camera.strategy.CaptureStrategyMode
import com.webappypie.optilens.core.common.result.OptiResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BurstAcquisitionEngineTest {

    private val engine = FakeBurstAcquisitionEngine()

    @Test
    fun `acquireBurst produces expected frame count and metadata`() = runTest {
        val progressUpdates = mutableListOf<Pair<Int, Int>>()
        val result = engine.acquireBurst(
            frameCount = 4,
            evOffsets = listOf(0),
            mode = CaptureStrategyMode.MULTI_FRAME_HDR,
            targetRotation = 90,
            onProgress = { c, t -> progressUpdates.add(c to t) }
        )

        assertTrue(result is OptiResult.Success)
        val burstResult = (result as OptiResult.Success).data
        assertEquals(4, burstResult.frameCount)
        assertEquals(CaptureStrategyMode.MULTI_FRAME_HDR, burstResult.modeUsed)
        assertFalse(burstResult.isFallbackSingleFrame)
        assertEquals(4, progressUpdates.size)
        assertEquals(4 to 4, progressUpdates.last())

        val firstPacket = burstResult.packets[0]
        assertEquals(0, firstPacket.sequenceIndex)
        assertEquals(4, firstPacket.totalSequenceCount)
        assertEquals(90, firstPacket.metadata.orientationDegrees)
        assertEquals(4, engine.bufferPool.activeAllocations)

        burstResult.close()
        assertEquals(0, engine.bufferPool.activeAllocations)
    }

    @Test
    fun `acquireBurst with fallback produces single frame result with isFallbackSingleFrame true`() = runTest {
        engine.shouldFallbackToSingle = true
        val result = engine.acquireBurst(
            frameCount = 8,
            evOffsets = listOf(0),
            mode = CaptureStrategyMode.NIGHT_STACK,
        )

        assertTrue(result is OptiResult.Success)
        val burstResult = (result as OptiResult.Success).data
        assertEquals(1, burstResult.frameCount)
        assertTrue(burstResult.isFallbackSingleFrame)

        burstResult.close()
        assertEquals(0, engine.bufferPool.activeAllocations)
    }

    @Test
    fun `acquireBurst with simulated failure returns OptiResult Error`() = runTest {
        engine.shouldFail = true
        val result = engine.acquireBurst(
            frameCount = 3,
            evOffsets = listOf(0),
            mode = CaptureStrategyMode.ACTION_FREEZE,
        )

        assertTrue(result is OptiResult.Error)
        assertEquals(0, engine.bufferPool.activeAllocations)
    }

    @Test
    fun `evOffsets are correctly recorded by acquisition engine`() = runTest {
        val evs = listOf(-2, 0, 2)
        engine.acquireBurst(
            frameCount = 3,
            evOffsets = evs,
            mode = CaptureStrategyMode.MULTI_FRAME_HDR,
        )

        assertEquals(3, engine.lastAcquiredCount)
        assertEquals(evs, engine.lastEvOffsets)
    }
}
