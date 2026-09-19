package com.webappypie.optilens.core.camera.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QualityMetricsEvaluatorTest {

    private val evaluator = QualityMetricsEvaluator()

    @Test
    fun evaluate_uniformFlatFrame_computesLowSharpnessAndZeroClipping() {
        val gridWidth = 160
        val gridHeight = 120
        val uniformY = IntArray(gridWidth * gridHeight) { 128 }
        val histogram = FloatArray(64) { if (it == 32) 1.0f else 0.0f }

        val frame = PreprocessedFrameData(
            gridWidth = gridWidth,
            gridHeight = gridHeight,
            yGrid = uniformY,
            histogramBins = histogram,
            meanLuminance = 128.0f,
            centerLuminance = 128.0f,
            peripheryLuminance = 128.0f,
            highlightClippingPercent = 0.0f,
            shadowClippingPercent = 0.0f,
            averageU = 128.0f,
            averageV = 128.0f,
            skyScore = 0.0f,
            plantScore = 0.0f,
            warmScore = 0.0f,
            timestampMs = 1000L,
        )

        val metrics = evaluator.evaluate(frame)

        assertEquals(128.0f, metrics.luminance, 0.01f)
        assertEquals(0.0f, metrics.highlightClippingPercent, 0.01f)
        assertEquals(0.0f, metrics.shadowClippingPercent, 0.01f)
        assertEquals(0.0f, metrics.sharpnessScore, 0.01f)
        assertFalse(metrics.isBacklit)
        assertEquals(1.0f, metrics.backlightRatio, 0.01f)
    }

    @Test
    fun evaluate_highContrastCheckerboard_computesHighSharpnessScore() {
        val gridWidth = 160
        val gridHeight = 120
        val highContrastY = IntArray(gridWidth * gridHeight) { i ->
            val x = i % gridWidth
            val y = i / gridWidth
            if (((x / 4) + (y / 4)) % 2 == 0) 240 else 15
        }

        val frame = PreprocessedFrameData(
            gridWidth = gridWidth,
            gridHeight = gridHeight,
            yGrid = highContrastY,
            histogramBins = FloatArray(64) { 0.5f },
            meanLuminance = 127.5f,
            centerLuminance = 127.5f,
            peripheryLuminance = 127.5f,
            highlightClippingPercent = 0.0f,
            shadowClippingPercent = 0.0f,
            averageU = 128.0f,
            averageV = 128.0f,
            skyScore = 0.0f,
            plantScore = 0.0f,
            warmScore = 0.0f,
            timestampMs = 1000L,
        )

        val metrics = evaluator.evaluate(frame)

        assertTrue("Sharpness score should be elevated for high contrast pattern", metrics.sharpnessScore > 50.0f)
    }

    @Test
    fun evaluate_backlitScene_correctlyFlagsBacklight() {
        val gridWidth = 160
        val gridHeight = 120
        val frame = PreprocessedFrameData(
            gridWidth = gridWidth,
            gridHeight = gridHeight,
            yGrid = IntArray(gridWidth * gridHeight) { 100 },
            histogramBins = FloatArray(64),
            meanLuminance = 110.0f,
            centerLuminance = 60.0f,       // Dark subject in center
            peripheryLuminance = 200.0f,   // Bright peripheral sky/window
            highlightClippingPercent = 8.0f,
            shadowClippingPercent = 2.0f,
            averageU = 128.0f,
            averageV = 128.0f,
            skyScore = 0.4f,
            plantScore = 0.0f,
            warmScore = 0.0f,
            timestampMs = 1000L,
        )

        val metrics = evaluator.evaluate(frame)

        assertTrue(metrics.isBacklit)
        assertTrue(metrics.backlightRatio > 1.6f)
    }
}
