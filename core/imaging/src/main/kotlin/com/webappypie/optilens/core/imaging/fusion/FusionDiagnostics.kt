package com.webappypie.optilens.core.imaging.fusion

/**
 * Performance timings and image quality diagnostics from multi-frame fusion.
 *
 * @param fusionDurationMs Latency of temporal/HDR stack fusion.
 * @param toneMappingDurationMs Latency of shadow lift, highlight roll-off, and filmic mapping.
 * @param colorGradingDurationMs Latency of AWB, profiling, skin protection, and detail enhancement.
 * @param encodingDurationMs Latency of final image compression.
 * @param totalDurationMs Aggregate latency across all fusion pipeline stages.
 * @param snrGainDb Measured signal-to-noise ratio improvement in decibels (10 * log10(N_eff)).
 * @param dynamicRangeExtensionEv Dynamic range recovery extended across exposures in EV stops.
 * @param ghostPixelFraction Percentage of frame pixels where motion was detected and reference fallback occurred.
 * @param usedFrameCount Number of frames that contributed valid pixels to the fused radiance map.
 * @param totalFrameCount Total frames originally supplied in the aligned stack.
 */
data class FusionDiagnostics(
    val fusionDurationMs: Long,
    val toneMappingDurationMs: Long,
    val colorGradingDurationMs: Long,
    val encodingDurationMs: Long,
    val totalDurationMs: Long,
    val snrGainDb: Float,
    val dynamicRangeExtensionEv: Float,
    val ghostPixelFraction: Float,
    val usedFrameCount: Int,
    val totalFrameCount: Int,
)
