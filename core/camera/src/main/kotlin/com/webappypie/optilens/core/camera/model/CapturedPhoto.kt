package com.webappypie.optilens.core.camera.model

import android.graphics.Bitmap
import android.net.Uri

/**
 * Lifecycle and active session state of the camera pipeline.
 */
enum class CameraSessionState {
    IDLE,
    INITIALIZING,
    PREVIEW_ACTIVE,
    CAPTURING,
    ERROR;
}

/**
 * Flash and continuous illumination modes.
 */
enum class FlashMode {
    AUTO,
    ON,
    OFF,
    TORCH;
}

/**
 * Dynamic zoom limits and current magnification.
 */
data class ZoomState(
    val currentZoom: Float = 1.0f,
    val minZoom: Float = 1.0f,
    val maxZoom: Float = 1.0f,
    val linearZoom: Float = 0.0f,
)

/**
 * Exposure compensation bounds and current step index.
 */
data class ExposureState(
    val index: Int = 0,
    val minIndex: Int = 0,
    val maxIndex: Int = 0,
    val step: Float = 0.0f,
)

/**
 * Metadata and thumbnail for a captured photo saved to MediaStore.
 */
data class CapturedPhoto(
    val uri: String,
    val contentUri: Uri? = null,
    val width: Int,
    val height: Int,
    val timestampMs: Long,
    val thumbnail: Bitmap? = null,
    val orientationDegrees: Int = 0,
    val fileSizeBytes: Long = 0L,
    val companionUri: String? = null,
    val isRaw: Boolean = false,
    val rawFormat: RawCaptureFormat? = null,
)
