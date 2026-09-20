package com.webappypie.optilens.core.camera.analysis

import com.webappypie.optilens.core.camera.model.CameraShakeLevel
import com.webappypie.optilens.core.camera.model.MotionState
import com.webappypie.optilens.core.camera.model.QualityMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LensDirtyDetectorTest {

    private lateinit var detector: LensDirtyDetector

    @Before
    fun setUp() {
        detector = LensDirtyDetector()
    }

    private fun createFrameData(
        centerLuma: Float = 100f,
        shadowClip: Float = 0.1f,
    ): PreprocessedFrameData {
        return PreprocessedFrameData(
            gridWidth = 16,
            gridHeight = 16,
            yGrid = IntArray(16 * 16) { centerLuma.toInt() },
            histogramBins = FloatArray(64),
            meanLuminance = centerLuma,
            centerLuminance = centerLuma,
            peripheryLuminance = centerLuma,
            highlightClippingPercent = 0.0f,
            shadowClippingPercent = shadowClip,
            averageU = 128f,
            averageV = 128f,
            skyScore = 0f,
            plantScore = 0f,
            warmScore = 0f,
            timestampMs = System.currentTimeMillis(),
        )
    }

    private fun createSmudgedQuality(): QualityMetrics {
        return QualityMetrics(
            luminance = 100.0f,
            sharpnessScore = 10.0f, // very low sharpness relative to 100 luma
            dynamicRangeScore = 60.0f,
        )
    }

    private fun createStableMotion(): MotionState {
        return MotionState(
            cameraShakeLevel = CameraShakeLevel.STABLE,
        )
    }

    @Test
    fun `clean sharp frame produces clean state`() {
        val frame = createFrameData(centerLuma = 120f, shadowClip = 0.05f)
        val quality = QualityMetrics(
            luminance = 120f,
            sharpnessScore = 65f, // sharp
            dynamicRangeScore = 70f,
        )
        val motion = createStableMotion()

        val state = detector.evaluate(frame, quality, motion)
        assertFalse("Clean frame must not trigger isDirty", state.isDirty)
        assertFalse("Clean frame must not show prompt", state.shouldShowPrompt)
        assertTrue(state.confidence < 0.2f)
    }

    @Test
    fun `device shake suppresses lens dirty accumulation`() {
        val frame = createFrameData(centerLuma = 100f, shadowClip = 0.1f)
        val quality = createSmudgedQuality()
        val shakingMotion = MotionState(
            cameraShakeLevel = CameraShakeLevel.HIGH,
        )

        for (i in 1..10) {
            val state = detector.evaluate(frame, quality, shakingMotion)
            assertFalse("Shake must suppress dirty trigger", state.isDirty)
            assertFalse(state.shouldShowPrompt)
        }
    }

    @Test
    fun `extreme low light suppresses lens dirty detection`() {
        val frame = createFrameData(centerLuma = 30f, shadowClip = 0.1f)
        val lowLightQuality = QualityMetrics(
            luminance = 30.0f, // < 45 luma guard
            sharpnessScore = 10.0f,
            dynamicRangeScore = 40.0f,
        )
        val motion = createStableMotion()

        for (i in 1..10) {
            val state = detector.evaluate(frame, lowLightQuality, motion)
            assertFalse("Low light must suppress dirty trigger", state.isDirty)
        }
    }

    @Test
    fun `repeated confidence threshold requires at least 5 consecutive frames before triggering`() {
        val frame = createFrameData(centerLuma = 100f, shadowClip = 0.1f)
        val quality = createSmudgedQuality()
        val motion = createStableMotion()

        // Frames 1 through 4: confidence accumulates, but isDirty is false
        for (i in 1..4) {
            val state = detector.evaluate(frame, quality, motion)
            assertFalse("Frame $i must not trigger isDirty yet", state.isDirty)
            assertFalse(state.shouldShowPrompt)
        }

        // Frame 5: threshold reached
        val confirmedState = detector.evaluate(frame, quality, motion)
        assertTrue("5th consecutive frame must confirm dirty lens", confirmedState.isDirty)
        assertTrue("Should show prompt on confirmed dirty lens", confirmedState.shouldShowPrompt)
        assertTrue(confirmedState.confidence >= 0.72f)
    }

    @Test
    fun `dismiss prompt suppresses notification for active session`() {
        val frame = createFrameData(centerLuma = 100f, shadowClip = 0.1f)
        val quality = createSmudgedQuality()
        val motion = createStableMotion()

        // Trigger dirty lens
        for (i in 1..5) {
            detector.evaluate(frame, quality, motion)
        }
        assertTrue(detector.evaluate(frame, quality, motion).shouldShowPrompt)

        // User dismisses prompt
        detector.dismissPrompt()

        val dismissedState = detector.evaluate(frame, quality, motion)
        assertTrue(dismissedState.isDirty)
        assertTrue(dismissedState.isDismissed)
        assertFalse("Dismissed prompt must not show in viewfinder", dismissedState.shouldShowPrompt)

        // Reset clears dismissal
        detector.reset()
        val resetState = detector.evaluate(frame, quality, motion)
        assertFalse(resetState.isDismissed)
        assertFalse(resetState.isDirty) // requires 5 frames again
    }
}
