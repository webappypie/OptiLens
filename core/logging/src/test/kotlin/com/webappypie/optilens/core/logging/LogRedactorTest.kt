package com.webappypie.optilens.core.logging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogRedactorTest {

    @Test
    fun `redact replaces absolute file paths with PATH_REDACTED`() {
        val msg1 = "Saved image to /data/user/0/com.webappypie.optilens/cache/temp_123.jpg successfully"
        val redacted1 = LogRedactor.redact(msg1)
        assertFalse(redacted1.contains("/data/user/0"))
        assertTrue(redacted1.contains("[PATH_REDACTED]"))

        val msg2 = "Accessing /storage/emulated/0/DCIM/Camera/IMG_2026.jpg"
        val redacted2 = LogRedactor.redact(msg2)
        assertFalse(redacted2.contains("/storage/emulated/0"))
        assertTrue(redacted2.contains("[PATH_REDACTED]"))
    }

    @Test
    fun `redact replaces content URIs with CONTENT_URI_REDACTED`() {
        val msg = "Opened photo at content://media/external/images/media/987654 for processing"
        val redacted = LogRedactor.redact(msg)
        assertFalse(redacted.contains("content://media/external"))
        assertTrue(redacted.contains("[CONTENT_URI_REDACTED]"))
    }

    @Test
    fun `redact replaces email addresses with EMAIL_REDACTED`() {
        val msg = "User contact feedback from user.name+tag@example.com received"
        val redacted = LogRedactor.redact(msg)
        assertFalse(redacted.contains("user.name+tag@example.com"))
        assertTrue(redacted.contains("[EMAIL_REDACTED]"))
    }

    @Test
    fun `redact replaces IPv4 addresses with IP_REDACTED`() {
        val msg = "Socket connection attempt to 192.168.1.100 refused"
        val redacted = LogRedactor.redact(msg)
        assertFalse(redacted.contains("192.168.1.100"))
        assertTrue(redacted.contains("[IP_REDACTED]"))
    }

    @Test
    fun `redact replaces API keys and auth tokens with TOKEN_REDACTED`() {
        val msg = "Header: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"
        val redacted = LogRedactor.redact(msg)
        assertFalse(redacted.contains("eyJhbGci"))
        assertTrue(redacted.contains("[TOKEN_REDACTED]"))

        val apiKeyMsg = "Google API key: AIzaSyD1234567890abcdefghijklmnopqrstuvw"
        val redactedKey = LogRedactor.redact(apiKeyMsg)
        assertFalse(redactedKey.contains("AIzaSyD1234567890"))
        assertTrue(redactedKey.contains("[API_KEY_REDACTED]"))
    }

    @Test
    fun `redact leaves clean non-sensitive log messages unchanged`() {
        val cleanMsg = "Viewfinder pipeline started at 30 fps in PHOTO mode"
        val redacted = LogRedactor.redact(cleanMsg)
        assertEquals(cleanMsg, redacted)
    }

    @Test
    fun `redactingLogger decorates logger and scrubs calls`() {
        val loggedMessages = mutableListOf<String>()
        val mockLogger = object : AppLogger {
            override fun v(tag: String, message: String) { loggedMessages.add("$tag: $message") }
            override fun d(tag: String, message: String) { loggedMessages.add("$tag: $message") }
            override fun i(tag: String, message: String) { loggedMessages.add("$tag: $message") }
            override fun w(tag: String, message: String, cause: Throwable?) { loggedMessages.add("$tag: $message") }
            override fun e(tag: String, message: String, cause: Throwable?) { loggedMessages.add("$tag: $message") }
            override fun perf(tag: String, event: String, millis: Long) { loggedMessages.add("$tag: $event: $millis") }
        }

        val redactingLogger = RedactingLogger(mockLogger)
        redactingLogger.d("CameraStorage", "Cached at /data/user/0/cache/foo.raw with key=secretToken12345")

        assertEquals(1, loggedMessages.size)
        val out = loggedMessages[0]
        assertFalse(out.contains("/data/user/0"))
        assertFalse(out.contains("secretToken12345"))
        assertTrue(out.contains("[PATH_REDACTED]"))
        assertTrue(out.contains("[TOKEN_REDACTED]"))
    }
}
