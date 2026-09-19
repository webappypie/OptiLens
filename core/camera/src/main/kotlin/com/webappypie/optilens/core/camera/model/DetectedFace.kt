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

    fun contains(x: Float, y: Float): Boolean {
        return x in left..right && y in top..bottom
    }
}

/**
 * 2D normalized coordinate in the range [0.0, 1.0].
 */
data class NormalizedPoint(
    val x: Float,
    val y: Float,
)

/**
 * Supported facial landmark anatomical points and contours.
 */
enum class LandmarkType {
    LEFT_EYE,
    RIGHT_EYE,
    LEFT_EYEBROW,
    RIGHT_EYEBROW,
    NOSE_BASE,
    NOSE_BRIDGE,
    MOUTH_BOTTOM,
    MOUTH_LEFT,
    MOUTH_RIGHT,
    LIPS_CONTOUR,
    LEFT_EAR,
    RIGHT_EAR,
    LEFT_CHEEK,
    RIGHT_CHEEK,
    FACE_OVAL;
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
 * Facial contour outline curve formed by sequential normalized points.
 */
data class FaceContour(
    val type: LandmarkType,
    val points: List<NormalizedPoint>,
)

/**
 * Facial detection entity identified by the on-device face detector.
 *
 * @param id Optional tracking ID assigned across sequential frames.
 * @param bounds Normalized bounding box within the viewfinder coordinate space [0.0, 1.0].
 * @param landmarks Detected anatomical points (eyes, nose, mouth, ears).
 * @param contours Detected anatomical boundary contour curves.
 * @param confidence Detection confidence score (0.0 to 1.0).
 * @param meanLuminance Estimated mean luminance across the face region (0.0 to 255.0).
 */
data class DetectedFace(
    val id: Int? = null,
    val bounds: NormalizedRect,
    val landmarks: List<FaceLandmarkPoint> = emptyList(),
    val contours: List<FaceContour> = emptyList(),
    val confidence: Float = 1.0f,
    val meanLuminance: Float? = null,
) {
    fun getLeftEye(): FaceLandmarkPoint? = landmarks.firstOrNull { it.type == LandmarkType.LEFT_EYE }
    fun getRightEye(): FaceLandmarkPoint? = landmarks.firstOrNull { it.type == LandmarkType.RIGHT_EYE }

    fun getEyeDistance(): Float? {
        val left = getLeftEye() ?: return null
        val right = getRightEye() ?: return null
        val dx = right.x - left.x
        val dy = right.y - left.y
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }
}
