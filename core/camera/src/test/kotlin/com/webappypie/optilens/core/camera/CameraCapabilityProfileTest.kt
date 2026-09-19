package com.webappypie.optilens.core.camera

import com.webappypie.optilens.core.camera.fixtures.CameraCapabilityFixtures
import com.webappypie.optilens.core.camera.model.CameraCapabilityProfile
import com.webappypie.optilens.core.camera.model.CameraHardwareLevel
import com.webappypie.optilens.core.camera.model.PerformanceTier
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraCapabilityProfileTest {

    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
    }

    @Test
    fun `profile serializes to json and deserializes without loss`() {
        val original = CameraCapabilityFixtures.createFlagshipProfile()
        val serialized = json.encodeToString(original)

        val restored = json.decodeFromString<CameraCapabilityProfile>(serialized)

        assertEquals(original.deviceModel, restored.deviceModel)
        assertEquals(original.performanceTier, restored.performanceTier)
        assertEquals(original.cameras.size, restored.cameras.size)
        assertEquals(original.cameras.first().hardwareLevel, restored.cameras.first().hardwareLevel)
        assertEquals(original.cameras.first().resolutions.maxJpegSize, restored.cameras.first().resolutions.maxJpegSize)
        assertEquals(original.cameras.first().streamCapabilities.supportsRaw, restored.cameras.first().streamCapabilities.supportsRaw)
        assertEquals(original.cameras.first().stabilization.opticalImageStabilization, restored.cameras.first().stabilization.opticalImageStabilization)
    }

    @Test
    fun `toLegacyCameraCapability produces valid legacy model`() {
        val profile = CameraCapabilityFixtures.createFlagshipProfile()
        val legacy = profile.toLegacyCameraCapability()

        assertTrue(legacy.hasRearCamera)
        assertTrue(legacy.hasFrontCamera)
        assertTrue(legacy.hasOis)
        assertTrue(legacy.supportsRaw)
        assertTrue(legacy.supportsHdrCapture)
        assertTrue(legacy.hasLogicalMultiCamera)
        assertEquals(3, legacy.maxCaptureStreams)
        assertTrue(legacy.availableZoomRatios.contains(1.0f))
        assertTrue(legacy.availableZoomRatios.contains(0.5f))
        assertTrue(legacy.availableZoomRatios.contains(2.0f))
    }

    @Test
    fun `computeAvailableZoomRatios handles ultra wide and telephoto`() {
        val camera = CameraCapabilityFixtures.createFlagshipProfile().cameras.first()
        val zoomRatios = camera.computeAvailableZoomRatios()

        assertEquals(listOf(0.5f, 1.0f, 2.0f, 3.0f, 5.0f, 10.0f), zoomRatios)
    }

    @Test
    fun `primaryBackCamera and primaryFrontCamera getters return matching cameras`() {
        val profile = CameraCapabilityFixtures.createFlagshipProfile()
        val back = profile.primaryBackCamera
        val front = profile.primaryFrontCamera

        assertNotNull(back)
        assertNotNull(front)
        assertEquals("0", back?.id)
        assertEquals("1", front?.id)
    }
}
