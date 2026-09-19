package com.webappypie.optilens.core.camera.quirks

import com.webappypie.optilens.core.camera.fixtures.CameraCapabilityFixtures
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DeviceQuirkRegistryTest {

    private lateinit var registry: DeviceQuirkRegistry

    @Before
    fun setUp() {
        registry = DeviceQuirkRegistry()
    }

    @Test
    fun `samsung devices trigger samsung preview aspect quirk`() {
        val quirks = registry.getApplicableQuirks(
            manufacturer = "Samsung",
            model = "SM-S918B",
            device = "dm3q",
            apiLevel = 34,
            cameras = emptyList(),
        )

        assertTrue(quirks.any { it is SamsungPreviewAspectQuirk })
    }

    @Test
    fun `pixel 7 device triggers pixel ae convergence quirk`() {
        val quirks = registry.getApplicableQuirks(
            manufacturer = "Google",
            model = "Pixel 7 Pro",
            device = "cheetah",
            apiLevel = 34,
            cameras = emptyList(),
        )

        assertTrue(quirks.any { it is PixelAeConvergenceQuirk })
    }

    @Test
    fun `device with limited hardware level triggers limited stream constraint quirk`() {
        val fixture = CameraCapabilityFixtures.createEntryLevelProfile()
        val quirks = registry.getApplicableQuirks(
            manufacturer = "Generic",
            model = "BudgetOne",
            device = "budget1",
            apiLevel = 30,
            cameras = fixture.cameras,
        )

        assertTrue(quirks.any { it is LimitedHardwareStreamConstraintQuirk })
    }

    @Test
    fun `device with high megapixel output triggers high resolution capture lag quirk`() {
        val fixture = CameraCapabilityFixtures.createFlagshipProfile() // 8192x6144 = 50.3 MP
        val quirks = registry.getApplicableQuirks(
            manufacturer = "Generic",
            model = "UltraPhone",
            device = "uphone",
            apiLevel = 34,
            cameras = fixture.cameras,
        )

        assertTrue(quirks.any { it is HighResolutionCaptureLagQuirk })
    }

    @Test
    fun `clean standard device triggers no unnecessary quirks`() {
        val fixture = CameraCapabilityFixtures.createMidRangeProfile()
        val quirks = registry.getApplicableQuirks(
            manufacturer = "Motorola",
            model = "Edge 40",
            device = "rtwo",
            apiLevel = 33,
            cameras = fixture.cameras,
        )

        assertFalse(quirks.any { it is SamsungPreviewAspectQuirk })
        assertFalse(quirks.any { it is PixelAeConvergenceQuirk })
        assertFalse(quirks.any { it is LimitedHardwareStreamConstraintQuirk })
    }
}
