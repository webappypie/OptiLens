package com.webappypie.optilens.core.imaging.fusion

import com.webappypie.optilens.core.camera.burst.model.FrameMetadata
import com.webappypie.optilens.core.common.result.OptiResult
import com.webappypie.optilens.core.imaging.alignment.AlignedStack
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fake implementation of [MultiFrameFusionEngine] for testing.
 */
@Singleton
class FakeMultiFrameFusionEngine @Inject constructor() : MultiFrameFusionEngine {

    var lastStack: AlignedStack? = null
        private set

    var lastConfig: FusionConfig? = null
        private set

    var shouldFail: Boolean = false

    override suspend fun fuse(
        stack: AlignedStack,
        config: FusionConfig,
    ): OptiResult<FusedPhoto> {
        lastStack = stack
        lastConfig = config

        if (shouldFail) {
            return OptiResult.Error(
                com.webappypie.optilens.core.common.result.OptiError.ProcessingFailed("fake_fusion")
            )
        }

        val ref = stack.referenceFrame
        val width = ref.packet.width
        val height = ref.packet.height

        val mockJpeg = byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(),
            0xFF.toByte(), 0xE0.toByte(), 0x00.toByte(), 0x10.toByte(),
            0x4A.toByte(), 0x46.toByte(), 0x49.toByte(), 0x46.toByte(), 0x00.toByte(),
            0x01.toByte(), 0x01.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(),
            0x00.toByte(), 0x01.toByte(), 0x00.toByte(), 0x00.toByte(),
            0xFF.toByte(), 0xD9.toByte()
        )

        val diagnostics = FusionDiagnostics(
            fusionDurationMs = 25L,
            toneMappingDurationMs = 12L,
            colorGradingDurationMs = 10L,
            encodingDurationMs = 15L,
            totalDurationMs = 62L,
            snrGainDb = 6.02f,
            dynamicRangeExtensionEv = 2.0f,
            ghostPixelFraction = 0.01f,
            usedFrameCount = stack.usableFrameCount,
            totalFrameCount = stack.usableFrameCount + stack.rejectedFrames.size,
        )

        return OptiResult.Success(
            FusedPhoto(
                jpegBytes = mockJpeg,
                yuvBytes = ByteArray(width * height),
                width = width,
                height = height,
                colorProfile = config.colorProfile,
                diagnostics = diagnostics,
                metadata = ref.packet.metadata,
            )
        )
    }
}
