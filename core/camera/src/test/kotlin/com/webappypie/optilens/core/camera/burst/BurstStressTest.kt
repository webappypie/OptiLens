package com.webappypie.optilens.core.camera.burst

import com.webappypie.optilens.core.camera.burst.pool.BoundedBufferPool
import com.webappypie.optilens.core.camera.strategy.CaptureStrategyMode
import com.webappypie.optilens.core.common.result.OptiResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BurstStressTest {

    @Test
    fun `repeated bursts release all buffers with zero memory leaks`() = runTest {
        val pool = BoundedBufferPool(maxCapacity = 12, defaultBufferSize = 1024)
        val engine = FakeBurstAcquisitionEngine(bufferPool = pool)

        val burstCount = 5
        val framesPerBurst = 8

        for (b in 0 until burstCount) {
            val result = engine.acquireBurst(
                frameCount = framesPerBurst,
                evOffsets = listOf(0),
                mode = CaptureStrategyMode.NIGHT_STACK,
            )

            assertTrue(result is OptiResult.Success)
            val burstResult = (result as OptiResult.Success).data
            assertEquals(framesPerBurst, burstResult.frameCount)
            assertEquals(framesPerBurst, pool.activeAllocations)

            // Release the burst (simulating downstream pipeline completion)
            burstResult.close()

            // Invariant check: Pool must return to 0 active allocations
            assertEquals("Pool active allocations must be 0 after burst $b closes", 0, pool.activeAllocations)
        }

        val stats = pool.getStats()
        assertEquals(0, stats.activeAllocations)
        assertEquals((burstCount * framesPerBurst).toLong(), stats.totalAcquisitionsCount)
        assertTrue(stats.availableBuffers > 0)
    }
}
