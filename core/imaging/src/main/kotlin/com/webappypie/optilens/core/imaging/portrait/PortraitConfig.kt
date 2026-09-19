package com.webappypie.optilens.core.imaging.portrait

import com.webappypie.optilens.core.camera.model.DetectedFace
import com.webappypie.optilens.core.camera.portrait.PortraitAperture

/**
 * Configuration parameters for face-aware portrait processing.
 *
 * @param aperture Simulated optical physical aperture for depth of field.
 * @param skinSmoothingStrength Subtle skin cleanup strength (0.0 to 1.0, default 0.25f).
 * @param faceEvCompensation Backlit face exposure compensation in EV (0.0 to +1.5 EV).
 * @param isBacklit Whether scene is detected as backlit subject.
 * @param enableDetailProtection Strictly protect eyes, brows, lips from smoothing.
 * @param enableEyeSparkle Apply subtle micro-contrast to eye iris/cornea (+15%).
 * @param faces Detected face boundaries and landmarks.
 */
data class PortraitConfig(
    val aperture: PortraitAperture = PortraitAperture.DEFAULT,
    val skinSmoothingStrength: Float = 0.25f,
    val faceEvCompensation: Float = 0.0f,
    val isBacklit: Boolean = false,
    val enableDetailProtection: Boolean = true,
    val enableEyeSparkle: Boolean = true,
    val faces: List<DetectedFace> = emptyList(),
)
