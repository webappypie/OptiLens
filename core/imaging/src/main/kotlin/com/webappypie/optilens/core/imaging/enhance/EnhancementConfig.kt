package com.webappypie.optilens.core.imaging.enhance

/**
 * Fine-grained configuration parameters for the on-device computational photography pipeline.
 *
 * Defaults tuned to achieve a "premium computational-photography look" that remains natural:
 * - Clean, lifted shadows without gray haze
 * - Preserved highlight roll-off without blown highlights
 * - Vibrant memory colors (blues, greens, warm highlights) with protected, natural skin tones
 * - Rich local contrast and texture sharpness with halo-free edges
 */
data class EnhancementConfig(
    val hdrStrength: Float = 0.65f,
    val shadowLift: Float = 0.40f,
    val highlightRecovery: Float = 0.50f,
    val localContrast: Float = 0.35f,
    val detailSharpening: Float = 0.35f,
    val denoiseStrength: Float = 0.40f,
    val vibrance: Float = 0.25f,
    val preserveSkinTones: Boolean = true,
    val zoomSuperResStrength: Float = 0.50f,
    val isZoomedCapture: Boolean = false,
    val zoomRatio: Float = 1.0f,
) {
    companion object {
        val BALANCED = EnhancementConfig()
        val HIGH_DYNAMIC_RANGE = EnhancementConfig(
            hdrStrength = 0.85f,
            shadowLift = 0.55f,
            highlightRecovery = 0.70f,
            localContrast = 0.40f,
        )
        val LOW_LIGHT = EnhancementConfig(
            hdrStrength = 0.75f,
            shadowLift = 0.60f,
            denoiseStrength = 0.65f,
            detailSharpening = 0.30f,
        )
        val ZOOM_ENHANCE = EnhancementConfig(
            isZoomedCapture = true,
            zoomSuperResStrength = 0.70f,
            detailSharpening = 0.45f,
            denoiseStrength = 0.45f,
        )
    }
}
