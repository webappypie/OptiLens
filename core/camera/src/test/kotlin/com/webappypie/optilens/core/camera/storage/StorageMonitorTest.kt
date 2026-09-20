package com.webappypie.optilens.core.camera.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageMonitorTest {

    @Test
    fun `storage state calculates megabytes and formatting properly`() {
        val normalState = StorageState(
            freeBytes = 15_000_000_000L, // ~13.9 GB
            totalBytes = 64_000_000_000L,
            status = StorageStatus.NORMAL,
            cacheSizeBytes = 12_500_000L, // ~11.9 MB
        )

        assertEquals(StorageStatus.NORMAL, normalState.status)
        assertTrue(normalState.isCaptureAllowed)
        assertTrue(normalState.formattedFree.contains("GB free"))
        assertTrue(normalState.formattedCacheSize.contains("MB"))
    }

    @Test
    fun `low and critical storage states correctly restrict capture capability`() {
        val lowState = StorageState(
            freeBytes = 250 * 1024 * 1024L, // 250 MB
            totalBytes = 64_000_000_000L,
            status = StorageStatus.LOW,
        )
        assertEquals(StorageStatus.LOW, lowState.status)
        assertTrue(lowState.isCaptureAllowed)
        assertTrue(lowState.formattedFree.contains("250 MB free"))

        val criticalState = StorageState(
            freeBytes = 40 * 1024 * 1024L, // 40 MB
            totalBytes = 64_000_000_000L,
            status = StorageStatus.CRITICAL,
        )
        assertEquals(StorageStatus.CRITICAL, criticalState.status)
        assertFalse(criticalState.isCaptureAllowed)
    }

    @Test
    fun `threshold constants conform to safety specifications`() {
        assertEquals(500L, StorageMonitor.LOW_THRESHOLD_MB)
        assertEquals(100L, StorageMonitor.CRITICAL_THRESHOLD_MB)
        assertEquals(86400000L, StorageMonitor.DEFAULT_EXPIRATION_MS)
    }
}
