package com.webappypie.optilens.core.logging

import javax.inject.Inject

/**
 * No-op logger for release builds.
 * All calls are completely inlined away by R8 — zero runtime overhead.
 */
class NoOpLogger @Inject constructor() : AppLogger {
    override fun v(tag: String, message: String) = Unit
    override fun d(tag: String, message: String) = Unit
    override fun i(tag: String, message: String) = Unit
    override fun w(tag: String, message: String, cause: Throwable?) = Unit
    override fun e(tag: String, message: String, cause: Throwable?) = Unit
    override fun perf(tag: String, event: String, millis: Long) = Unit
}
