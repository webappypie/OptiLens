package com.webappypie.optilens.core.camera.model

/**
 * Optical and radiometric quality metrics computed in real-time from the viewfinder stream.
 *
 * @param luminance Mean frame luminance across all subsampled pixels (0.0 to 255.0).
 * @param highlightClippingPercent Percentage of pixels in the highlight clipping region (Y >= 250).
 * @param shadowClippingPercent Percentage of pixels in the shadow clipping region (Y <= 10).
 * @param sharpnessScore Focus and edge contrast metric derived from modified Laplacian variance (0.0 to 100.0).
 * @param isBacklit True if peripheral/background illumination significantly exceeds the center region.
 * @param backlightRatio Ratio of peripheral luminance to center luminance.
 * @param dynamicRangeScore Estimated dynamic range spread between shadows and highlights (0.0 to 100.0).
 * @param timestampMs Frame analysis timestamp in milliseconds.
 */
data class QualityMetrics(
    val luminance: Float = 128.0f,
    val highlightClippingPercent: Float = 0.0f,
    val shadowClippingPercent: Float = 0.0f,
    val sharpnessScore: Float = 50.0f,
    val isBacklit: Boolean = false,
    val backlightRatio: Float = 1.0f,
    val dynamicRangeScore: Float = 50.0f,
    val timestampMs: Long = System.currentTimeMillis(),
) {
    companion object {
        val DEFAULT = QualityMetrics()
    }
}
