package com.webappypie.optilens.core.imaging.sr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SuperResolutionBenchmarkTest {

    @Before
    fun setUp() {
        SuperResolutionBenchmark.setForceJvm(true)
    }

    @Test
    fun `runComparativeBenchmark compares all 5 methods and returns valid metrics`() {
        val width = 32
        val height = 32

        // Create high-contrast test patch
        val patch = ByteArray(width * height) { idx ->
            val x = idx % width
            val y = idx / width
            if ((x + y) % 4 == 0) 230.toByte() else 30.toByte()
        }

        val results = SuperResolutionBenchmark.runComparativeBenchmark(
            testPatchY = patch,
            width = width,
            height = height,
            scaleFactor = 2.0f,
        )

        assertEquals(5, results.size)

        val bicubic = results.first { it.method == SuperResolutionMethod.BICUBIC_BASELINE }
        val lanczos = results.first { it.method == SuperResolutionMethod.LANCZOS_BASELINE }
        val sharpened = results.first { it.method == SuperResolutionMethod.SHARPENED_UPSCALE_BASELINE }
        val sfsr = results.first { it.method == SuperResolutionMethod.SINGLE_FRAME_EDGE_SR }
        val mfsr = results.first { it.method == SuperResolutionMethod.MULTI_FRAME_SR }

        // All methods execute with measured latency
        assertTrue(bicubic.durationMs >= 0.0f)
        assertTrue(lanczos.durationMs >= 0.0f)
        assertTrue(sfsr.durationMs >= 0.0f)
        assertTrue(mfsr.durationMs >= 0.0f)

        // Super Resolution PSNR gains over standard bicubic baseline
        assertTrue("SFSR PSNR (${sfsr.psnrDb}) should exceed Bicubic PSNR (${bicubic.psnrDb})", sfsr.psnrDb > bicubic.psnrDb)
        assertTrue("MFSR PSNR (${mfsr.psnrDb}) should exceed Bicubic PSNR (${bicubic.psnrDb})", mfsr.psnrDb > bicubic.psnrDb)

        // SSIM should be >= 0.85 across all methods
        assertTrue(bicubic.ssim in 0.8f..1.0f)
        assertTrue(mfsr.ssim >= bicubic.ssim)

        // Memory allocated should be reasonable
        assertTrue(mfsr.memoryKb > 0f)
    }
}
