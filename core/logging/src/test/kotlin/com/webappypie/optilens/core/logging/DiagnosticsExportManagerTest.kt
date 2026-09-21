package com.webappypie.optilens.core.logging

import com.webappypie.optilens.core.common.build.BuildInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsExportManagerTest {

    private val fakeBuildInfo = object : BuildInfo {
        override val versionName: String = "1.0.0"
        override val versionCode: Int = 100
        override val isDebug: Boolean = false
        override val applicationId: String = "com.webappypie.optilens"
        override val showDiagnostics: Boolean = false
    }

    @Test
    fun `exportDiagnosticsJson contains no photo content and no pii`() {
        val manager = DiagnosticsExportManager(fakeBuildInfo)
        val cohort = DeviceCohortMetadata(
            socFamily = "Snapdragon",
            ramBucket = "6GB",
            apiLevel = 34,
            manufacturerBucket = "Samsung",
            hardwareLevel = "FULL",
            quirkCount = 1,
        )
        val health = SystemHealthSnapshot(
            heapAllocatedMb = 48,
            heapMaxMb = 512,
            activeQuirks = listOf("Nexus6PQuirk"),
            successfulCaptures = 42,
            aiEnhanceKeepRate = 0.95f,
            recentProcessingErrorsCount = 0,
        )

        val json = manager.exportDiagnosticsJson(cohort, health)

        assertTrue(json.contains("\"containsPhotoContent\": false"))
        assertTrue(json.contains("\"containsPii\": false"))
        assertTrue(json.contains("\"socFamily\": \"Snapdragon\""))
        assertTrue(json.contains("\"successfulCaptures\": 42"))
        assertFalse(json.contains(".jpg"))
        assertFalse(json.contains(".png"))
        assertFalse(json.contains(".dng"))
    }

    @Test
    fun `sanitization redacts photo file paths and gps terms from notes`() {
        val manager = DiagnosticsExportManager(fakeBuildInfo)
        val notes = "User encountered crash while viewing file:///storage/emulated/0/DCIM/Camera/IMG_2026.jpg with GPS coordinates"

        val sanitized = manager.sanitize(notes)

        assertFalse(sanitized.contains("file://"))
        assertFalse(sanitized.contains(".jpg"))
        assertFalse(sanitized.contains("storage/emulated"))
        assertFalse(sanitized.contains("GPS"))
        assertTrue(sanitized.contains("[REDACTED]"))
    }

    @Test
    fun `exportDiagnosticsMarkdown formats correct markdown structure`() {
        val manager = DiagnosticsExportManager(fakeBuildInfo)
        val cohort = DeviceCohortMetadata(
            socFamily = "Tensor",
            ramBucket = ">12GB",
            apiLevel = 35,
            manufacturerBucket = "Google",
            hardwareLevel = "FULL",
            quirkCount = 0,
        )
        val health = SystemHealthSnapshot(
            heapAllocatedMb = 64,
            heapMaxMb = 768,
        )

        val md = manager.exportDiagnosticsMarkdown(cohort, health)

        assertTrue(md.startsWith("# OptiLens Diagnostics Report"))
        assertTrue(md.contains("Tensor"))
        assertTrue(md.contains("No image binary data, thumbnails, photo filenames, or location coordinates are included."))
    }
}
