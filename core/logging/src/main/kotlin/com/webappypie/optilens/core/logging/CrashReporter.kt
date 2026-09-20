package com.webappypie.optilens.core.logging

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Privacy-preserving crash reporting interface for production diagnostics.
 *
 * Privacy Invariants:
 * - Strictly zero personal identifying information (PII) recorded.
 * - Zero user account data, file system paths, or image bytes recorded.
 * - All log breadcrumbs and non-fatal messages run through [LogRedactor].
 * - Supports user opt-out toggle.
 */
interface CrashReporter {
    val isEnabled: Boolean
    fun setCrashReportingEnabled(enabled: Boolean)
    fun logBreadcrumb(message: String)
    fun setCustomKey(key: String, value: String)
    fun setCustomKey(key: String, value: Boolean)
    fun setCustomKey(key: String, value: Int)
    fun recordNonFatalException(throwable: Throwable)
}

@Singleton
class DefaultCrashReporter @Inject constructor(
    private val logger: AppLogger,
) : CrashReporter {

    private var _isEnabled: Boolean = true
    override val isEnabled: Boolean get() = _isEnabled

    override fun setCrashReportingEnabled(enabled: Boolean) {
        _isEnabled = enabled
        logger.i(TAG, "Crash reporting collection enabled state updated: $enabled")
    }

    override fun logBreadcrumb(message: String) {
        if (!_isEnabled) return
        val sanitized = LogRedactor.redact(message)
        logger.d(TAG, "Breadcrumb: $sanitized")
    }

    override fun setCustomKey(key: String, value: String) {
        if (!_isEnabled) return
        val sanitizedKey = LogRedactor.redact(key)
        val sanitizedVal = LogRedactor.redact(value)
        logger.d(TAG, "CustomKey: $sanitizedKey = $sanitizedVal")
    }

    override fun setCustomKey(key: String, value: Boolean) {
        if (!_isEnabled) return
        val sanitizedKey = LogRedactor.redact(key)
        logger.d(TAG, "CustomKey: $sanitizedKey = $value")
    }

    override fun setCustomKey(key: String, value: Int) {
        if (!_isEnabled) return
        val sanitizedKey = LogRedactor.redact(key)
        logger.d(TAG, "CustomKey: $sanitizedKey = $value")
    }

    override fun recordNonFatalException(throwable: Throwable) {
        if (!_isEnabled) return
        val sanitizedMsg = LogRedactor.redactThrowableMessage(throwable)
        logger.w(TAG, "NonFatalException: ${throwable.javaClass.simpleName} ($sanitizedMsg)", throwable)
    }

    companion object {
        private const val TAG = "CrashReporter"
    }
}
