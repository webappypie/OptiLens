package com.webappypie.optilens.core.camera.moon

/**
 * Normalized bounding region and geometric metrics of a detected lunar disc.
 *
 * All coordinates are normalized in the [0.0, 1.0] range relative to the frame.
 */
data class MoonDiscRoi(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val centerX: Float,
    val centerY: Float,
    val radius: Float,
    val meanLuma: Float,
    val circularity: Float,
    val confidence: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val areaFraction: Float get() = width * height
}

/**
 * Stability cue indicating handheld steadiness during high-magnification astronomical alignment.
 */
enum class StabilityCue(val message: String) {
    STABLE("Stable — Ready to Capture"),
    HOLD_STEADY("Hold steady for Moon capture"),
    TRIPOD_RECOMMENDED("Tripod recommended for maximum lunar clarity"),
}

/**
 * Real-time detection state of the lunar disc emitted by [MoonDetector].
 */
data class MoonDetectionState(
    val isMoonDetected: Boolean = false,
    val roi: MoonDiscRoi? = null,
    val confidence: Float = 0.0f,
    val stabilityCue: StabilityCue = StabilityCue.HOLD_STEADY,
    val suggestedEvOffset: Int = -2,
    val suggestedZoomRatio: Float = 5.0f,
    val timestampMs: Long = System.currentTimeMillis(),
) {
    companion object {
        val EMPTY = MoonDetectionState()
    }
}

/**
 * Computational capture configuration computed by [MoonModeEngine].
 *
 * Dictates physical exposure timing, spot metering attenuation, frame stacking count,
 * and conservative detail recovery bounds without synthetic AI textures.
 */
data class MoonCaptureConfig(
    val shutterSpeedNanos: Long = 4_000_000L, // 1/250s
    val isoBias: Float = 0.6f,
    val evOffset: Int = -2,
    val recommendedZoomRatio: Float = 5.0f,
    val burstFrameCount: Int = 8,
    val detailRecoveryStrength: Float = 0.75f,
    val isThermallyCapped: Boolean = false,
)
