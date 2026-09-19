package com.webappypie.optilens.core.imaging.sr

/**
 * Method of super resolution used to synthesize higher-frequency spatial detail.
 */
enum class SuperResolutionMethod(val id: Int, val label: String) {
    OPTICAL_NATIVE(0, "Optical Native"),
    MULTI_FRAME_SR(1, "Multi-Frame SR (2x/4x)"),
    SINGLE_FRAME_EDGE_SR(2, "Edge-Directed SR (2x)"),
    BICUBIC_BASELINE(3, "Bicubic Interpolation"),
    LANCZOS_BASELINE(4, "Lanczos-3 Interpolation"),
    SHARPENED_UPSCALE_BASELINE(5, "Sharpened Upscale (Unsharp)");

    companion object {
        fun fromId(id: Int): SuperResolutionMethod {
            return entries.firstOrNull { it.id == id } ?: SINGLE_FRAME_EDGE_SR
        }
    }
}

/**
 * Configuration parameters for Super Resolution execution.
 *
 * @param scaleFactor Output magnification factor (2.0f or 4.0f).
 * @param confidenceThreshold Minimum alignment confidence required for frame contribution.
 * @param coringThreshold Noise gate threshold below which micro-contrast differences are suppressed.
 * @param enableHaloSuppression Prevents edge ringing / dark-light halos along high-contrast lines.
 * @param residualRejectionThreshold Max luminance deviation before candidate frame pixel is rejected.
 * @param sharpnessBoost Strength multiplier for deconvolution/unsharp high frequencies.
 * @param tileSize Dimension of square tiles for memory-bounded execution (< 25MB).
 * @param tileOverlap Overlapping border width for Hann window seamless tile stitching.
 */
data class SuperResolutionConfig(
    val scaleFactor: Float = 2.0f,
    val confidenceThreshold: Float = 0.65f,
    val coringThreshold: Float = 6.0f,
    val enableHaloSuppression: Boolean = true,
    val residualRejectionThreshold: Float = 28.0f,
    val sharpnessBoost: Float = 0.25f,
    val tileSize: Int = 256,
    val tileOverlap: Int = 32,
)

/**
 * Result metric of an upscaling / super-resolution benchmark run.
 */
data class SrBenchmarkResult(
    val method: SuperResolutionMethod,
    val durationMs: Float,
    val psnrDb: Float,
    val ssim: Float,
    val acutanceScore: Float,
    val memoryKb: Float,
)

/**
 * Descriptor for tiled sub-image processing window.
 */
data class SrTile(
    val tileX: Int,
    val tileY: Int,
    val tileWidth: Int,
    val tileHeight: Int,
    val paddedX: Int,
    val paddedY: Int,
    val paddedWidth: Int,
    val paddedHeight: Int,
    val outX: Int,
    val outY: Int,
    val outWidth: Int,
    val outHeight: Int,
)
