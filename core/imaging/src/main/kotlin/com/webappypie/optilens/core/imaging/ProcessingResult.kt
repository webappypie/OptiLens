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
)
