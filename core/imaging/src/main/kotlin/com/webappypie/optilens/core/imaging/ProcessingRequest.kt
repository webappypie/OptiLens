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
    AI_ENHANCE,
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
    val portraitConfig: com.webappypie.optilens.core.imaging.portrait.PortraitConfig? = null,
    val aiEnhanceConfig: com.webappypie.optilens.core.imaging.enhance.AiEnhanceConfig? = null,
    val superResConfig: com.webappypie.optilens.core.imaging.sr.SuperResolutionConfig? = null,
    val targetZoomRatio: Float = 1.0f,
)
