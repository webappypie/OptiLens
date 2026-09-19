package com.webappypie.optilens.core.imaging.enhance

/**
 * Real processing stages in the One-Tap AI Enhance pipeline.
 *
 * Progress reports strictly correspond to real stage transitions without
 * fake or timer-based percentage inflation.
 */
enum class AiEnhanceStage(val displayName: String) {
    ANALYZING_SCENE("Analyzing scene & lighting..."),
    BALANCING_EXPOSURE("Balancing shadows & dynamic range..."),
    REDUCING_NOISE("Refining texture & reducing noise..."),
    ENHANCING_DETAILS("Sharpening micro-contrast & clarity..."),
    COLOR_HARMONY("Enhancing color vibrance & tone..."),
    COMPLETED("Enhancement complete");

    val stageIndex: Int get() = ordinal + 1
    val totalStages: Int get() = entries.size - 1 // Excluding COMPLETED
}

/**
 * Configuration options for the AI enhancement pipeline.
 */
data class AiEnhanceConfig(
    val strength: Float = 1.0f,
    val preserveSkinTones: Boolean = true,
    val enableExposureBalancing: Boolean = true,
    val enableNoiseReduction: Boolean = true,
    val enableDetailEnhancement: Boolean = true,
    val enableColorHarmony: Boolean = true,
)

/**
 * Result metrics for completed AI enhancement.
 */
data class AiEnhanceResult(
    val isEnhanced: Boolean,
    val stagesExecuted: List<AiEnhanceStage>,
    val processingDurationMs: Long,
    val initialLuminanceMean: Float = 0f,
    val finalLuminanceMean: Float = 0f,
    val shadowGainApplied: Float = 0f,
    val clarityGainApplied: Float = 0f,
)
