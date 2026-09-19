package com.webappypie.optilens.core.imaging

/**
 * Output model produced by [ImagingPipeline].
 */
data class ProcessingResult(
    val outputUri: String,
    val originalUri: String? = null,
    val modeUsed: ProcessingMode,
    val processingDurationMs: Long,
    val width: Int,
    val height: Int,
    val isHdrApplied: Boolean = false,
    val isNightModeApplied: Boolean = false,
    val isPortraitApplied: Boolean = false,
    val isAiEnhanceApplied: Boolean = false,
    val isSuperResApplied: Boolean = false,
    val superResMethod: String? = null,
    val zoomFactor: Float = 1.0f,
    val isPureOptical: Boolean = true,
    val faceCount: Int = 0,
    val isFallbackUsed: Boolean = false,
)
