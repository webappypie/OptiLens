package com.webappypie.optilens.core.camera.model

/**
 * High-level shooting modes supported by the OptiLens camera interface.
 *
 * Each mode represents a distinct capture and computational processing pipeline.
 */
enum class CameraMode(
    val label: String,
    val description: String,
) {
    PHOTO(
        label = "Photo",
        description = "Standard intelligent capture with single-frame or multi-frame HDR fusion",
    ),
    NIGHT(
        label = "Night",
        description = "Handheld low-light multi-frame alignment, de-ghosting, and luma boost",
    ),
    PORTRAIT(
        label = "Portrait",
        description = "Face-aware processing with synthetic optical bokeh disc simulation",
    ),
    BEST_SHOT(
        label = "Best Shot",
        description = "Intelligent burst capture with sharpness, motion, and eye-open ranking",
    ),
    PET(
        label = "Pet",
        description = "Fast-action shutter priority with high-frequency fur detail protection",
    ),
    FOOD(
        label = "Food",
        description = "Warm-neutral white balance stability, restrained saturation, and appetizing local contrast",
    ),
    DOCUMENT(
        label = "Document",
        description = "Perspective warp, illumination normalization, and high-readability text enhancement",
    ),
    PRO(
        label = "Pro",
        description = "Full manual control over ISO, shutter speed, focus diopters, WB, and RAW/DNG",
    ),
    VIDEO(
        label = "Video",
        description = "High-definition video recording with optical and gyro stabilization",
    );

    companion object {
        val DEFAULT = PHOTO
    }
}
