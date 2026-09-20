package com.webappypie.optilens.core.camera.quirks

import android.os.Build
import com.webappypie.optilens.core.camera.model.CameraDeviceProfile
import com.webappypie.optilens.core.camera.model.CameraHardwareLevel
import com.webappypie.optilens.core.camera.model.LensFacing
import com.webappypie.optilens.core.common.config.RemoteConfigRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Interface representing a hardware or driver quirk requiring custom handling.
 *
 * NOTE (Gate Compliance): Quirks represent vendor/HAL driver bugs or edge cases.
 * Camera capabilities (RAW, OIS, Manual Sensor, etc.) are ALWAYS queried directly
 * from Android hardware characteristics and NEVER inferred from device model.
 */
interface DeviceQuirk {
    val id: String
    val name: String
    val description: String
}

/**
 * Workaround for Samsung devices that stretch 4:3 preview frames when rendered in 16:9 containers.
 */
data object SamsungPreviewAspectQuirk : DeviceQuirk {
    override val id = "samsung_preview_aspect_quirk"
    override val name = "Samsung Preview Aspect Correction"
    override val description = "Forces 4:3 viewfinder aspect ratio container to prevent sensor distortion."
}

/**
 * Workaround for Samsung multi-camera HALs requiring optical settling when switching physical lenses.
 */
data object SamsungLensSwitchLagQuirk : DeviceQuirk {
    override val id = "samsung_lens_switch_lag_quirk"
    override val name = "Samsung Multi-Camera Lens Transition Settling"
    override val description = "Inserts brief frame buffer discard when switching between wide and telephoto physical cameras."
}

/**
 * Workaround for Samsung ISOCELL sensors requiring dynamic black-level pedestal compensation.
 */
data object SamsungBlackLevelOffsetQuirk : DeviceQuirk {
    override val id = "samsung_black_level_offset_quirk"
    override val name = "Samsung ISOCELL Dynamic Black Level Pedestal"
    override val description = "Compensates for sensor black-level shift on Samsung ISOCELL sensors during RAW captures."
}

/**
 * Workaround for Google Tensor/Pixel camera HAL AE flicker during rapid focus transitions.
 */
data object PixelAeConvergenceQuirk : DeviceQuirk {
    override val id = "pixel_ae_convergence_quirk"
    override val name = "Pixel AE Convergence Stabilizer"
    override val description = "Inserts convergence settling frames before triggering still capture."
}

/**
 * Workaround for Pixel 6/7/8/9 optical image stabilization voice coil motor settling.
 */
data object PixelOisSettlingQuirk : DeviceQuirk {
    override val id = "pixel_ois_settling_quirk"
    override val name = "Pixel OIS Actuator Settling Stabilizer"
    override val description = "Allows optical image stabilization actuator to settle following rapid recomposition."
}

/**
 * Workaround for MediaTek camera HALs with non-standard YUV row stride padding.
 */
data object MediaTekYuvStrideQuirk : DeviceQuirk {
    override val id = "mediatek_yuv_stride_quirk"
    override val name = "MediaTek YUV Row Stride Alignment"
    override val description = "Enforces row-stride byte buffer alignment during YUV_420_888 image analysis."
}

/**
 * Workaround for MediaTek ISP low-light spatial noise over-smoothing.
 */
data object MediaTekLowLightDenoiseQuirk : DeviceQuirk {
    override val id = "mediatek_low_light_denoise_quirk"
    override val name = "MediaTek ISP Low-Light Denoise Balancer"
    override val description = "Clamps spatial bilateral filter radii during multi-frame night alignment to prevent texture loss."
}

/**
 * Workaround for Xiaomi/Redmi/POCO Camera2 HAL frame timestamp jitter in continuous burst.
 */
data object XiaomiBurstTimestampJitterQuirk : DeviceQuirk {
    override val id = "xiaomi_burst_timestamp_jitter_quirk"
    override val name = "Xiaomi Camera2 HAL Burst Timestamp Sync"
    override val description = "Re-aligns monotonic frame timestamps during high-speed burst on MIUI/HyperOS Camera2 HAL."
}

/**
 * Workaround for Xiaomi 108MP/200MP quad/nona-bayer hardware remosaic watchdog timeouts.
 */
data object XiaomiHighMegapixelRemosaicQuirk : DeviceQuirk {
    override val id = "xiaomi_high_mp_remosaic_quirk"
    override val name = "Xiaomi High-Megapixel Remosaic Watchdog"
    override val description = "Extends still capture timeout watchdog to prevent HAL resets during ultra-high-res remosaic."
}

