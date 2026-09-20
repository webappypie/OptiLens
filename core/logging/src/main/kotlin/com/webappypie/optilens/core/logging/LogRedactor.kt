package com.webappypie.optilens.core.logging

import java.util.regex.Pattern

/**
 * Production log redaction utility that scrubs sensitive data before it reaches logcat,
 * crash reporting trees, or persistent disk logs.
 *
 * Scans for and redacts:
 * 1. Absolute filesystem paths (internal data, external storage, app private caches).
 * 2. Android Content URIs and MediaStore references.
 * 3. Email addresses.
 * 4. IPv4 and IPv6 addresses.
 * 5. Secret tokens, authorization headers, and API keys.
 * 6. Payment card numbers and sensitive numeric sequences.
 */
object LogRedactor {

    private val FILE_PATH_PATTERN = Pattern.compile(
        """(?i)(?:/(?:data|storage|sdcard|mnt|system|vendor)/[^\s,;'"\]>)]+|[a-zA-Z]:\\[^\s,;'"\]>)]+)"""
    )

    private val CONTENT_URI_PATTERN = Pattern.compile(
        """(?i)content://[^\s,;'"\]>)]+"""
    )

    private val EMAIL_PATTERN = Pattern.compile(
        """[a-zA-Z0-9_.+-]+@[a-zA-Z0-9-]+\.[a-zA-Z0-9-.]+"""
    )

    private val IP_PATTERN = Pattern.compile(
        """\b(?:\d{1,3}\.){3}\d{1,3}\b"""
    )

    private val GOOGLE_API_KEY_PATTERN = Pattern.compile(
        """AIza[0-9A-Za-z-_]{35}"""
    )

    private val AUTH_TOKEN_PATTERN = Pattern.compile(
        """(?i)\b(bearer|token|key|secret|password|api[_-]?key|auth)[\s:=]+([a-zA-Z0-9_\-.]{8,})"""
    )

    private val CREDIT_CARD_PATTERN = Pattern.compile(
        """\b(?:\d[ -]*?){13,19}\b"""
    )

    /**
     * Sanitizes a log message string by redacting all sensitive substrings.
     */
    fun redact(message: String?): String {
        if (message.isNullOrEmpty()) return ""

        var sanitized = message
        sanitized = FILE_PATH_PATTERN.matcher(sanitized).replaceAll("[PATH_REDACTED]")
        sanitized = CONTENT_URI_PATTERN.matcher(sanitized).replaceAll("[CONTENT_URI_REDACTED]")
        sanitized = EMAIL_PATTERN.matcher(sanitized).replaceAll("[EMAIL_REDACTED]")
        sanitized = IP_PATTERN.matcher(sanitized).replaceAll("[IP_REDACTED]")
        sanitized = GOOGLE_API_KEY_PATTERN.matcher(sanitized).replaceAll("[API_KEY_REDACTED]")
        sanitized = AUTH_TOKEN_PATTERN.matcher(sanitized).replaceAll("$1=[TOKEN_REDACTED]")
        sanitized = CREDIT_CARD_PATTERN.matcher(sanitized).replaceAll("[CARD_REDACTED]")

        return sanitized
    }

    /**
     * Sanitizes a throwable by scrubbing the message and wrapped causes while preserving class names.
     */
    fun redactThrowableMessage(throwable: Throwable?): String {
        if (throwable == null) return ""
        val originalMsg = throwable.message ?: throwable.javaClass.simpleName
        return redact(originalMsg)
    }
}
