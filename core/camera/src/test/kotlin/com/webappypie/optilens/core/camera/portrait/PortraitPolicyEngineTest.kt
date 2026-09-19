package com.webappypie.optilens.core.camera.portrait

import com.webappypie.optilens.core.camera.model.CameraDeviceProfile
import com.webappypie.optilens.core.camera.model.CameraHardwareLevel
import com.webappypie.optilens.core.camera.model.DetectedFace
import com.webappypie.optilens.core.camera.model.ExtensionSupport
import com.webappypie.optilens.core.camera.model.LensFacing
import com.webappypie.optilens.core.camera.model.NormalizedRect
import com.webappypie.optilens.core.camera.model.PerformanceTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PortraitPolicyEngineTest {

    private lateinit var engine: PortraitPolicyEngine

    @Before
    fun setUp() {
        engine = PortraitPolicyEngine()
    }

    private fun createProfile(hasVendorBokeh: Boolean): CameraDeviceProfile {
        return CameraDeviceProfile(
            id = "0",
            lensFacing = LensFacing.BACK,
            hardwareLevel = CameraHardwareLevel.FULL,
            extensions = ExtensionSupport(bokeh = hasVendorBokeh),
        )
    }

    @Test
    fun `auto policy selects vendor bokeh when available on mid-range hardware`() {
        val profile = createProfile(hasVendorBokeh = true)
        val face = DetectedFace(
            bounds = NormalizedRect(0.3f, 0.2f, 0.7f, 0.6f),
            meanLuminance = 120f,
        )

        val plan = engine.evaluatePolicy(
            activeCameraProfile = profile,
            performanceTier = PerformanceTier.MID_RANGE,
            detectedFaces = listOf(face),
            preference = PortraitPolicyPreference.AUTO,
        )

        assertEquals(PortraitModeType.VENDOR_BOKEH, plan.mode)
        assertEquals(1, plan.faceCount)
        assertFalse(plan.isBacklitScene)
    }

    @Test
    fun `auto policy selects custom software bokeh when vendor extension unavailable`() {
        val profile = createProfile(hasVendorBokeh = false)
        val face = DetectedFace(
            bounds = NormalizedRect(0.3f, 0.2f, 0.7f, 0.6f),
            meanLuminance = 120f,
        )

        val plan = engine.evaluatePolicy(
            activeCameraProfile = profile,
            performanceTier = PerformanceTier.MID_RANGE,
            detectedFaces = listOf(face),
            preference = PortraitPolicyPreference.AUTO,
        )

        assertEquals(PortraitModeType.CUSTOM_SOFTWARE_BOKEH, plan.mode)
        assertEquals(1, plan.faceCount)
    }

    @Test
    fun `prefer custom policy overrides vendor extension availability`() {
        val profile = createProfile(hasVendorBokeh = true)

        val plan = engine.evaluatePolicy(
            activeCameraProfile = profile,
            preference = PortraitPolicyPreference.PREFER_CUSTOM,
        )

        assertEquals(PortraitModeType.CUSTOM_SOFTWARE_BOKEH, plan.mode)
    }

    @Test
    fun `backlit scene triggers face exposure compensation and custom software path`() {
        val profile = createProfile(hasVendorBokeh = true)
        // Background bright (200.0f), face underexposed (80.0f) -> ratio 2.5 > 1.6
        val face = DetectedFace(
            bounds = NormalizedRect(0.2f, 0.2f, 0.6f, 0.6f),
            meanLuminance = 80.0f,
        )

        val plan = engine.evaluatePolicy(
            activeCameraProfile = profile,
            detectedFaces = listOf(face),
            sceneLuminance = 200.0f,
            preference = PortraitPolicyPreference.AUTO,
        )

        assertTrue(plan.isBacklitScene)
        assertTrue(plan.faceExposureCompensationEv > 0.3f)
        // Custom software bokeh is preferred for fine-grained backlit exposure balancing
        assertEquals(PortraitModeType.CUSTOM_SOFTWARE_BOKEH, plan.mode)
    }

    @Test
    fun `multi-face group portraits are supported and all faces tracked`() {
        val profile = createProfile(hasVendorBokeh = false)
        val faces = listOf(
            DetectedFace(bounds = NormalizedRect(0.1f, 0.2f, 0.4f, 0.5f), meanLuminance = 110f),
            DetectedFace(bounds = NormalizedRect(0.6f, 0.2f, 0.9f, 0.5f), meanLuminance = 115f),
        )

        val plan = engine.evaluatePolicy(
            activeCameraProfile = profile,
            detectedFaces = faces,
            aperture = PortraitAperture.F4_0,
        )

        assertEquals(2, plan.faceCount)
        assertEquals(PortraitAperture.F4_0, plan.aperture)
        assertEquals(PortraitModeType.CUSTOM_SOFTWARE_BOKEH, plan.mode)
    }
}
