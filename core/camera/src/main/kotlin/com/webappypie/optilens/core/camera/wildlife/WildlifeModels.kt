package com.webappypie.optilens.core.camera.wildlife

/**
 * Proposed taxonomic classification category for the detected wildlife subject.
 */
enum class WildlifeSubjectType(val displayName: String) {
    BIRD("Bird"),
    MAMMAL_OR_ANIMAL("Animal"),
    GENERAL_WILDLIFE("Wildlife"),
}

/**
 * Normalized bounding region and kinematic attributes of a detected wildlife/bird subject.
 */
data class WildlifeRoi(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val centerX: Float,
    val centerY: Float,
    val subjectType: WildlifeSubjectType = WildlifeSubjectType.GENERAL_WILDLIFE,
    val confidence: Float = 0.8f,
    val motionScore: Float = 0.0f,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val areaFraction: Float get() = width * height
}

/**
 * Real-time detection state of wildlife/birds emitted by [WildlifeDetector].
 */
data class WildlifeDetectionState(
    val isDetected: Boolean = false,
    val roi: WildlifeRoi? = null,
    val confidence: Float = 0.0f,
    val trackingStability: Float = 1.0f,
    val suggestedShutterNanos: Long = 2_000_000L, // 1/500s
    val suggestedZoomRatio: Float = 3.0f,
    val timestampMs: Long = System.currentTimeMillis(),
) {
    companion object {
        val EMPTY = WildlifeDetectionState()
    }
}

/**
 * Computational capture configuration computed by [WildlifeModeEngine].
 */
data class WildlifeCaptureConfig(
    val targetShutterSpeedNanos: Long = 2_000_000L, // 1/500s
    val recommendedIsoBias: Float = 1.4f,
    val burstFrameCount: Int = 6,
    val plumageFurDetailStrength: Float = 0.85f,
    val recommendedZoomRatio: Float = 3.0f,
    val isThermallyCapped: Boolean = false,
)
