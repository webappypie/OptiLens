package com.webappypie.optilens.core.imaging.alignment

/**
 * Optical and radiometric quality score bundle for an individual burst frame.
 *
 * @param sharpnessScore Tenengrad gradient energy metric (0.0 to 100.0, higher = sharper).
 * @param exposurePenalty Saturation clipping and crushing penalty (0.0 = perfect, 1.0 = severely clipped).
 * @param motionDifference Inter-frame photometric disparity against reference frame (0.0 to 255.0).
 * @param focusConfidence High-frequency edge gradient distribution (0.0 to 1.0).
 * @param gyroAngularSpeed Physical angular velocity during exposure in rad/s.
 * @param totalScore Composite selection score used by [ReferenceFrameSelector].
 */
data class FrameScore(
    val sharpnessScore: Float,
    val exposurePenalty: Float,
    val motionDifference: Float,
    val focusConfidence: Float,
    val gyroAngularSpeed: Float = 0.0f,
    val totalScore: Float,
) {
    val isMotionBlurred: Boolean
        get() = gyroAngularSpeed > 0.20f || (sharpnessScore < 15.0f && motionDifference > 30.0f)

    val isWellExposed: Boolean
        get() = exposurePenalty < 0.35f

    companion object {
        val ZERO = FrameScore(0f, 1f, 0f, 0f, 0f, 0f)
    }
}
