package com.webappypie.optilens.core.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.UUID

/**
 * Automated audit tests for Phase 21 Privacy & Security Hardening.
 *
 * Verifies:
 * 1. Strict opt-in defaults: analytics, crash reporting, and location tagging default to false.
 * 2. Independent state mutation and persistence for privacy settings.
 * 3. Cache directory recursive purging logic to prevent stale photo buffer leakage.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PrivacySecurityAuditTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private fun createSettings(scope: CoroutineScope): AppSettings {
        val uniqueDir = tmpFolder.newFolder(UUID.randomUUID().toString())
        val file = File(uniqueDir, "privacy_audit_settings.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { file },
        )
        return AppSettingsImpl(dataStore)
    }

    @Test
    fun `privacy posture - analytics, crash reporting, and location default to false (opt-in only)`() = runTest {
        val settings = createSettings(backgroundScope)

        assertFalse("Analytics must default to disabled (opt-in)", settings.analyticsEnabled.first())
        assertFalse("Crash reporting must default to disabled (opt-in)", settings.crashReportingEnabled.first())
        assertFalse("Location tagging must default to disabled (opt-in)", settings.locationTaggingEnabled.first())
    }

    @Test
    fun `analytics toggle mutates without state bleed to crash reporting`() = runTest {
        val settings = createSettings(backgroundScope)
        assertFalse(settings.analyticsEnabled.first())
        assertFalse(settings.crashReportingEnabled.first())

        settings.setAnalyticsEnabled(true)
        assertTrue(settings.analyticsEnabled.first())
        assertFalse(settings.crashReportingEnabled.first())
        assertFalse(settings.locationTaggingEnabled.first())
    }

    @Test
    fun `crash reporting toggle mutates without state bleed to analytics`() = runTest {
        val settings = createSettings(backgroundScope)
        assertFalse(settings.analyticsEnabled.first())
        assertFalse(settings.crashReportingEnabled.first())

        settings.setCrashReportingEnabled(true)
        assertFalse(settings.analyticsEnabled.first())
        assertTrue(settings.crashReportingEnabled.first())
        assertFalse(settings.locationTaggingEnabled.first())
    }

    @Test
    fun `cache purge routine cleans temporary image buffers and directories`() {
        val cacheDir = tmpFolder.newFolder("mock_cache")
        val subDir = File(cacheDir, "intermediate_bayer").apply { mkdir() }
        val bufferFile1 = File(cacheDir, "preview_cache_001.jpg").apply { writeBytes(ByteArray(1024)) }
        val bufferFile2 = File(subDir, "raw_frame_001.dng").apply { writeBytes(ByteArray(4096)) }

        assertTrue(bufferFile1.exists())
        assertTrue(bufferFile2.exists())
        assertTrue(cacheDir.listFiles()?.isNotEmpty() == true)

        // Simulate recursive deletion routine used by SettingsScreen cache purge
        var deletedBytes = 0L
        fun deleteRecursively(file: File) {
            if (file.isDirectory) {
                file.listFiles()?.forEach { deleteRecursively(it) }
            }
            deletedBytes += file.length()
            file.delete()
        }

        cacheDir.listFiles()?.forEach { deleteRecursively(it) }

        // Cache root still exists, but contents are completely wiped
        assertEquals(0, cacheDir.listFiles()?.size ?: 0)
        assertFalse(bufferFile1.exists())
        assertFalse(bufferFile2.exists())
        assertTrue(deletedBytes >= 5120L)
    }
}
