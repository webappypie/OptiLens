package com.webappypie.optilens.core.imaging.fusion

import com.webappypie.optilens.core.camera.burst.FakeBurstAcquisitionEngine
import com.webappypie.optilens.core.camera.burst.pool.BoundedBufferPool
import com.webappypie.optilens.core.camera.strategy.CaptureStrategyMode
import com.webappypie.optilens.core.common.coroutines.AppDispatchers
import com.webappypie.optilens.core.common.result.OptiResult
import com.webappypie.optilens.core.imaging.alignment.NativeFrameAlignmentEngine
import com.webappypie.optilens.core.logging.NoOpLogger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MultiFrameFusionEngineTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val appDispatchers = object : AppDispatchers {
        override val default: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val main: CoroutineDispatcher = testDispatcher
        override val mainImmediate: CoroutineDispatcher = testDispatcher
    }
    private val pool = BoundedBufferPool(maxCapacity = 16, defaultBufferSize = 4096)
    private val alignmentEngine = NativeFrameAlignmentEngine(appDispatchers, NoOpLogger())
    private val fusionEngine = NativeMultiFrameFusionEngine(appDispatchers, NoOpLogger())
    private val burstEngine = FakeBurstAcquisitionEngine(bufferPool = pool)

    @Test
    fun `fuse produces valid FusedPhoto with correct dimensions and valid JPEG headers`() = runTest {
        val burstResult = (burstEngine.acquireBurst(
            frameCount = 4,
            evOffsets = listOf(-1, 0, 1),
            mode = CaptureStrategyMode.MULTI_FRAME_HDR,
        ) as OptiResult.Success).data

        val stackResult = (alignmentEngine.alignStack(burstResult) as OptiResult.Success).data
        val fusionResult = fusionEngine.fuse(stackResult, FusionConfig(colorProfile = ColorProfile.VIVID))

        assertTrue(fusionResult is OptiResult.Success)
        val photo = (fusionResult as OptiResult.Success).data

        assertEquals(4032, photo.width)
        assertEquals(3024, photo.height)
        assertEquals(ColorProfile.VIVID, photo.colorProfile)
        assertNotNull(photo.jpegBytes)
        assertTrue("JPEG output must not be empty", photo.jpegBytes.isNotEmpty())

        // Check JPEG SOI (0xFF, 0xD8)
        assertEquals(0xFF.toByte(), photo.jpegBytes[0])
        assertEquals(0xD8.toByte(), photo.jpegBytes[1])

        // Verify diagnostic latencies were populated
        assertTrue("Fusion duration must be >= 0", photo.diagnostics.fusionDurationMs >= 0)
        assertTrue("Total duration must be >= 0", photo.diagnostics.totalDurationMs >= 0)
        assertTrue("Used frame count must be 4", photo.diagnostics.usedFrameCount == 4)

        stackResult.close()
    }

    @Test
    fun `fake fusion engine returns deterministic mock results`() = runTest {
        val fake = FakeMultiFrameFusionEngine()
        val burstResult = (burstEngine.acquireBurst(
            frameCount = 2,
            evOffsets = listOf(0),
            mode = CaptureStrategyMode.SINGLE_FRAME,
        ) as OptiResult.Success).data
        val stack = (alignmentEngine.alignStack(burstResult) as OptiResult.Success).data

        val res = fake.fuse(stack, FusionConfig(colorProfile = ColorProfile.NATURAL))
        assertTrue(res is OptiResult.Success)
        val photo = (res as OptiResult.Success).data
        assertEquals(ColorProfile.NATURAL, photo.colorProfile)
        assertEquals(6.02f, photo.diagnostics.snrGainDb, 0.01f)

        stack.close()
    }
}
