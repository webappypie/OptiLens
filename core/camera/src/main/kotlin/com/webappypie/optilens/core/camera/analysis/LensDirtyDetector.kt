package com.webappypie.optilens.core.camera.analysis

import com.webappypie.optilens.core.camera.model.CameraShakeLevel
import com.webappypie.optilens.core.camera.model.MotionState
import com.webappypie.optilens.core.camera.model.QualityMetrics
import javax.inject.Inject
import javax.inject.Singleton

/**
 * State representing optical lens cleanliness and prompt visibility.
 *
 * @param isDirty True if the conservative detector has confirmed persistent lens contamination.
 * @param confidence Confidence score of contamination (0.0 to 1.0).
 * @param isDismissed True if the user has dismissed the viewfinder prompt for this session.
 */
data class LensDirtyState(
    val isDirty: Boolean = false,
    val confidence: Float = 0.0f,
    val isDismissed: Boolean = false,
) {
    val shouldShowPrompt: Boolean
        get() = isDirty && !isDismissed

    companion object {
        val CLEAN = LensDirtyState(isDirty = false, confidence = 0.0f, isDismissed = false)
    }
}

/**
 * Conservative optical lens smudge and contamination detector.
 *
 * Detection criteria:
 * 1. Evaluates forward veiling glare and high-frequency edge attenuation.
 * 2. Strict guard conditions: Inactive during device shake, extreme low-light (<45 luma),
 *    or overexposed scenes (>220 luma).
 * 3. Repeated-confidence temporal threshold: Requires at least [REQUIRED_CONSECUTIVE_FRAMES] (5)
 *    consecutive high-confidence evaluations before triggering.
 * 4. Dismissible prompt: Once dismissed, suppresses warnings for the remainder of the session.
 */
@Singleton
class LensDirtyDetector @Inject constructor() {

    companion object {
        const val REQUIRED_CONSECUTIVE_FRAMES = 5
        const val CONFIDENCE_THRESHOLD = 0.72f
    }

    private var consecutiveHighConfidenceCount = 0
    private var isDismissed = false
    private var lastEmittedState = LensDirtyState.CLEAN

    /**
     * Evaluates optical frame metrics and motion conditions.
     */
    fun evaluate(
        frameData: PreprocessedFrameData,
        quality: QualityMetrics,
        motion: MotionState,
    ): LensDirtyState {
        if (isDismissed) {
            return LensDirtyState(isDirty = lastEmittedState.isDirty, confidence = lastEmittedState.confidence, isDismissed = true)
        }

        // Guard condition 1: Camera must be stable (prevent mistaking motion blur for dirty lens)
        if (motion.cameraShakeLevel == CameraShakeLevel.HIGH || motion.isCameraShaking) {
            consecutiveHighConfidenceCount = (consecutiveHighConfidenceCount - 1).coerceAtLeast(0)
            return lastEmittedState.copy(isDismissed = isDismissed)
        }

        // Guard condition 2: Adequate lighting required (prevent mistaking sensor noise in low-light for smudges)
        val luma = quality.luminance
        if (luma < 45.0f || luma > 220.0f) {
            consecutiveHighConfidenceCount = (consecutiveHighConfidenceCount - 1).coerceAtLeast(0)
            return lastEmittedState.copy(isDismissed = isDismissed)
        }

        // Guard condition 3: Scene must have sufficient dynamic range (not a blank white or black wall)
        if (quality.dynamicRangeScore < 25.0f) {
            return lastEmittedState.copy(isDismissed = isDismissed)
        }

        // Optical smudge metric calculation:
        // Smudged lenses exhibit veiling glare: elevated minimum black level combined with
        // suppressed high-frequency sharpness relative to illumination.
        val centerLuma = frameData.centerLuminance
        val sharpness = quality.sharpnessScore

        // Expected sharpness for given lighting
        val expectedSharpness = (luma * 0.40f).coerceIn(20.0f, 75.0f)
        val sharpnessDeficit = ((expectedSharpness - sharpness) / expectedSharpness).coerceIn(0.0f, 1.0f)

        // Haze index: elevated shadow floor with low contrast
        val hasLowContrastHaze = frameData.shadowClippingPercent < 0.5f && sharpnessDeficit > 0.45f

        val instantConfidence = if (hasLowContrastHaze && sharpness < 22.0f && centerLuma in 60.0f..180.0f) {
            (0.60f + (sharpnessDeficit * 0.35f)).coerceIn(0.0f, 0.95f)
        } else {
            0.0f
        }

        // Temporal repeated-confidence accumulator
        if (instantConfidence >= CONFIDENCE_THRESHOLD) {
            consecutiveHighConfidenceCount++
        } else {
            consecutiveHighConfidenceCount = (consecutiveHighConfidenceCount - 1).coerceAtLeast(0)
        }

        val isDirtyConfirmed = consecutiveHighConfidenceCount >= REQUIRED_CONSECUTIVE_FRAMES
        val finalConfidence = if (isDirtyConfirmed) instantConfidence else (consecutiveHighConfidenceCount.toFloat() / REQUIRED_CONSECUTIVE_FRAMES) * 0.6f

        lastEmittedState = LensDirtyState(
            isDirty = isDirtyConfirmed,
            confidence = finalConfidence,
            isDismissed = isDismissed,
        )

        return lastEmittedState
    }

    /**
     * User dismissed the lens dirty viewfinder prompt.
     * Suppresses future warnings for the active session.
     */
    fun dismissPrompt() {
        isDismissed = true
        lastEmittedState = lastEmittedState.copy(isDismissed = true)
    }

    /**
     * Resets detector history and dismissal status (e.g. upon app resume or lens change).
     */
    fun reset() {
        consecutiveHighConfidenceCount = 0
        isDismissed = false
        lastEmittedState = LensDirtyState.CLEAN
    }
}
