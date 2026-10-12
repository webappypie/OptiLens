package com.webappypie.optilens.core.imaging.ai

import android.graphics.Bitmap

/**
 * Blur type classification.
 */
enum class BlurType(val displayName: String) {
    SHARP("Sharp / In Focus"),
    MOTION_BLUR("Directional Motion Blur"),
    DEFOCUS_BLUR("Defocus / Out-of-Focus Blur"),
    SEVERE_UNRECOVERABLE("Severe / Irrecoverable Blur");
}

/**
 * Result of blur analysis on a candidate image.
 */
data class BlurClassificationResult(
    val blurType: BlurType,
    val confidence: Float,
    val motionAngleDegrees: Float = 0f,
    val motionLengthPixels: Float = 0f,
    val defocusRadiusPixels: Float = 0f,
    val isRecoverable: Boolean = true,
    val userAdvice: String = "",
)

/**
 * Configuration options for deblur processing.
 */
data class DeblurConfig(
    val strength: Float = 0.7f,
    val iterations: Int = 10,
    val suppressNoise: Boolean = true,
    val autoClassify: Boolean = true,
)

/**
 * Configuration options for background cleanup / inpainting.
 */
data class InpaintingConfig(
    val featherRadius: Int = 4,
    val dilationPixels: Int = 2,
    val bilateralSmoothing: Boolean = true,
)

/**
 * Configuration options for reflection reduction.
 */
data class ReflectionConfig(
    val strength: Float = 0.6f,
    val recoverTransmissionColor: Boolean = true,
    val suppressSpecularFlares: Boolean = true,
)

/**
 * Configuration options for super-resolution upscaling.
 */
data class UpscaleConfig(
    val scaleFactor: Int = 2, // 2 or 4
    val enhanceMicroContrast: Boolean = true,
)

/**
 * Memory budget for Upscale. The output is one ARGB_8888 bitmap plus same-size working arrays,
 * so it is capped to keep peak memory well under typical app heap limits.
 */
object UpscaleBudget {
    /** 24 MP output = ~96 MB per ARGB_8888 buffer. */
    const val MAX_OUTPUT_PIXELS: Long = 24_000_000L

    val SUPPORTED_FACTORS = listOf(2, 4)

    fun isAllowed(width: Int, height: Int, factor: Int): Boolean =
        width > 0 && height > 0 && factor in SUPPORTED_FACTORS &&
            width.toLong() * height.toLong() * factor * factor <= MAX_OUTPUT_PIXELS

    fun allowedFactors(width: Int, height: Int): List<Int> =
        SUPPORTED_FACTORS.filter { isAllowed(width, height, it) }
}

/**
 * Configuration options for old photo restoration.
 */
data class RestorationConfig(
    val scratchRemovalStrength: Float = 0.75f,
    val colorRevivalStrength: Float = 0.65f,
    val grainStabilization: Boolean = true,
)

/**
 * Concrete processing stages emitted during AI execution.
 */
enum class AiExecutionStage(val stageIndex: Int, val totalStages: Int, val displayName: String) {
    PREPARING_TILES(1, 5, "Preparing image tiles..."),
    ANALYZING_IMAGE(2, 5, "Analyzing the photo..."),
    EXECUTING_AI_INFERENCE(3, 5, "Applying the edit..."),
    RECONSTRUCTING_HIGH_RES(4, 5, "Building the full-size result..."),
    FINALIZING_BLENDING(5, 5, "Finishing...");
}

/**
 * Output result of an executed AI transformation.
 */
data class AiToolExecutionResult(
    val processedBitmap: Bitmap,
    val toolType: AiToolType,
    val executionTimeMs: Long,
    val disclosureApplied: Boolean,
    val disclosureText: String?,
    val metadataTags: Map<String, String> = emptyMap(),
)
