package com.webappypie.optilens.core.imaging

/**
 * Modes of imaging processing supported by the pipeline.
 */
enum class ProcessingMode {
    STANDARD,
    HDR,
    NIGHT,
    PORTRAIT,
    SUPER_RES_ZOOM,
}

/**
 * Request parameter bundle passed into [ImagingPipeline].
 */
data class ProcessingRequest(
    val inputUri: String,
    val mode: ProcessingMode = ProcessingMode.STANDARD,
    val burstFrameUris: List<String> = emptyList(),
    val keepOriginal: Boolean = true,
    val applyDenoise: Boolean = true,
    val enhanceLighting: Boolean = true,
)
