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
    /**
     * 0..1: atmospheric haze removal (dark-channel prior, linear light). Runs only when haze is
     * measured, never on photos with faces, and transmission is floored so distant detail is
     * restored without the "over-processed" look.
     */
    val dehaze: Float = 0.6f,
    /** 0..1: compression of bright-but-unclipped areas (sky, windows); clipped pixels stay white. */
    val highlightRecovery: Float = 0.7f,
    /** Uses detected faces (when provided) to keep skin texture: less clarity, sharpening and denoise there. */
    val protectFaces: Boolean = true,
) {
    companion object {
        val BALANCED = EnhancementConfig()

        /** For photos OptiLens already merged and tone-mapped at capture (Night, HDR). */
        val ALREADY_TONE_MAPPED = EnhancementConfig(
            exposureCorrection = 0.3f,
            maxExposureLiftStops = 0.5f,
            shadowRecovery = 0.3f,
            localContrast = 0.2f,
            lumaNoiseReduction = 0.3f,
            dehaze = 0.3f,
            highlightRecovery = 0.3f,
        )
    }
}

/** A detected face in normalised image coordinates (0..1, same orientation as the bitmap). */
data class FaceRegion(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val area: Float get() = (right - left).coerceAtLeast(0f) * (bottom - top).coerceAtLeast(0f)
}

/** Optional measurements from outside the pipeline (e.g. on-device face detection). */
data class EnhancementHints(val faces: List<FaceRegion> = emptyList()) {
    companion object {
        val NONE = EnhancementHints()
    }
}

/** Scene type the pipeline measured; drives which corrections are emphasised. */
enum class EnhancementScene { GENERAL, PORTRAIT, LANDSCAPE, LOW_LIGHT, HIGH_CONTRAST }
