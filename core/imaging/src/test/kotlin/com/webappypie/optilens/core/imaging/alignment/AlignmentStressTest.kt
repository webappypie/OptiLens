package com.webappypie.optilens.core.imaging.alignment

import com.webappypie.optilens.core.camera.burst.FakeBurstAcquisitionEngine
import com.webappypie.optilens.core.camera.burst.pool.BoundedBufferPool
import com.webappypie.optilens.core.camera.strategy.CaptureStrategyMode
import com.webappypie.optilens.core.common.coroutines.AppDispatchers
import com.webappypie.optilens.core.common.result.OptiResult
import com.webappypie.optilens.core.logging.NoOpLogger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AlignmentStressTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val appDispatchers = object : AppDispatchers {
        override val default: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val main: CoroutineDispatcher = testDispatcher
        override val mainImmediate: CoroutineDispatcher = testDispatcher
    }

    @Test
    fun `repeated stack alignments release all memory buffers with zero leaks`() = runTest {
        val pool = BoundedBufferPool(maxCapacity = 10, defaultBufferSize = 512)
        val burstEngine = FakeBurstAcquisitionEngine(bufferPool = pool)
        val alignEngine = NativeFrameAlignmentEngine(appDispatchers, NoOpLogger())

        val iterations = 5
        val framesPerBurst = 6

        for (iter in 0 until iterations) {
            val burstResult = (burstEngine.acquireBurst(
                frameCount = framesPerBurst,
                evOffsets = listOf(0),
                mode = CaptureStrategyMode.NIGHT_STACK,
            ) as OptiResult.Success).data

            assertEquals(framesPerBurst, pool.activeAllocations)

            val stackResult = alignEngine.alignStack(burstResult)
            assertTrue(stackResult is OptiResult.Success)
            val stack = (stackResult as OptiResult.Success).data

            assertEquals(framesPerBurst, stack.usableFrameCount + stack.rejectedFrames.size)

            // Release stack
            stack.close()

            // Invariant: all buffers returned to bounded pool
            assertEquals("Iteration $iter: Active buffer allocations must return to 0", 0, pool.activeAllocations)
        }

        val stats = pool.getStats()
        assertEquals(0, stats.activeAllocations)
        assertEquals((iterations * framesPerBurst).toLong(), stats.totalAcquisitionsCount)
    }
}
