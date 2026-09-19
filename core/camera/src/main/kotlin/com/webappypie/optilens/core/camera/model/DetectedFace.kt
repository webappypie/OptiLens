package com.webappypie.optilens.core.camera.model

/**
 * Normalized 2D bounding rectangle in coordinates [0.0, 1.0] relative to the viewfinder.
 */
data class NormalizedRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
    val areaFraction: Float get() = width * height
}

/**
 * Supported facial landmark anatomical points.
 */
enum class LandmarkType {
    LEFT_EYE,
    RIGHT_EYE,
    NOSE_BASE,
    MOUTH_BOTTOM,
    MOUTH_LEFT,
    MOUTH_RIGHT;
}

/**
 * Normalized 2D coordinates for an identified facial landmark.
 */
data class FaceLandmarkPoint(
    val type: LandmarkType,
    val x: Float,
    val y: Float,
)

/**
 * Facial detection entity identified by the on-device face detector.
 *
 * @param id Optional tracking ID assigned across sequential frames.
 * @param bounds Normalized bounding box within the viewfinder coordinate space [0.0, 1.0].
 * @param landmarks Detected anatomical points (eyes, nose, mouth).
 * @param confidence Detection confidence score (0.0 to 1.0).
 */
data class DetectedFace(
    val id: Int? = null,
    val bounds: NormalizedRect,
    val landmarks: List<FaceLandmarkPoint> = emptyList(),
    val confidence: Float = 1.0f,
)
