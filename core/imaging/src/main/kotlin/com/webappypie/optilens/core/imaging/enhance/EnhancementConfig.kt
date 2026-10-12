package com.webappypie.optilens.core.imaging.enhance

/**
 * Strengths for the single-image on-device enhancement pipeline ([LocalEnhancementPipeline]).
 *
 * Every value is a ceiling: the pipeline measures the photo first (exposure, colour cast,
 * dynamic range, noise level) and applies only as much of each correction as the
 * measurement justifies. A well-exposed, clean photo is changed very little.
 */
data class EnhancementConfig(
    /** Fraction (0..1) of the measured exposure error that is corrected. */
    val exposureCorrection: Float = 0.65f,
    /** Upper bound for brightening, in stops. */
    val maxExposureLiftStops: Float = 1.5f,
    /** 0..1: how strongly deep shadows are compressed towards the mid-tones. */
    val shadowRecovery: Float = 0.8f,
    /** 0..1: mid-scale local contrast ("clarity") added on top of the tone mapping. */
    val localContrast: Float = 0.30f,
    /** 0..1: fraction of a measured colour cast that is neutralised (large casts are corrected less). */
    val whiteBalance: Float = 0.6f,
    /** 0..1: chroma (colour blotch) noise reduction. */
    val chromaNoiseReduction: Float = 0.85f,
    /** 0..1: luminance noise reduction (only runs when noise is actually measured). */
    val lumaNoiseReduction: Float = 0.6f,
    /** 0..1: fine-detail sharpening with noise coring and overshoot clamping (no halos). */
    val sharpening: Float = 0.35f,
    /** 0..1: saturation boost for muted colours. */
    val vibrance: Float = 0.15f,
    /** Reduces vibrance on skin-coloured pixels. */
    val preserveSkinTones: Boolean = true,
) {
    companion object {
        val BALANCED = EnhancementConfig()
    }
}