/**
 * Workaround for LIMITED/LEGACY devices that cannot sustain concurrent high-res capture and analysis.
 */
data object LimitedHardwareStreamConstraintQuirk : DeviceQuirk {
    override val id = "limited_stream_constraint_quirk"
    override val name = "Limited Hardware Stream Throttling"
    override val description = "Downsamples real-time analysis stream to 720p to prevent pipeline stall on LIMITED devices."
}

/**
 * Workaround for devices with low RAM (<= 4GB) or LIMITED hardware to prevent out-of-memory kills.
 */
data object LowMemoryBurstDepthQuirk : DeviceQuirk {
    override val id = "low_memory_burst_depth_quirk"
    override val name = "Low-RAM Burst Depth Throttling"
    override val description = "Caps continuous burst acquisition depth to 4 frames on low-memory configurations."
}

/**
 * Workaround for devices with high-megapixels (>= 48MP) sensors where capture readout exceeds 1.5s.
 */
data object HighResolutionCaptureLagQuirk : DeviceQuirk {
    override val id = "high_res_capture_lag_quirk"
    override val name = "High-Resolution Capture Latency Indicator"
    override val description = "Extends UI shutter lock feedback to avoid early motion blur on heavy sensor readouts."
}

/**
 * Workaround for front sensor orientation reporting anomaly on certain foldables and tablets.
 */
data object InvertedSensorOrientationQuirk : DeviceQuirk {
    override val id = "inverted_sensor_orientation_quirk"
    override val name = "Sensor Orientation Normalization"
    override val description = "Normalizes inverted front sensor rotation on non-standard form factors."
}

/**
 * Workaround for foldable dual-screen posture changes and display hinge transitions.
 */
data object FoldableSurfaceReattachQuirk : DeviceQuirk {
    override val id = "foldable_surface_reattach_quirk"
    override val name = "Foldable Dual-Display Surface Re-attachment"
    override val description = "Gracefully invalidates and re-binds preview surface upon display folding transitions."
}

/**
 * Dynamic placeholder for emergency quirks injected remotely via Remote Config.
 */
data class DynamicRemoteQuirk(
    override val id: String,
    override val name: String = "Remote Config Emergency Quirk ($id)",
    override val description: String = "Emergency quirk rule injected dynamically via Remote Config.",
) : DeviceQuirk

/**
 * Registry responsible for evaluating and returning active device quirks.
 *
 * All quirks are added strictly through this central registry.
 * Remote Config emergency overrides can dynamically inject or suppress quirks per device model.
 */
