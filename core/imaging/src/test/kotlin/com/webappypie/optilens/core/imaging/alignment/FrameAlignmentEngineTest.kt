package com.webappypie.optilens.core.imaging.alignment

import com.webappypie.optilens.core.camera.burst.FakeBurstAcquisitionEngine
import com.webappypie.optilens.core.camera.burst.model.BurstDiagnostics
import com.webappypie.optilens.core.camera.burst.model.BurstResult
import com.webappypie.optilens.core.camera.burst.model.FrameMetadata
import com.webappypie.optilens.core.camera.burst.model.FramePacket
import com.webappypie.optilens.core.camera.burst.model.GyroSample
import com.webappypie.optilens.core.camera.burst.model.GyroWindow
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FrameAlignmentEngineTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val appDispatchers = object : AppDispatchers {
        override val default: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val main: CoroutineDispatcher = testDispatcher
        override val mainImmediate: CoroutineDispatcher = testDispatcher
    }
    private val pool = BoundedBufferPool(maxCapacity = 8, defaultBufferSize = 512)
    private val nativeEngine = NativeFrameAlignmentEngine(appDispatchers, NoOpLogger())
    private val fakeEngine = FakeFrameAlignmentEngine()

    @Test
    fun `scoreFrame evaluates sharpness and flags motion blurred frame`() = runTest {
        val blurredPacket = FramePacket(
            sequenceIndex = 0,
            totalSequenceCount = 1,
            buffer = pool.acquire(256),
            metadata = FrameMetadata(),
            gyroWindow = GyroWindow(samples = listOf(GyroSample(1L, 0.3f, 0.2f, 0.1f))), // > 0.35 rad/s
        )

        val score = nativeEngine.scoreFrame(blurredPacket)
        assertTrue(score.isMotionBlurred)

        blurredPacket.close()
    }

    @Test
    fun `alignFrame correctly identifies anchor reference frame with identity matrix`() = runTest {
        val refPacket = FramePacket(0, 2, pool.acquire(256), FrameMetadata())
        val score = FrameScore(70f, 0.05f, 0f, 0.9f, 0.01f, 65f)

        val aligned = nativeEngine.alignFrame(refPacket, refPacket, score)
        assertTrue(aligned.isReference)
        assertFalse(aligned.isRejected)
        assertTrue(aligned.homography.isIdentity)
        assertEquals(1.0f, aligned.alignmentConfidence, 0.001f)
        assertNotNull(aligned.ghostMask)

        refPacket.close()
    }

    @Test
    fun `alignFrame rejects candidate frame with excessive camera shake`() = runTest {
        val refPacket = FramePacket(0, 2, pool.acquire(256), FrameMetadata())
        val candPacket = FramePacket(1, 2, pool.acquire(256), FrameMetadata())
        val extremeShakeScore = FrameScore(30f, 0.1f, 10f, 0.5f, 0.55f, 5f) // gyro 0.55 rad/s > 0.40

        val aligned = nativeEngine.alignFrame(refPacket, candPacket, extremeShakeScore)
        assertFalse(aligned.isReference)
        assertTrue(aligned.isRejected)
        assertTrue(aligned.rejectionReason?.contains("Excessive camera shake") == true)

        refPacket.close()
        candPacket.close()
    }

    @Test
    fun `alignStack produces valid AlignedStack with anchor and aligned frames`() = runTest {
        val burstEngine = FakeBurstAcquisitionEngine(bufferPool = pool)
        val burstResult = (burstEngine.acquireBurst(
            frameCount = 4,
            evOffsets = listOf(0),
            mode = CaptureStrategyMode.MULTI_FRAME_HDR,
        ) as OptiResult.Success).data

        val stackResult = nativeEngine.alignStack(burstResult)
        assertTrue(stackResult is OptiResult.Success)

        val stack = (stackResult as OptiResult.Success).data
        assertEquals(4, stack.diagnostics.candidateCount)
        assertEquals(3, stack.alignedFrames.size) // 1 reference + 3 aligned
        assertEquals(0, stack.rejectedFrames.size)
        assertEquals(4, stack.usableFrameCount)
        assertTrue(stack.referenceFrame.isReference)
        assertTrue(stack.referenceFrame.homography.isIdentity)

        stack.close()
    }

    @Test
    fun `FakeFrameAlignmentEngine enforces forced rejections accurately`() = runTest {
        fakeEngine.rejectIndices = setOf(2)

        val burstEngine = FakeBurstAcquisitionEngine(bufferPool = pool)
        val burstResult = (burstEngine.acquireBurst(
            frameCount = 3,
            evOffsets = listOf(0),
            mode = CaptureStrategyMode.NIGHT_STACK,
        ) as OptiResult.Success).data

        val stackResult = fakeEngine.alignStack(burstResult)
        assertTrue(stackResult is OptiResult.Success)

        val stack = (stackResult as OptiResult.Success).data
        assertEquals(1, stack.rejectedFrames.size)
        assertEquals(2, stack.rejectedFrames[0].sequenceIndex)
        assertTrue(stack.rejectedFrames[0].isRejected)
        assertEquals(2, stack.usableFrameCount)

        stack.close()
    }
}
