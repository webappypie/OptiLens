package com.webappypie.optilens.core.camera.wildlife

import com.webappypie.optilens.core.camera.analysis.PreprocessedFrameData
import com.webappypie.optilens.core.camera.bestshot.BestShotEngine
import com.webappypie.optilens.core.camera.bestshot.BestShotScorer
import com.webappypie.optilens.core.camera.model.CameraShakeLevel
import com.webappypie.optilens.core.camera.model.MotionState
import com.webappypie.optilens.core.camera.model.QualityMetrics
import com.webappypie.optilens.core.camera.model.SubjectMotionLevel
import com.webappypie.optilens.core.camera.model.ZoomStop
import com.webappypie.optilens.core.camera.thermal.DeviceThermalState
import com.webappypie.optilens.core.camera.thermal.ThermalDegradationPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.abs

class WildlifeModeEngineTest {

    private lateinit var wildlifeDetector: WildlifeDetector
    private lateinit var wildlifeEngine: WildlifeModeEngine

    @Before
    fun setUp() {
        wildlifeDetector = WildlifeDetector()
        wildlifeEngine = WildlifeModeEngine(BestShotEngine(BestShotScorer()))
    }

    private fun createSyntheticNatureFrame(
        width: Int = 160,
        height: Int = 120,
        hasSalientSubject: Boolean = true,
        isBirdSky: Boolean = false,
    ): PreprocessedFrameData {
        val yGrid = IntArray(width * height) { 100 }

        if (hasSalientSubject) {
            val startY = if (isBirdSky) 30 else 60
            for (y in startY..(startY + 20)) {
                for (x in 60..90) {
                    // Textured animal fur or plumage
                    yGrid[y * width + x] = if ((x + y) % 2 == 0) 140 else 80
                }
            }
        }

        return PreprocessedFrameData(
            gridWidth = width,
            gridHeight = height,
            yGrid = yGrid,
            histogramBins = FloatArray(64),
            meanLuminance = 100f,
            centerLuminance = 110f,
            peripheryLuminance = 95f,
            highlightClippingPercent = 0f,
            shadowClippingPercent = 0f,
            averageU = 120f,
            averageV = 135f,
            skyScore = if (isBirdSky) 0.50f else 0.10f,
            plantScore = if (isBirdSky) 0.10f else 0.45f,
            warmScore = 0.20f,
            timestampMs = System.currentTimeMillis(),
        )
    }

    @Test
    fun `wildlifeDetector detects salient animal subject in nature context`() {
        val frame = createSyntheticNatureFrame(isBirdSky = false)
        val metrics = QualityMetrics(luminance = 100f, sharpnessScore = 45f)
        val motion = MotionState(
            subjectMotionLevel = SubjectMotionLevel.HIGH_MOTION,
            subjectMotionScore = 0.30f,
            cameraShakeLevel = CameraShakeLevel.STABLE,
        )

        val state = wildlifeDetector.detectWildlife(frame, metrics, motion, currentZoomRatio = 2.0f)

        assertTrue("Wildlife subject must be detected", state.isDetected)
        val roi = state.roi
        assertTrue("ROI must be present", roi != null)
        assertEquals(WildlifeSubjectType.MAMMAL_OR_ANIMAL, roi!!.subjectType)
        assertTrue("Shutter must be fast (<= 2_000_000 ns)", state.suggestedShutterNanos <= 2_000_000L)
    }

    @Test
    fun `wildlifeDetector detects bird subject in sky context`() {
        val frame = createSyntheticNatureFrame(isBirdSky = true)
        val metrics = QualityMetrics(luminance = 110f, sharpnessScore = 45f)
        val motion = MotionState(
            subjectMotionLevel = SubjectMotionLevel.HIGH_MOTION,
            subjectMotionScore = 0.40f,
            cameraShakeLevel = CameraShakeLevel.STABLE,
        )

        val state = wildlifeDetector.detectWildlife(frame, metrics, motion, currentZoomRatio = 3.0f)

        assertTrue(state.isDetected)
        val roi = state.roi
        assertTrue(roi != null)
        assertEquals(WildlifeSubjectType.BIRD, roi!!.subjectType)
        assertEquals(WildlifeDetector.ULTRA_FAST_SHUTTER_NANOS, state.suggestedShutterNanos)
    }

    @Test
    fun `computeCaptureConfig enforces fast shutter and tele lens routing`() {
        val roi = WildlifeRoi(
            left = 0.3f, top = 0.2f, right = 0.7f, bottom = 0.6f,
            centerX = 0.5f, centerY = 0.4f,
            subjectType = WildlifeSubjectType.BIRD,
            motionScore = 0.35f,
        )
        val state = WildlifeDetectionState(isDetected = true, roi = roi, suggestedZoomRatio = 3.0f)

        val availableStops = listOf(
            ZoomStop(ratio = 1.0f, label = "1x", isOptical = true),
            ZoomStop(ratio = 3.0f, label = "3x", isOptical = true),
        )

        val config = wildlifeEngine.computeCaptureConfig(
            wildlifeState = state,
            thermalPolicy = ThermalDegradationPolicy.forThermalState(DeviceThermalState.NORMAL),
            availableZoomStops = availableStops,
        )

        assertEquals("Flight bird motion must trigger 1 over 2000s (500_000 ns)", 500_000L, config.targetShutterSpeedNanos)
        assertEquals("Must prefer optical telephoto stop (3x)", 3.0f, config.recommendedZoomRatio, 0.01f)
        assertEquals("Normal thermal state must capture 6 burst frames", 6, config.burstFrameCount)
    }

    @Test
    fun `plumage and fur detail filter enhances keratin texture without touching flat regions`() {
        val width = 16
        val height = 16
        val input = ByteArray(width * height) { 100 }

        // Fine feather barbule texture (variance around 15..25)
        for (y in 4..10) {
            for (x in 4..10) {
                input[y * width + x] = if ((x + y) % 2 == 0) 115.toByte() else 85.toByte()
            }
        }

        val out = wildlifeEngine.processPlumageAndFurDetails(
            inputY = input,
            width = width,
            height = height,
            strength = 0.85f,
        )

        // Flat region outside (2, 2) must remain unchanged
        assertEquals(input[2 * width + 2], out[2 * width + 2])

        // Textured keratin region (6, 6) must have micro-contrast boosted
        val delta = abs((out[6 * width + 6].toInt() and 0xFF) - (input[6 * width + 6].toInt() and 0xFF))
        assertTrue("Feather/plumage micro-contrast must be boosted", delta > 0)
        assertTrue("Delta must be within max limit", delta <= WildlifeModeEngine.MAX_DETAIL_DELTA_LUMA)
    }

    @Test
    fun `rankWildlifeRawBuffers ranks frames with zero wing or motion blur`() {
        val width = 16
        val height = 16

        // Sharp frame with high gradient edges (checkerboard pattern)
        val sharpBytes = ByteArray(width * height) { idx ->
            val x = idx % width
            val y = idx / width
            if ((x + y) % 2 == 0) 220.toByte() else 30.toByte()
        }
        // Blurry frame (flat uniform)
        val blurryBytes = ByteArray(width * height) { 128.toByte() }

        val result = wildlifeEngine.rankWildlifeRawBuffers(
            buffers = listOf(sharpBytes, blurryBytes),
            width = width,
            height = height,
        )

        assertEquals("Sharp textured frame must be ranked #1", 0, result.bestIndex)
        assertEquals(2, result.candidates.size)
        val bestCandidate = result.candidates.first { it.index == 0 }
        assertEquals("Sharp candidate must have rank 1", 1, bestCandidate.ranking)
    }
}
