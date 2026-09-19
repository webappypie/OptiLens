package com.webappypie.optilens.core.camera.strategy

import com.webappypie.optilens.core.camera.model.CameraShakeLevel
import com.webappypie.optilens.core.camera.model.MotionState
import com.webappypie.optilens.core.camera.model.QualityMetrics
import com.webappypie.optilens.core.camera.model.SceneClassification
import com.webappypie.optilens.core.camera.model.SceneType
import com.webappypie.optilens.core.camera.model.SubjectMotionLevel

/**
 * Intelligent decision engine computing computational acquisition strategy, frame counts,
 * exposure bracketing, and viewfinder hints in real time.
 */
class CaptureStrategyEngine {

    /**
     * Determines optimal [CaptureStrategy] given current environmental and motion conditions.
     */
    fun decideStrategy(
        scene: SceneClassification,
        quality: QualityMetrics,
        motion: MotionState,
        supportsHdr: Boolean = true,
        diagnostics: AnalysisDiagnostics = AnalysisDiagnostics.EMPTY,
    ): CaptureStrategy {
        val isShaking = motion.cameraShakeLevel == CameraShakeLevel.HIGH

        // 1. High Camera Shake takes priority for user guidance
        if (isShaking) {
            return CaptureStrategy(
                mode = CaptureStrategyMode.SINGLE_FRAME,
                recommendedFrameCount = 1,
                exposureEvOffsets = listOf(0),
                uiHint = CaptureUiHint.HOLD_STEADY,
                shutterPriority = ShutterPriority.FAST_ACTION,
                diagnostics = diagnostics,
            )
        }

        // 2. Low-Light / Night Stacking Strategy
        if (scene.primaryScene == SceneType.LOW_LIGHT || quality.luminance < 38.0f) {
            val frameCount = when (motion.cameraShakeLevel) {
                CameraShakeLevel.STABLE -> 8
                CameraShakeLevel.MODERATE -> 4
                CameraShakeLevel.HIGH -> 2
            }
            return CaptureStrategy(
                mode = CaptureStrategyMode.NIGHT_STACK,
                recommendedFrameCount = frameCount,
                exposureEvOffsets = listOf(0),
                uiHint = CaptureUiHint.NIGHT_SUGGESTED,
                shutterPriority = if (motion.cameraShakeLevel == CameraShakeLevel.STABLE) {
                    ShutterPriority.LONG_STABILIZED
                } else {
                    ShutterPriority.AUTO
                },
                diagnostics = diagnostics,
            )
        }

        // 3. High Dynamic Range (HDR) & Backlight Strategy
        if (supportsHdr && (quality.isBacklit || quality.highlightClippingPercent > 3.5f || quality.dynamicRangeScore > 72.0f)) {
            val hint = if (quality.isBacklit) CaptureUiHint.BACKLIGHT_DETECTED else CaptureUiHint.HDR_SUGGESTED
            return CaptureStrategy(
                mode = CaptureStrategyMode.MULTI_FRAME_HDR,
                recommendedFrameCount = 3,
                exposureEvOffsets = listOf(-2, 0, 2),
                uiHint = hint,
                shutterPriority = ShutterPriority.AUTO,
                diagnostics = diagnostics,
            )
        }

        // 4. Portrait & People Strategy
        if (scene.primaryScene == SceneType.PORTRAIT) {
            return CaptureStrategy(
                mode = CaptureStrategyMode.PORTRAIT_DEPTH,
                recommendedFrameCount = 1,
                exposureEvOffsets = listOf(0),
                uiHint = null, // Viewfinder face boxes provide visual feedback
                shutterPriority = ShutterPriority.AUTO,
                diagnostics = diagnostics,
            )
        }

        // 5. Document Scanning Strategy
        if (scene.primaryScene == SceneType.DOCUMENT) {
            return CaptureStrategy(
                mode = CaptureStrategyMode.DOCUMENT_ENHANCE,
                recommendedFrameCount = 1,
                exposureEvOffsets = listOf(0),
                uiHint = CaptureUiHint.DOCUMENT_DETECTED,
                shutterPriority = ShutterPriority.AUTO,
                diagnostics = diagnostics,
            )
        }

        // 6. Fast Subject Motion (Freeze Action)
        if (motion.subjectMotionLevel == SubjectMotionLevel.HIGH_MOTION) {
            return CaptureStrategy(
                mode = CaptureStrategyMode.ACTION_FREEZE,
                recommendedFrameCount = 1,
                exposureEvOffsets = listOf(0),
                uiHint = null,
                shutterPriority = ShutterPriority.FAST_ACTION,
                diagnostics = diagnostics,
            )
        }

        // 7. Standard Single Frame
        return CaptureStrategy(
            mode = CaptureStrategyMode.SINGLE_FRAME,
            recommendedFrameCount = 1,
            exposureEvOffsets = listOf(0),
            uiHint = null,
            shutterPriority = ShutterPriority.AUTO,
            diagnostics = diagnostics,
        )
    }
}
