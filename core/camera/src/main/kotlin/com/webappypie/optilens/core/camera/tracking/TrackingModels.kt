package com.webappypie.optilens.core.camera.tracking

/**
 * Normalized bounding coordinates of a tracked object within the viewfinder.
 * All coordinates are normalized in the [0.0, 1.0] range.
 */
data class TrackedObjectBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val centerX: Float = (left + right) / 2.0f,
    val centerY: Float = (top + bottom) / 2.0f,
) {
    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)
    val areaFraction: Float get() = width * height

    companion object {
        val EMPTY = TrackedObjectBounds(0f, 0f, 0f, 0f)
    }
}

/**
 * Lifecycle and tracking health status of the real-time object tracker.
 */
enum class TrackingStatus {
    /** Tracker is idle; no target object active. */
    INACTIVE,

    /** Target selected; initializing template and spatial bounds. */
    INITIALIZING,

    /** Active locked tracking with confident correlation. */
    TRACKING,

    /** Subject is temporarily occluded; tracker is extrapolating position along velocity. */
    OCCLUDED,

    /** Subject left frame or occlusion duration exceeded recovery timeout. */
    LOST,

    /** Tracker is gracefully disabled due to severe thermal throttling or low battery. */
    DISABLED_THERMAL;

    val isVisualActive: Boolean get() = this == TRACKING || this == OCCLUDED
}

/**
 * Real-time state of the tracked subject emitted on every analysis cycle.
 */
data class TrackedObjectState(
    val bounds: TrackedObjectBounds? = null,
    val status: TrackingStatus = TrackingStatus.INACTIVE,
    val confidence: Float = 0.0f,
    val velocityX: Float = 0.0f,
    val velocityY: Float = 0.0f,
    val occlusionDurationMs: Long = 0L,
    val timestampMs: Long = System.currentTimeMillis(),
) {
    companion object {
        val INACTIVE = TrackedObjectState()
    }
}
