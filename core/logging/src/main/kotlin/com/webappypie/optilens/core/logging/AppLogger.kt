package com.webappypie.optilens.core.logging

/**
 * AppLogger abstraction.
 *
 * Rules enforced across all implementations:
 * - NEVER log image bytes, pixel data, or Base64-encoded images.
 * - NEVER log recognized text (OCR results, text from photos).
 * - NEVER log precise GPS coordinates.
 * - NEVER log face identity or biometric data.
 * - Release builds use [NoOpLogger] — no data ever leaves the device via logs.
 */
interface AppLogger {

    fun v(tag: String, message: String)
    fun d(tag: String, message: String)
    fun i(tag: String, message: String)
    fun w(tag: String, message: String, cause: Throwable? = null)
    fun e(tag: String, message: String, cause: Throwable? = null)

    /**
     * Log a performance timing event.
     * @param tag    Component tag.
     * @param event  Short event name (e.g. "frame_capture", "hdr_merge").
     * @param millis Duration in milliseconds.
     */
    fun perf(tag: String, event: String, millis: Long)
}

/** Convenience extension: log at debug level using the class name as tag. */
inline fun <reified T : Any> AppLogger.d(message: String) =
    d(T::class.java.simpleName, message)

/** Convenience extension: log at error level using the class name as tag. */
inline fun <reified T : Any> AppLogger.e(message: String, cause: Throwable? = null) =
    e(T::class.java.simpleName, message, cause)
