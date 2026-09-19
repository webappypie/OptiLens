package com.webappypie.optilens.core.common.result

/**
 * Structured error model for OptiLens.
 *
 * Keeps error information typed and avoids bare exception propagation
 * across architectural boundaries.
 */
sealed class OptiError {

    /** An unexpected exception from a lower layer. */
    data class Unknown(
        val cause: Throwable? = null,
        val message: String = cause?.message ?: "An unexpected error occurred.",
    ) : OptiError()

    /** Camera hardware was not available or was disconnected. */
    data class CameraUnavailable(
        val reason: String,
        val cause: Throwable? = null,
    ) : OptiError()

    /** Image processing pipeline failed. */
    data class ProcessingFailed(
        val stage: String,
        val cause: Throwable? = null,
    ) : OptiError()

    /** A required device capability is not supported. */
    data class CapabilityNotSupported(
        val capability: String,
    ) : OptiError()

    /** Storage write failed (MediaStore, file I/O). */
    data class StorageFailed(
        val cause: Throwable? = null,
    ) : OptiError()

    /** A required permission was not granted. */
    data class PermissionDenied(
        val permission: String,
    ) : OptiError()

    /** Human-readable description for UI display (safe — no internal paths or stack traces). */
    val displayMessage: String
        get() = when (this) {
            is Unknown              -> message
            is CameraUnavailable    -> "Camera unavailable: $reason"
            is ProcessingFailed     -> "Processing failed at $stage."
            is CapabilityNotSupported -> "$capability is not supported on this device."
            is StorageFailed        -> "Failed to save photo."
            is PermissionDenied     -> "Permission required: $permission"
        }
}
