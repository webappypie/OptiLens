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
    val faceCount: Int = 0,
    val isFallbackUsed: Boolean = false,
)
