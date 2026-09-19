package com.webappypie.optilens.core.imaging.fusion

import com.webappypie.optilens.core.camera.burst.model.FrameMetadata
import com.webappypie.optilens.core.camera.burst.model.FramePacket
import com.webappypie.optilens.core.camera.burst.pool.BoundedBufferPool
import com.webappypie.optilens.core.common.coroutines.AppDispatchers
import com.webappypie.optilens.core.imaging.alignment.AlignedFrame
import com.webappypie.optilens.core.imaging.alignment.AlignedStack
import com.webappypie.optilens.core.imaging.alignment.AlignmentDiagnostics
import com.webappypie.optilens.core.imaging.alignment.FrameScore
import com.webappypie.optilens.core.imaging.alignment.GhostMask
import com.webappypie.optilens.core.imaging.alignment.HomographyMatrix
import com.webappypie.optilens.core.logging.NoOpLogger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

@OptIn(ExperimentalCoroutinesApi::class)
class TemporalFusionEngineTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val appDispatchers = object : AppDispatchers {
        override val default: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val main: CoroutineDispatcher = testDispatcher
        override val mainImmediate: CoroutineDispatcher = testDispatcher
    }
    private val pool = BoundedBufferPool(maxCapacity = 16, defaultBufferSize = 4096)
    private val fusionEngine = NativeMultiFrameFusionEngine(appDispatchers, NoOpLogger())

    @Test
    fun `temporal fusion reduces noise variance across multiple static frames`() = runTest {
        val width = 32
        val height = 32
        val total = width * height

        // Reference frame with noise centered at 128
        val refBuffer = pool.acquire(total)
        for (i in 0 until total) {
            val noise = if (i % 2 == 0) 6 else -6
            refBuffer.data[i] = (128 + noise).toByte()
        }
        val refPacket = FramePacket(0, 4, refBuffer, FrameMetadata(), width = width, height = height)
        val refFrame = AlignedFrame(
            sequenceIndex = 0,
            isReference = true,
            score = FrameScore(80f, 0f, 0f, 0.9f, 0.01f, 80f),
            homography = HomographyMatrix.IDENTITY,
            alignmentConfidence = 1.0f,
            isRejected = false,
            ghostMask = GhostMask.empty(width, height),
            packet = refPacket,
        )

        // 3 candidate frames with opposite phase noise
        val cands = mutableListOf<AlignedFrame>()
        for (c in 1..3) {
            val buf = pool.acquire(total)
            for (i in 0 until total) {
                val noise = if ((i + c) % 2 == 0) 6 else -6
                buf.data[i] = (128 + noise).toByte()
            }
            val pkt = FramePacket(c, 4, buf, FrameMetadata(), width = width, height = height)
            cands.add(
                AlignedFrame(
                    sequenceIndex = c,
                    isReference = false,
                    score = FrameScore(80f, 0f, 0f, 0.9f, 0.01f, 80f),
                    homography = HomographyMatrix.IDENTITY,
                    alignmentConfidence = 1.0f,
                    isRejected = false,
                    ghostMask = GhostMask.empty(width, height),
                    packet = pkt,
                )
            )
        }

        val stack = AlignedStack(
            referenceIndex = 0,
            referenceFrame = refFrame,
            alignedFrames = cands,
            rejectedFrames = emptyList(),
            diagnostics = AlignmentDiagnostics(10, 10, 20, 4, 4, 0, 1.0f),
        )

        val result = fusionEngine.fuse(stack, FusionConfig(enableDenoise = true))
        assertTrue(result is com.webappypie.optilens.core.common.result.OptiResult.Success)

        val photo = (result as com.webappypie.optilens.core.common.result.OptiResult.Success).data
        assertTrue("SNR gain should be > 3 dB with 4 frames", photo.diagnostics.snrGainDb >= 3.0f)
        assertEquals(4, photo.diagnostics.usedFrameCount)

        stack.close()
    }

    @Test
    fun `ghost mask forces moving pixels to fall back strictly to reference frame`() = runTest {
        val width = 16
        val height = 16
        val total = width * height

        val refBuf = pool.acquire(total)
        refBuf.data.fill(100.toByte()) // Reference is 100
        val refPacket = FramePacket(0, 2, refBuf, FrameMetadata(), width = width, height = height)
        val refFrame = AlignedFrame(
            sequenceIndex = 0,
            isReference = true,
            score = FrameScore(70f, 0f, 0f, 0.9f, 0.01f, 70f),
            homography = HomographyMatrix.IDENTITY,
            alignmentConfidence = 1.0f,
            isRejected = false,
            ghostMask = GhostMask.empty(width, height),
            packet = refPacket,
        )

        // Candidate has a moving bright object (value 240) in pixel index 10
        val candBuf = pool.acquire(total)
        candBuf.data.fill(100.toByte())
        candBuf.data[10] = 240.toByte()

        // Mask flags pixel index 10 as moving (255)
        val maskBytes = ByteArray(total)
        maskBytes[10] = 255.toByte()
        val ghostMask = GhostMask(width, height, maskBytes, 1.0f / total)

        val candPacket = FramePacket(1, 2, candBuf, FrameMetadata(), width = width, height = height)
        val candFrame = AlignedFrame(
            sequenceIndex = 1,
            isReference = false,
            score = FrameScore(70f, 0f, 0f, 0.9f, 0.01f, 70f),
            homography = HomographyMatrix.IDENTITY,
            alignmentConfidence = 0.95f,
            isRejected = false,
            ghostMask = ghostMask,
            packet = candPacket,
        )

        val stack = AlignedStack(
            referenceIndex = 0,
            referenceFrame = refFrame,
            alignedFrames = listOf(candFrame),
            rejectedFrames = emptyList(),
            diagnostics = AlignmentDiagnostics(10, 10, 20, 2, 2, 0, 1.0f),
        )

        val result = fusionEngine.fuse(stack, FusionConfig(enableDenoise = true))
        assertTrue(result is com.webappypie.optilens.core.common.result.OptiResult.Success)

        val photo = (result as com.webappypie.optilens.core.common.result.OptiResult.Success).data
        assertTrue(photo.diagnostics.ghostPixelFraction > 0.0f)

        // In pixel 10, the output should match reference (near 100), not the ghost candidate (240)
        val yuv = photo.yuvBytes
        assertNotNull(yuv)
        val fusedVal = yuv!![10].toInt() and 0xFF
        assertTrue("Moving pixel should match reference ~100 instead of ghost 240, was $fusedVal", fusedVal < 150)

        stack.close()
    }

    @Test
    fun `exposure aware HDR calculates dynamic range extension correctly`() = runTest {
        val width = 16
        val height = 16
        val total = width * height

        // Reference at base exposure (1/60s, ISO 100)
        val refBuf = pool.acquire(total)
        refBuf.data.fill(128.toByte())
        val refMeta = FrameMetadata(exposureTimeNs = 16_666_666L, iso = 100)
        val refFrame = AlignedFrame(
            0, true, FrameScore(70f, 0f, 0f, 0.9f, 0.01f, 70f),
            HomographyMatrix.IDENTITY, 1.0f, false, null, GhostMask.empty(width, height),
            FramePacket(0, 3, refBuf, refMeta, width = width, height = height)
        )

        // Underexposed candidate for highlights (-2 EV: 1/240s, ISO 100)
        val underBuf = pool.acquire(total)
        underBuf.data.fill(60.toByte())
        val underMeta = FrameMetadata(exposureTimeNs = 4_166_666L, iso = 100)
        val underFrame = AlignedFrame(
            1, false, FrameScore(70f, 0f, 0f, 0.9f, 0.01f, 70f),
            HomographyMatrix.IDENTITY, 1.0f, false, null, GhostMask.empty(width, height),
            FramePacket(1, 3, underBuf, underMeta, width = width, height = height)
        )

        // Overexposed candidate for shadows (+1 EV: 1/30s, ISO 100)
        val overBuf = pool.acquire(total)
        overBuf.data.fill(210.toByte())
        val overMeta = FrameMetadata(exposureTimeNs = 33_333_333L, iso = 100)
        val overFrame = AlignedFrame(
            2, false, FrameScore(70f, 0f, 0f, 0.9f, 0.01f, 70f),
            HomographyMatrix.IDENTITY, 1.0f, false, null, GhostMask.empty(width, height),
            FramePacket(2, 3, overBuf, overMeta, width = width, height = height)
        )

        val stack = AlignedStack(
            referenceIndex = 0,
            referenceFrame = refFrame,
            alignedFrames = listOf(underFrame, overFrame),
            rejectedFrames = emptyList(),
            diagnostics = AlignmentDiagnostics(10, 10, 20, 3, 3, 0, 1.0f),
        )

        val result = fusionEngine.fuse(stack, FusionConfig(enableHdr = true))
        assertTrue(result is com.webappypie.optilens.core.common.result.OptiResult.Success)

        val photo = (result as com.webappypie.optilens.core.common.result.OptiResult.Success).data
        assertTrue("HDR dynamic range extension should be >= 2.0 EV", photo.diagnostics.dynamicRangeExtensionEv >= 2.0f)

        stack.close()
    }
}
