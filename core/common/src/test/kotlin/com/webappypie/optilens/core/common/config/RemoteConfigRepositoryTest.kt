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

    @Test
    fun `getEffectiveQuirks applies emergency additions and suppressions`() {
        val sampleJson = """
            {
              "rules": [
                {
                  "deviceModelPattern": "SM-S928.*",
                  "disabledQuirkIds": ["samsung_preview_aspect_quirk"],
                  "additionalQuirkIds": ["samsung_lens_switch_lag_quirk", "emergency_vendor_tag_quirk"],
                  "disabledModes": ["NIGHT"]
                }
              ]
            }
        """.trimIndent()

        repository.updateFlags(CustomFeatureFlags(deviceSpecificOverridesJson = sampleJson))

        val baseQuirks = listOf("samsung_preview_aspect_quirk", "limited_stream_constraint_quirk")
        val effective = repository.getEffectiveQuirks(baseQuirks, "SM-S928B")

        // samsung_preview_aspect_quirk should be suppressed
        assertTrue("Suppressed quirk must not be present", "samsung_preview_aspect_quirk" !in effective)
        // limited_stream_constraint_quirk should remain
        assertTrue("Unrelated base quirk must remain", "limited_stream_constraint_quirk" in effective)
        // emergency quirks should be added
        assertTrue("Added quirk must be present", "samsung_lens_switch_lag_quirk" in effective)
        assertTrue("Emergency quirk must be present", "emergency_vendor_tag_quirk" in effective)
    }

    @Test
    fun `device override for one model never disables features globally on other devices`() {
        val sampleJson = """
            {
              "rules": [
                {
                  "deviceModelPattern": "BrokenPhone.*",
                  "disabledModes": ["RAW", "NIGHT"],
                  "emergencyFallbackReason": "HAL crash on rapid RAW stream allocation"
                }
              ]
            }
        """.trimIndent()

        repository.updateFlags(CustomFeatureFlags(deviceSpecificOverridesJson = sampleJson))

        val brokenDeviceOverride = repository.getDeviceProcessingOverride("BrokenPhone X1")
        assertNotNull(brokenDeviceOverride)
        assertEquals(listOf("RAW", "NIGHT"), brokenDeviceOverride?.disabledModes)

        // Pixel 8 Pro or Galaxy S24 Ultra must NOT have any disabled modes!
        val pixelOverride = repository.getDeviceProcessingOverride("Pixel 8 Pro")
        assertNull(pixelOverride)

        val galaxyOverride = repository.getDeviceProcessingOverride("SM-S928B")
        assertNull(galaxyOverride)
    }
}
