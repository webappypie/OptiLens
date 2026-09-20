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

    @Test
    fun `samsung galaxy ultra triggers lens switch lag and isocell raw black level quirk`() {
        val fixture = CameraCapabilityFixtures.createFlagshipProfile()
        val quirks = registry.getApplicableQuirks(
            manufacturer = "Samsung",
            model = "Galaxy S24 Ultra",
            device = "e3q",
            apiLevel = 34,
            cameras = fixture.cameras,
        )

        assertTrue(quirks.any { it is SamsungPreviewAspectQuirk })
        assertTrue(quirks.any { it is SamsungLensSwitchLagQuirk })
        assertTrue(quirks.any { it is SamsungBlackLevelOffsetQuirk })
    }

    @Test
    fun `google pixel 8 triggers ois settling and ae convergence quirks`() {
        val fixture = CameraCapabilityFixtures.createFlagshipProfile()
        val quirks = registry.getApplicableQuirks(
            manufacturer = "Google",
            model = "Pixel 8 Pro",
            device = "husky",
            apiLevel = 34,
            cameras = fixture.cameras,
        )

        assertTrue(quirks.any { it is PixelAeConvergenceQuirk })
        assertTrue(quirks.any { it is PixelOisSettlingQuirk })
    }

    @Test
    fun `xiaomi ultra high-resolution camera triggers burst timestamp and remosaic quirks`() {
        val fixture = CameraCapabilityFixtures.createFlagshipProfile()
        val quirks = registry.getApplicableQuirks(
            manufacturer = "Xiaomi",
            model = "Xiaomi 14 Ultra",
            device = "aurora",
            apiLevel = 34,
            cameras = fixture.cameras,
        )

        assertTrue(quirks.any { it is XiaomiBurstTimestampJitterQuirk })
    }

    @Test
    fun `mediatek device triggers stride and low-light denoise balancer quirks`() {
        val fixture = CameraCapabilityFixtures.createMidRangeProfile()
        val quirks = registry.getApplicableQuirks(
            manufacturer = "OnePlus",
            model = "Nord 3",
            hardware = "mt6983",
            apiLevel = 33,
            cameras = fixture.cameras,
        )

        assertTrue(quirks.any { it is MediaTekYuvStrideQuirk })
        assertTrue(quirks.any { it is MediaTekLowLightDenoiseQuirk })
    }

    @Test
    fun `low memory device triggers low-ram burst depth throttling quirk`() {
        val fixture = CameraCapabilityFixtures.createEntryLevelProfile()
        val quirks = registry.getApplicableQuirks(
            manufacturer = "Generic",
            model = "BudgetPhone",
            totalRamGb = 3,
            cameras = fixture.cameras,
        )

        assertTrue(quirks.any { it is LowMemoryBurstDepthQuirk })
    }

    @Test
    fun `foldable device triggers foldable surface reattach quirk`() {
        val fixture = CameraCapabilityFixtures.createFlagshipProfile()
        val quirks = registry.getApplicableQuirks(
            manufacturer = "Samsung",
            model = "Galaxy Z Fold 5",
            cameras = fixture.cameras,
        )

        assertTrue(quirks.any { it is FoldableSurfaceReattachQuirk })
    }

    @Test
    fun `remote config dynamically suppresses fixed quirk and injects emergency quirk`() {
        val fakeRepo = com.webappypie.optilens.core.common.config.LocalRemoteConfigRepository()
        val overrideJson = """
            {
              "rules": [
                {
                  "deviceModelPattern": "Pixel 7 Pro",
                  "disabledQuirkIds": ["pixel_ae_convergence_quirk"],
                  "additionalQuirkIds": ["emergency_driver_patch_quirk"]
                }
              ]
            }
        """.trimIndent()
        fakeRepo.updateFlags(com.webappypie.optilens.core.common.feature.CustomFeatureFlags(deviceSpecificOverridesJson = overrideJson))

        val remoteAwareRegistry = DeviceQuirkRegistry(remoteConfigRepository = fakeRepo)
        val quirks = remoteAwareRegistry.getApplicableQuirks(
            manufacturer = "Google",
            model = "Pixel 7 Pro",
            device = "cheetah",
            apiLevel = 34,
            cameras = emptyList(),
        )

        // pixel_ae_convergence_quirk should have been suppressed by remote config
        assertFalse(quirks.any { it is PixelAeConvergenceQuirk })
        // emergency_driver_patch_quirk should have been dynamically injected
        assertTrue(quirks.any { it.id == "emergency_driver_patch_quirk" })
    }
}
