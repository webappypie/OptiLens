package com.webappypie.optilens.core.logging

import android.util.Log
import javax.inject.Inject

/**
 * Debug-only logger that routes to Android [Log].
 * Never installed in release builds.
 */
class LogcatLogger @Inject constructor() : AppLogger {

    override fun v(tag: String, message: String) { Log.v(tag, message) }
    override fun d(tag: String, message: String) { Log.d(tag, message) }
    override fun i(tag: String, message: String) { Log.i(tag, message) }
    override fun w(tag: String, message: String, cause: Throwable?) {
        if (cause != null) Log.w(tag, message, cause) else Log.w(tag, message)
    }
    override fun e(tag: String, message: String, cause: Throwable?) {
        if (cause != null) Log.e(tag, message, cause) else Log.e(tag, message)
    }
    override fun perf(tag: String, event: String, millis: Long) {
        Log.d(tag, "PERF [$event] ${millis}ms")
    }
}
