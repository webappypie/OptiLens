package com.webappypie.optilens.core.logging

/**
 * Decorator for [AppLogger] that transparently runs all tags and log messages
 * through [LogRedactor] before forwarding to the underlying delegate logger.
 */
class RedactingLogger(
    private val delegate: AppLogger,
) : AppLogger {

    override fun v(tag: String, message: String) {
        delegate.v(LogRedactor.redact(tag), LogRedactor.redact(message))
    }

    override fun d(tag: String, message: String) {
        delegate.d(LogRedactor.redact(tag), LogRedactor.redact(message))
    }

    override fun i(tag: String, message: String) {
        delegate.i(LogRedactor.redact(tag), LogRedactor.redact(message))
    }

    override fun w(tag: String, message: String, cause: Throwable?) {
        delegate.w(LogRedactor.redact(tag), LogRedactor.redact(message), cause)
    }

    override fun e(tag: String, message: String, cause: Throwable?) {
        delegate.e(LogRedactor.redact(tag), LogRedactor.redact(message), cause)
    }

    override fun perf(tag: String, event: String, millis: Long) {
        delegate.perf(LogRedactor.redact(tag), LogRedactor.redact(event), millis)
    }
}
