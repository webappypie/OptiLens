package com.webappypie.optilens.core.logging

import com.webappypie.optilens.core.common.build.BuildInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class SystemHealthSnapshot(
    val heapAllocatedMb: Long,
    val heapMaxMb: Long,
    val activeQuirks: List<String> = emptyList(),
    val successfulCaptures: Int = 0,
    val aiEnhanceKeepRate: Float = 1.0f,
    val recentProcessingErrorsCount: Int = 0,
)

/**
 * Aggregates anonymous application diagnostics, device cohort metadata, and system health
 * into an exportable report without photo content or personal identifiable information (PII).
 */
@Singleton
class DiagnosticsExportManager @Inject constructor(
    private val buildInfo: BuildInfo,
) {
    companion object {
        private val SENSITIVE_PATTERN = Regex("""(content://|file://|/data/user|/storage/emulated|gps|latitude|longitude|\.jpg|\.png|\.dng)""", RegexOption.IGNORE_CASE)
    }

    /**
     * Generates a clean JSON diagnostics export guaranteed to have zero photo content.
     */
    fun exportDiagnosticsJson(
        cohort: DeviceCohortMetadata = DeviceCohortMetadata.current(),
        health: SystemHealthSnapshot = captureHealth(),
        additionalNotes: String? = null,
    ): String {
        val sanitizedNotes = additionalNotes?.let { sanitize(it) } ?: ""
        val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())

        return buildString {
            append("{\n")
            append("  \"timestamp\": \"$timestamp\",\n")
            append("  \"build\": {\n")
            append("    \"versionName\": \"${buildInfo.versionName}\",\n")
            append("    \"versionCode\": ${buildInfo.versionCode},\n")
            append("    \"applicationId\": \"${buildInfo.applicationId}\",\n")
            append("    \"isDebug\": ${buildInfo.isDebug}\n")
            append("  },\n")
            append("  \"deviceCohort\": {\n")
            append("    \"socFamily\": \"${cohort.socFamily}\",\n")
            append("    \"ramBucket\": \"${cohort.ramBucket}\",\n")
            append("    \"apiLevel\": ${cohort.apiLevel},\n")
            append("    \"manufacturerBucket\": \"${cohort.manufacturerBucket}\",\n")
            append("    \"hardwareLevel\": \"${cohort.hardwareLevel}\",\n")
            append("    \"quirkCount\": ${cohort.quirkCount}\n")
            append("  },\n")
            append("  \"health\": {\n")
            append("    \"heapAllocatedMb\": ${health.heapAllocatedMb},\n")
            append("    \"heapMaxMb\": ${health.heapMaxMb},\n")
            append("    \"successfulCaptures\": ${health.successfulCaptures},\n")
            append("    \"aiEnhanceKeepRate\": ${String.format(Locale.US, "%.2f", health.aiEnhanceKeepRate)},\n")
            append("    \"recentProcessingErrorsCount\": ${health.recentProcessingErrorsCount},\n")
            append("    \"activeQuirks\": [${health.activeQuirks.joinToString(",") { "\"$it\"" }}]\n")
            append("  },\n")
            append("  \"containsPhotoContent\": false,\n")
            append("  \"containsPii\": false,\n")
            append("  \"sanitizedNotes\": \"$sanitizedNotes\"\n")
            append("}")
        }
    }

    /**
     * Generates a Markdown report suitable for customer support or bug tracking.
     */
    fun exportDiagnosticsMarkdown(
        cohort: DeviceCohortMetadata = DeviceCohortMetadata.current(),
        health: SystemHealthSnapshot = captureHealth(),
        additionalNotes: String? = null,
    ): String = buildString {
        appendLine("# OptiLens Diagnostics Report")
        appendLine()
        appendLine("> **Privacy Notice:** This report contains only non-sensitive device configuration and system performance telemetry. No image binary data, thumbnails, photo filenames, or location coordinates are included.")
        appendLine()
        appendLine("## Build Information")
        appendLine("- **Version:** ${buildInfo.versionName} (${buildInfo.versionCode})")
        appendLine("- **Application ID:** ${buildInfo.applicationId}")
        appendLine("- **Debug Build:** ${buildInfo.isDebug}")
        appendLine()
        appendLine("## Device Cohort")
        appendLine("- **SoC Family:** ${cohort.socFamily}")
        appendLine("- **RAM Bucket:** ${cohort.ramBucket}")
        appendLine("- **Android API Level:** ${cohort.apiLevel}")
        appendLine("- **Manufacturer Bucket:** ${cohort.manufacturerBucket}")
        appendLine("- **Hardware Level:** ${cohort.hardwareLevel}")
        appendLine("- **Quirks Count:** ${cohort.quirkCount}")
        appendLine()
        appendLine("## System Health & Telemetry")
        appendLine("- **Heap Usage:** ${health.heapAllocatedMb} MB / ${health.heapMaxMb} MB")
        appendLine("- **Successful Captures:** ${health.successfulCaptures}")
        appendLine("- **AI Keep Rate:** ${String.format(Locale.US, "%.1f", health.aiEnhanceKeepRate * 100f)}%")
        appendLine("- **Recent Errors:** ${health.recentProcessingErrorsCount}")
        appendLine("- **Active Hardware Quirks:** ${if (health.activeQuirks.isEmpty()) "None" else health.activeQuirks.joinToString(", ")}")
        if (!additionalNotes.isNullOrBlank()) {
            appendLine()
            appendLine("## Notes")
            appendLine(sanitize(additionalNotes))
        }
    }

    private fun captureHealth(): SystemHealthSnapshot {
        val runtime = Runtime.getRuntime()
        val allocated = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        val max = runtime.maxMemory() / (1024 * 1024)
        return SystemHealthSnapshot(
            heapAllocatedMb = allocated,
            heapMaxMb = max,
        )
    }

    /**
     * Strips accidental paths or sensitive patterns from user notes.
     */
    fun sanitize(input: String): String {
        return input.replace(SENSITIVE_PATTERN, "[REDACTED]")
            .replace("\n", " ")
            .replace("\"", "\\\"")
    }
}
