package com.webappypie.optilens.core.imaging.sample

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BeforeAfterSampleGeneratorTest {

    private val generator = BeforeAfterSampleGenerator()

    @Test
    fun `generateBeforeAfterSample executes real enhancement and yields authentic metrics`() {
        val width = 128
        val height = 128
        val chart = generator.generateTestCalibrationChart(width, height)

        val result = generator.generateBeforeAfterSample(chart, width, height)

        assertEquals(width, result.width)
        assertEquals(height, result.height)
        assertEquals(width * height, result.beforePixels.size)
        assertEquals(width * height, result.afterPixels.size)

        // Ensure pixels are actually modified by the pipeline
        assertNotEquals(result.beforePixels.toList(), result.afterPixels.toList())

        // Validate authentic metrics
        assertTrue(result.metrics.isAuthentic)
        assertTrue("Shadow recovery gain should be positive", result.metrics.shadowRecoveryGain > 0.0)
        assertTrue("Acutance should show positive sharpening", result.metrics.acutanceGainPct > 0.0)
    }

    @Test
    fun `generateSideBySideComposite creates double-width comparison canvas with seam`() {
        val width = 64
        val height = 64
        val chart = generator.generateTestCalibrationChart(width, height)
        val result = generator.generateBeforeAfterSample(chart, width, height)

        val composite = generator.generateSideBySideComposite(result)

        assertEquals(width * 2 * height, composite.size)

        // Seam pixels at the center line should be white (0xFFFFFFFF)
        val seamLeft = composite[width - 1]
        val seamRight = composite[width]
        assertEquals(0xFFFFFFFF.toInt(), seamLeft)
        assertEquals(0xFFFFFFFF.toInt(), seamRight)
    }
}
