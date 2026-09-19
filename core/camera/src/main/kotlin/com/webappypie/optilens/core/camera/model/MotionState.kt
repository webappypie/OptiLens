package com.webappypie.optilens.core.camera.model

/**
 * Categorization of physical device camera shake based on gyroscope angular velocity.
 */
enum class CameraShakeLevel {
    STABLE,
    MODERATE,
    HIGH;
}

/**
 * Categorization of scene subject motion derived from visual frame differences.
 */
enum class SubjectMotionLevel {
    STATIC,
    LOW_MOTION,
    HIGH_MOTION;
}

/**
 * Combined physical device motion and visual scene motion state.
 *
 * @param gyroAngularVelocityRadPerSec Magnitude of rotational velocity in radians/second from the gyroscope.
 * @param cameraShakeLevel Categorized physical camera stability level.
 * @param subjectMotionScore Normalized inter-frame difference metric isolating subject movement (0.0 to 1.0).
 * @param subjectMotionLevel Categorized subject motion state.
 * @param timestampMs Timestamp in milliseconds.
 */
data class MotionState(
    val gyroAngularVelocityRadPerSec: Float = 0.0f,
    val cameraShakeLevel: CameraShakeLevel = CameraShakeLevel.STABLE,
    val subjectMotionScore: Float = 0.0f,
    val subjectMotionLevel: SubjectMotionLevel = SubjectMotionLevel.STATIC,
    val timestampMs: Long = System.currentTimeMillis(),
) {
    val isCameraShaking: Boolean
        get() = cameraShakeLevel != CameraShakeLevel.STABLE

    companion object {
        val DEFAULT = MotionState()
    }
}
