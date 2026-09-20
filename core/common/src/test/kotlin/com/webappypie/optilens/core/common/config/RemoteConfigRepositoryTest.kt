package com.webappypie.optilens.core.common.config

import com.webappypie.optilens.core.common.feature.CustomFeatureFlags
import com.webappypie.optilens.core.common.feature.LocalFeatureFlags
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteConfigRepositoryTest {

    private val repository = LocalRemoteConfigRepository()

    @Test
    fun `default feature flags match local defaults`() = runTest {
        val flags = repository.featureFlags.value
        assertEquals(LocalFeatureFlags.adsEnabled, flags.adsEnabled)
        assertEquals(LocalFeatureFlags.activeAdProvider, flags.activeAdProvider)
        assertEquals(LocalFeatureFlags.adFrequencyIntervalSec, flags.adFrequencyIntervalSec)
        assertEquals(LocalFeatureFlags.adMinCapturesBetweenInterstitials, flags.adMinCapturesBetweenInterstitials)
        assertEquals(LocalFeatureFlags.superResolutionEnabled, flags.superResolutionEnabled)
    }

    @Test
    fun `fetchAndActivate returns true in offline default mode`() = runTest {
        val result = repository.fetchAndActivate()
        assertTrue(result)
    }

    @Test
    fun `updateFlags propagates new flag values to stream`() = runTest {
        val modified = CustomFeatureFlags(
            adsEnabled = false,
            adFrequencyIntervalSec = 600,
            maxBurstCountOverride = 10,
            promotionalCopy = "50% off holiday sale",
        )

        repository.updateFlags(modified)

        val active = repository.featureFlags.value
        assertEquals(false, active.adsEnabled)
        assertEquals(600, active.adFrequencyIntervalSec)
        assertEquals(10, active.maxBurstCountOverride)
        assertEquals("50% off holiday sale", active.promotionalCopy)
    }

    @Test
    fun `getDeviceProcessingOverride returns null when no overrides configured`() {
        assertNull(repository.getDeviceProcessingOverride("Pixel 6 Pro"))
    }

    @Test
    fun `getDeviceProcessingOverride matches regex pattern correctly`() {
        val sampleJson = """
            {
              "rules": [
                {
                  "deviceModelPattern": "Pixel 6.*",
                  "maxBurstFrames": 6,
                  "maxNightExposureMs": 1500,
                  "maxSrZoomScale": 2.0,
                  "disableZeroShutterLag": false
                },
                {
                  "deviceModelPattern": "SM-G998.*",
                  "maxBurstFrames": 8,
                  "maxNightExposureMs": 2000,
                  "maxSrZoomScale": 1.0,
                  "disableZeroShutterLag": true
                }
              ]
            }
        """.trimIndent()

        repository.updateFlags(CustomFeatureFlags(deviceSpecificOverridesJson = sampleJson))

        val pixelMatch = repository.getDeviceProcessingOverride("Pixel 6 Pro")
        assertNotNull(pixelMatch)
        assertEquals("Pixel 6.*", pixelMatch?.deviceModelPattern)
        assertEquals(6, pixelMatch?.maxBurstFrames)
        assertEquals(1500L, pixelMatch?.maxNightExposureMs)
        assertEquals(false, pixelMatch?.disableZeroShutterLag)
        assertEquals(2.0f, pixelMatch?.maxSrZoomScale)

        val samsungMatch = repository.getDeviceProcessingOverride("SM-G998B")
        assertNotNull(samsungMatch)
        assertEquals(8, samsungMatch?.maxBurstFrames)
        assertEquals(true, samsungMatch?.disableZeroShutterLag)

        val unmatched = repository.getDeviceProcessingOverride("iPhone14,2")
        assertNull(unmatched)
    }

    @Test
    fun `getDeviceProcessingOverride gracefully handles malformed json`() {
        repository.updateFlags(CustomFeatureFlags(deviceSpecificOverridesJson = "{ not valid json !! }"))
        val result = repository.getDeviceProcessingOverride("Pixel 6 Pro")
        assertNull(result)
    }
}