@Singleton
class DeviceQuirkRegistry @Inject constructor(
    private val remoteConfigRepository: RemoteConfigRepository,
) {

    // Secondary constructor for testing or standalone usage without DI
    constructor() : this(com.webappypie.optilens.core.common.config.LocalRemoteConfigRepository())

    companion object {
        val ALL_KNOWN_QUIRKS: Map<String, DeviceQuirk> = listOf(
            SamsungPreviewAspectQuirk,
            SamsungLensSwitchLagQuirk,
            SamsungBlackLevelOffsetQuirk,
            PixelAeConvergenceQuirk,
            PixelOisSettlingQuirk,
            MediaTekYuvStrideQuirk,
            MediaTekLowLightDenoiseQuirk,
            XiaomiBurstTimestampJitterQuirk,
            XiaomiHighMegapixelRemosaicQuirk,
            LimitedHardwareStreamConstraintQuirk,
            LowMemoryBurstDepthQuirk,
            HighResolutionCaptureLagQuirk,
            InvertedSensorOrientationQuirk,
            FoldableSurfaceReattachQuirk,
        ).associateBy { it.id }
    }

    /**
     * Evaluates applicable quirks based on device characteristics, hardware levels, and platform environment.
     */
    fun getApplicableQuirks(
        manufacturer: String = Build.MANUFACTURER.orEmpty(),
        model: String = Build.MODEL.orEmpty(),
        device: String = Build.DEVICE.orEmpty(),
        apiLevel: Int = Build.VERSION.SDK_INT,
        totalRamGb: Int = 8,
        hardware: String = Build.HARDWARE.orEmpty(),
        cameras: List<CameraDeviceProfile> = emptyList(),
    ): List<DeviceQuirk> {
        val activeQuirks = mutableListOf<DeviceQuirk>()

        val mfgLower = manufacturer.lowercase()
        val modelLower = model.lowercase()
        val hwLower = hardware.lowercase()

        // 1. Samsung preview distortion workaround
        if (mfgLower.contains("samsung")) {
            activeQuirks.add(SamsungPreviewAspectQuirk)
        }

        // 2. Samsung multi-camera lens switch lag
        if (mfgLower.contains("samsung") && (modelLower.contains("ultra") || modelLower.contains("s2") || modelLower.contains("s23") || modelLower.contains("s24"))) {
            activeQuirks.add(SamsungLensSwitchLagQuirk)
        }

        // 3. Samsung ISOCELL black level offset
        if (mfgLower.contains("samsung") && cameras.any { it.rawCapabilities.supportsRawSensor }) {
            activeQuirks.add(SamsungBlackLevelOffsetQuirk)
        }

        // 4. Pixel AE settling workaround (Pixel 6 / 7 / 8 / 9 generations)
        if (mfgLower.contains("google") && (modelLower.contains("pixel 6") || modelLower.contains("pixel 7") || modelLower.contains("pixel 8") || modelLower.contains("pixel 9"))) {
            activeQuirks.add(PixelAeConvergenceQuirk)
            activeQuirks.add(PixelOisSettlingQuirk)
        }

        // 5. MediaTek YUV stride & denoise quirks
        if (hwLower.contains("mt") || hwLower.contains("mediatek") || mfgLower.contains("mediatek")) {
            activeQuirks.add(MediaTekYuvStrideQuirk)
            activeQuirks.add(MediaTekLowLightDenoiseQuirk)
        }

        // 6. Xiaomi burst timestamp & high MP remosaic quirks
        if (mfgLower.contains("xiaomi") || mfgLower.contains("redmi") || mfgLower.contains("poco")) {
            activeQuirks.add(XiaomiBurstTimestampJitterQuirk)
            val hasUltraHighResXiaomi = cameras.any { camera ->
                val maxJpegMp = camera.resolutions.maxJpegSize?.megapixels ?: 0f
                maxJpegMp >= 100f || camera.streamCapabilities.supportsUltraHighResolution
            }
            if (hasUltraHighResXiaomi) {
                activeQuirks.add(XiaomiHighMegapixelRemosaicQuirk)
            }
        }

        // 7. LIMITED / LEGACY hardware stream constraint
        val hasLimitedOrLegacy = cameras.any {
            it.hardwareLevel == CameraHardwareLevel.LIMITED || it.hardwareLevel == CameraHardwareLevel.LEGACY
        }
        if (hasLimitedOrLegacy) {
            activeQuirks.add(LimitedHardwareStreamConstraintQuirk)
        }

        // 8. Low memory burst depth throttling (<= 4GB RAM or limited hardware)
        if (totalRamGb <= 4 || hasLimitedOrLegacy) {
            activeQuirks.add(LowMemoryBurstDepthQuirk)
        }

        // 9. High-resolution capture lag (>= 48 MP output capability)
        val hasUltraHighRes = cameras.any { camera ->
            val maxJpegMp = camera.resolutions.maxJpegSize?.megapixels ?: 0f
            maxJpegMp >= 48f || camera.streamCapabilities.supportsUltraHighResolution
        }
        if (hasUltraHighRes) {
            activeQuirks.add(HighResolutionCaptureLagQuirk)
        }

        // 10. Front camera orientation anomaly
        val hasFrontWithOddOrientation = cameras.any {
            it.lensFacing == LensFacing.FRONT && (it.sensorInfo.orientationDegrees == 180 || it.sensorInfo.orientationDegrees == 0)
        }
        if (hasFrontWithOddOrientation) {
            activeQuirks.add(InvertedSensorOrientationQuirk)
        }

        // 11. Foldable dual-display surface re-attach
        if (modelLower.contains("fold") || modelLower.contains("flip")) {
            activeQuirks.add(FoldableSurfaceReattachQuirk)
        }

        // Deduplicate locally detected quirks
        val quirkList = activeQuirks.distinctBy { it.id }.toMutableList()

        // 12. Apply Remote Config emergency additions and suppressions
        val baseIds = quirkList.map { it.id }
        val effectiveIds = remoteConfigRepository.getEffectiveQuirks(baseIds, model)

        // Remove quirks suppressed by Remote Config
        quirkList.retainAll { it.id in effectiveIds }

        // Add emergency quirks injected by Remote Config
        val existingIds = quirkList.map { it.id }.toSet()
        for (id in effectiveIds) {
            if (id !in existingIds) {
                val knownQuirk = ALL_KNOWN_QUIRKS[id] ?: DynamicRemoteQuirk(id)
                quirkList.add(knownQuirk)
            }
        }

        return quirkList
    }
}
