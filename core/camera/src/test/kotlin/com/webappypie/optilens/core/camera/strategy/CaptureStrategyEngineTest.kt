package com.webappypie.optilens.core.camera.strategy

import com.webappypie.optilens.core.camera.model.CameraShakeLevel
import com.webappypie.optilens.core.camera.model.MotionState
import com.webappypie.optilens.core.camera.model.QualityMetrics
import com.webappypie.optilens.core.camera.model.SceneClassification
import com.webappypie.optilens.core.camera.model.SceneType
import com.webappypie.optilens.core.camera.model.SubjectMotionLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CaptureStrategyEngineTest {

    private val engine = CaptureStrategyEngine()

    @Test
    fun decideStrategy_highCameraShake_recommendsHoldSteadyWarning() {
        val motion = MotionState(
            cameraShakeLevel = CameraShakeLevel.HIGH,
            gyroAngularVelocityRadPerSec = 0.45f,
        )
        val scene = SceneClassification(primaryScene = SceneType.GENERAL)
        val quality = QualityMetrics(luminance = 120f)

        val strategy = engine.decideStrategy(scene, quality, motion)

        assertEquals(CaptureUiHint.HOLD_STEADY, strategy.uiHint)
        assertEquals(CaptureStrategyMode.SINGLE_FRAME, strategy.mode)
        assertEquals(1, strategy.recommendedFrameCount)
    }

    @Test
    fun decideStrategy_lowLightWithStableCamera_recommendsNightStack8Frames() {
        val motion = MotionState(
            cameraShakeLevel = CameraShakeLevel.STABLE,
            gyroAngularVelocityRadPerSec = 0.02f,
        )
        val scene = SceneClassification(primaryScene = SceneType.LOW_LIGHT)
        val quality = QualityMetrics(luminance = 20f)

        val strategy = engine.decideStrategy(scene, quality, motion)

        assertEquals(CaptureStrategyMode.NIGHT_STACK, strategy.mode)
        assertEquals(8, strategy.recommendedFrameCount)
        assertEquals(CaptureUiHint.NIGHT_SUGGESTED, strategy.uiHint)
        assertEquals(ShutterPriority.LONG_STABILIZED, strategy.shutterPriority)
    }

    @Test
    fun decideStrategy_lowLightWithModerateShake_recommendsNightStack4Frames() {
        val motion = MotionState(
            cameraShakeLevel = CameraShakeLevel.MODERATE,
            gyroAngularVelocityRadPerSec = 0.15f,
        )
        val scene = SceneClassification(primaryScene = SceneType.LOW_LIGHT)
        val quality = QualityMetrics(luminance = 25f)

        val strategy = engine.decideStrategy(scene, quality, motion)

        assertEquals(CaptureStrategyMode.NIGHT_STACK, strategy.mode)
        assertEquals(4, strategy.recommendedFrameCount)
    }

    @Test
    fun decideStrategy_backlitScene_recommendsMultiFrameHdr() {
        val motion = MotionState.DEFAULT
        val scene = SceneClassification(primaryScene = SceneType.GENERAL)
        val quality = QualityMetrics(
            luminance = 120f,
            isBacklit = true,
            highlightClippingPercent = 5.0f,
        )

        val strategy = engine.decideStrategy(scene, quality, motion, supportsHdr = true)

        assertEquals(CaptureStrategyMode.MULTI_FRAME_HDR, strategy.mode)
        assertEquals(3, strategy.recommendedFrameCount)
        assertEquals(listOf(-2, 0, 2), strategy.exposureEvOffsets)
        assertEquals(CaptureUiHint.BACKLIGHT_DETECTED, strategy.uiHint)
    }

    @Test
    fun decideStrategy_portrait_recommendsPortraitDepth() {
        val motion = MotionState.DEFAULT
        val scene = SceneClassification(primaryScene = SceneType.PORTRAIT)
        val quality = QualityMetrics(luminance = 130f)

        val strategy = engine.decideStrategy(scene, quality, motion)

        assertEquals(CaptureStrategyMode.PORTRAIT_DEPTH, strategy.mode)
        assertEquals(1, strategy.recommendedFrameCount)
        assertNull(strategy.uiHint)
    }

    @Test
    fun decideStrategy_highSubjectMotion_recommendsActionFreeze() {
        val motion = MotionState(
            cameraShakeLevel = CameraShakeLevel.STABLE,
            subjectMotionScore = 0.6f,
            subjectMotionLevel = SubjectMotionLevel.HIGH_MOTION,
        )
        val scene = SceneClassification(primaryScene = SceneType.GENERAL)
        val quality = QualityMetrics(luminance = 140f)

        val strategy = engine.decideStrategy(scene, quality, motion)

        assertEquals(CaptureStrategyMode.ACTION_FREEZE, strategy.mode)
        assertEquals(ShutterPriority.FAST_ACTION, strategy.shutterPriority)
    }
}
