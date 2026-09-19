package com.webappypie.optilens.core.imaging.diagnostics

import com.webappypie.optilens.core.camera.burst.model.FrameMetadata
import com.webappypie.optilens.core.camera.burst.model.FramePacket
import com.webappypie.optilens.core.camera.burst.pool.BoundedBufferPool
import com.webappypie.optilens.core.imaging.fusion.ColorProfile
import com.webappypie.optilens.core.imaging.fusion.FusedPhoto
import com.webappypie.optilens.core.imaging.fusion.FusionDiagnostics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactSheetGeneratorTest {

    private val pool = BoundedBufferPool(maxCapacity = 4, defaultBufferSize = 1024)
    private val generator = ContactSheetGenerator()

    @Test
    fun `generateComparison produces double-width side-by-side contact sheet and diff heat map`() {
        val width = 32
        val height = 32
        val total = width * height

        val refBuf = pool.acquire(total)
        refBuf.data.fill(100.toByte())
        val refPacket = FramePacket(0, 1, refBuf, FrameMetadata(), width = width, height = height)

        val fusedY = ByteArray(total) { i -> (100 + (i % 10)).toByte() }
        val fusedPhoto = FusedPhoto(
            jpegBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte()),
            yuvBytes = fusedY,
            width = width,
            height = height,
            colorProfile = ColorProfile.DEFAULT,
            diagnostics = FusionDiagnostics(10, 5, 5, 5, 25, 3.5f, 1.0f, 0f, 2, 2),
            metadata = FrameMetadata(),
        )

        val result = generator.generateComparison(refPacket, fusedPhoto)

        assertEquals(width * 2, result.comparisonWidth)
        assertEquals(height, result.comparisonHeight)
        assertNotNull(result.comparisonJpegBytes)
        assertNotNull(result.diffHeatMapJpegBytes)
        assertTrue("Comparison JPEG must not be empty", result.comparisonJpegBytes.isNotEmpty())
        assertTrue("Heat map JPEG must not be empty", result.diffHeatMapJpegBytes.isNotEmpty())
        assertTrue("Mean difference should be > 0", result.meanDifference > 0.0f)
        assertTrue("Max difference should be <= 10", result.maxDifference in 1.0f..10.0f)

        refPacket.close()
    }

    @Test
    fun `generateSyntheticScene produces valid patterns for all Phase 09 quality check targets`() {
        for (scene in QualitySceneType.entries) {
            val pattern = ContactSheetGenerator.generateSyntheticScene(scene, width = 64, height = 64)
            assertEquals(64 * 64, pattern.size)
            assertTrue("Pattern for $scene must contain valid byte data", pattern.isNotEmpty())
        }
    }
}
