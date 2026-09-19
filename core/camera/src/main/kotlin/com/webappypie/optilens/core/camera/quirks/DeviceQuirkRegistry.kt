package com.webappypie.optilens.core.camera.quirks

import android.os.Build
import com.webappypie.optilens.core.camera.model.CameraDeviceProfile
import com.webappypie.optilens.core.camera.model.CameraHardwareLevel
import com.webappypie.optilens.core.camera.model.LensFacing
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
 * Workaround for Google Tensor/Pixel camera HAL AE flicker during rapid focus transitions.
 */
data object PixelAeConvergenceQuirk : DeviceQuirk {
    override val id = "pixel_ae_convergence_quirk"
    override val name = "Pixel AE Convergence Stabilizer"
    override val description = "Inserts convergence settling frames before triggering still capture."
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
 * Workaround for LIMITED/LEGACY devices that cannot sustain concurrent high-res capture and analysis.
 */
data object LimitedHardwareStreamConstraintQuirk : DeviceQuirk {
    override val id = "limited_stream_constraint_quirk"
    override val name = "Limited Hardware Stream Throttling"
    override val description = "Downsamples real-time analysis stream to 720p to prevent pipeline stall on LIMITED devices."
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
 * Registry responsible for evaluating and returning active device quirks.
 */
@Singleton
class DeviceQuirkRegistry @Inject constructor() {

    /**
     * Evaluates applicable quirks based on device characteristics, hardware levels, and platform environment.
     */
    fun getApplicableQuirks(
        manufacturer: String = Build.MANUFACTURER,
        model: String = Build.MODEL,
        device: String = Build.DEVICE,
        apiLevel: Int = Build.VERSION.SDK_INT,
        cameras: List<CameraDeviceProfile> = emptyList(),
    ): List<DeviceQuirk> {
        val activeQuirks = mutableListOf<DeviceQuirk>()

        val mfgLower = manufacturer.lowercase()
        val modelLower = model.lowercase()

        // 1. Samsung preview distortion workaround
        if (mfgLower.contains("samsung")) {
            activeQuirks.add(SamsungPreviewAspectQuirk)
        }

        // 2. Pixel AE settling workaround (Pixel 6 / 7 / 8 generations)
        if (mfgLower.contains("google") && (modelLower.contains("pixel 6") || modelLower.contains("pixel 7") || modelLower.contains("pixel 8"))) {
            activeQuirks.add(PixelAeConvergenceQuirk)
        }

        // 3. MediaTek YUV stride quirk
        val hardware = Build.HARDWARE?.lowercase() ?: ""
        if (hardware.contains("mt") || hardware.contains("mediatek")) {
            activeQuirks.add(MediaTekYuvStrideQuirk)
        }

        // 4. LIMITED / LEGACY hardware stream constraint (Derived directly from CameraCharacteristics)
        val hasLimitedOrLegacy = cameras.any {
            it.hardwareLevel == CameraHardwareLevel.LIMITED || it.hardwareLevel == CameraHardwareLevel.LEGACY
        }
        if (hasLimitedOrLegacy) {
            activeQuirks.add(LimitedHardwareStreamConstraintQuirk)
        }

        // 5. High-resolution capture lag (>= 48 MP output capability)
        val hasUltraHighRes = cameras.any { camera ->
            val maxJpegMp = camera.resolutions.maxJpegSize?.megapixels ?: 0f
            maxJpegMp >= 48f || camera.streamCapabilities.supportsUltraHighResolution
        }
        if (hasUltraHighRes) {
            activeQuirks.add(HighResolutionCaptureLagQuirk)
        }

        // 6. Front camera orientation anomaly
        val hasFrontWithOddOrientation = cameras.any {
            it.lensFacing == LensFacing.FRONT && (it.sensorInfo.orientationDegrees == 180 || it.sensorInfo.orientationDegrees == 0)
        }
        if (hasFrontWithOddOrientation) {
            activeQuirks.add(InvertedSensorOrientationQuirk)
        }

        return activeQuirks.distinctBy { it.id }
    }
}
