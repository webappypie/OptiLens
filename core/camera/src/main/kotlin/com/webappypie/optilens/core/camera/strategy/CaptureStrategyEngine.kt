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
        zoomRatio: Float = 1.0f,
        isOpticalZoom: Boolean = true,
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

        // 6. Pet / Animal Strategy (Fast shutter bias to freeze sudden movement)
        if (scene.primaryScene == SceneType.PET) {
            return CaptureStrategy(
                mode = CaptureStrategyMode.PET_FREEZE,
                recommendedFrameCount = 1,
                exposureEvOffsets = listOf(0),
                uiHint = null,
                shutterPriority = ShutterPriority.FAST_ACTION,
                diagnostics = diagnostics,
            )
        }

        // 7. Food Photography Strategy (Controlled local contrast & stable color)
        if (scene.primaryScene == SceneType.FOOD) {
            return CaptureStrategy(
                mode = CaptureStrategyMode.FOOD_OPTIMIZED,
                recommendedFrameCount = 1,
                exposureEvOffsets = listOf(0),
                uiHint = null,
                shutterPriority = ShutterPriority.AUTO,
                diagnostics = diagnostics,
            )
        }

        // 8. Fast Subject Motion (Freeze Action)
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

        // 9. Digital Zoom Super Resolution Strategy
        if (zoomRatio >= 1.2f && !isOpticalZoom) {
            val count = if (motion.cameraShakeLevel == CameraShakeLevel.STABLE) 4 else 2
            return CaptureStrategy(
                mode = CaptureStrategyMode.SUPER_RES_ZOOM,
                recommendedFrameCount = count,
                exposureEvOffsets = listOf(0),
                uiHint = CaptureUiHint.AI_ZOOM_ACTIVE,
                shutterPriority = ShutterPriority.AUTO,
                diagnostics = diagnostics,
            )
        }

        // 10. Standard Single Frame
        return CaptureStrategy(
            mode = CaptureStrategyMode.SINGLE_FRAME,
            recommendedFrameCount = 1,
            exposureEvOffsets = listOf(0),
            uiHint = null,
            shutterPriority = ShutterPriority.AUTO,
            diagnostics = diagnostics,
        )
    }

    /**
     * Determines capture strategy when an explicit [com.webappypie.optilens.core.camera.model.CameraMode] is active.
     */
    fun decideStrategyForMode(
        mode: com.webappypie.optilens.core.camera.model.CameraMode,
        motion: MotionState = MotionState.DEFAULT,
        quality: QualityMetrics = QualityMetrics.DEFAULT,
    ): CaptureStrategy {
        return when (mode) {
            com.webappypie.optilens.core.camera.model.CameraMode.BEST_SHOT -> CaptureStrategy(
                mode = CaptureStrategyMode.BEST_SHOT_BURST,
                recommendedFrameCount = 6,
                exposureEvOffsets = listOf(0),
                shutterPriority = ShutterPriority.FAST_ACTION,
            )
            com.webappypie.optilens.core.camera.model.CameraMode.PET -> CaptureStrategy(
                mode = CaptureStrategyMode.PET_FREEZE,
                recommendedFrameCount = 1,
                exposureEvOffsets = listOf(0),
                shutterPriority = ShutterPriority.FAST_ACTION,
            )
            com.webappypie.optilens.core.camera.model.CameraMode.FOOD -> CaptureStrategy(
                mode = CaptureStrategyMode.FOOD_OPTIMIZED,
                recommendedFrameCount = 1,
                exposureEvOffsets = listOf(0),
                shutterPriority = ShutterPriority.AUTO,
            )
            com.webappypie.optilens.core.camera.model.CameraMode.DOCUMENT -> CaptureStrategy(
                mode = CaptureStrategyMode.DOCUMENT_ENHANCE,
                recommendedFrameCount = 1,
                exposureEvOffsets = listOf(0),
                uiHint = CaptureUiHint.DOCUMENT_DETECTED,
                shutterPriority = ShutterPriority.AUTO,
            )
            com.webappypie.optilens.core.camera.model.CameraMode.NIGHT -> CaptureStrategy(
                mode = CaptureStrategyMode.NIGHT_STACK,
                recommendedFrameCount = if (motion.cameraShakeLevel == CameraShakeLevel.STABLE) 8 else 4,
                exposureEvOffsets = listOf(0),
                uiHint = CaptureUiHint.NIGHT_SUGGESTED,
                shutterPriority = ShutterPriority.LONG_STABILIZED,
            )
            com.webappypie.optilens.core.camera.model.CameraMode.PORTRAIT -> CaptureStrategy(
                mode = CaptureStrategyMode.PORTRAIT_DEPTH,
                recommendedFrameCount = 1,
                exposureEvOffsets = listOf(0),
            )
            com.webappypie.optilens.core.camera.model.CameraMode.PRO -> CaptureStrategy(
                mode = CaptureStrategyMode.SINGLE_FRAME,
                recommendedFrameCount = 1,
                exposureEvOffsets = listOf(0),
            )
            com.webappypie.optilens.core.camera.model.CameraMode.PHOTO -> CaptureStrategy(
                mode = CaptureStrategyMode.SINGLE_FRAME,
                recommendedFrameCount = 1,
                exposureEvOffsets = listOf(0),
            )
            com.webappypie.optilens.core.camera.model.CameraMode.VIDEO -> CaptureStrategy(
                mode = CaptureStrategyMode.SINGLE_FRAME,
                recommendedFrameCount = 0,
            )
        }
    }
}
