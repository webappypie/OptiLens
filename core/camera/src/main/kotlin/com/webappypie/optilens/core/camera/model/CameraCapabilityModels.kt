package com.webappypie.optilens.core.camera.model

import kotlinx.serialization.Serializable

/**
 * Camera2 hardware level reported by INFO_SUPPORTED_HARDWARE_LEVEL.
 */
@Serializable
enum class CameraHardwareLevel {
    LEGACY,
    LIMITED,
    FULL,
    LEVEL_3,
    EXTERNAL,
    UNKNOWN;

    companion object {
        fun fromCamera2(level: Int?): CameraHardwareLevel = when (level) {
            2 -> LEGACY
            0 -> LIMITED
            1 -> FULL
            3 -> LEVEL_3
            4 -> EXTERNAL
            else -> UNKNOWN
        }
    }
}

/**
 * Lens facing orientation relative to the screen.
 */
@Serializable
enum class LensFacing {
    BACK,
    FRONT,
    EXTERNAL;

    companion object {
        fun fromCamera2(facing: Int?): LensFacing = when (facing) {
            1 -> BACK
            0 -> FRONT
            2 -> EXTERNAL
            else -> BACK
        }
    }
}

/**
 * Dimensions of a sensor output stream.
 */
@Serializable
data class OutputSize(
    val width: Int,
    val height: Int,
) {
    val megapixels: Float get() = (width.toLong() * height.toLong()) / 1_000_000f
    override fun toString(): String = "${width}x${height} (${String.format("%.1f", megapixels)} MP)"
}

/**
 * Sensor physical dimensions and active pixel array characteristics.
 */
@Serializable
data class SensorArrayInfo(
    val activeArrayWidth: Int = 0,
    val activeArrayHeight: Int = 0,
    val pixelArrayWidth: Int = 0,
    val pixelArrayHeight: Int = 0,
    val physicalWidthMm: Float = 0f,
    val physicalHeightMm: Float = 0f,
    val orientationDegrees: Int = 0,
    val timestampSource: String = "UNKNOWN",
)

/**
 * Output resolution profiles across stream formats.
 */
@Serializable
data class ResolutionInfo(
    val maxJpegSize: OutputSize? = null,
    val maxRawSize: OutputSize? = null,
    val maxYuvSize: OutputSize? = null,
    val maxPrivateSize: OutputSize? = null,
    val supportedJpegSizes: List<OutputSize> = emptyList(),
    val supportedRawSizes: List<OutputSize> = emptyList(),
    val supportedYuvSizes: List<OutputSize> = emptyList(),
    val ultraHighResolutionSizes: List<OutputSize> = emptyList(),
)

/**
 * Camera controls (AF, AE, AWB, exposure compensation, zoom, flash).
 */
@Serializable
data class ControlCapabilities(
    val afModes: List<String> = emptyList(),
    val aeModes: List<String> = emptyList(),
    val awbModes: List<String> = emptyList(),
    val aeCompensationMin: Int = 0,
    val aeCompensationMax: Int = 0,
    val aeCompensationStep: Float = 0f,
    val minZoom: Float = 1.0f,
    val maxZoom: Float = 1.0f,
    val hasFlash: Boolean = false,
    val minFocusDistanceDiopters: Float = 0f,
    val apertures: List<Float> = emptyList(),
)

/**
 * Capabilities for stream capture, RAW modes, reprocessing, and manual sensors.
 */
@Serializable
data class StreamCapabilities(
    val supportsRaw: Boolean = false,
    val supportsBurstCapture: Boolean = false,
    val supportsYuvReprocessing: Boolean = false,
    val supportsPrivateReprocessing: Boolean = false,
    val supportsManualSensor: Boolean = false,
    val supportsManualPostProcessing: Boolean = false,
    val isLogicalMultiCamera: Boolean = false,
    val supportsUltraHighResolution: Boolean = false,
    val supportsTenBitHdr: Boolean = false,
    val dynamicRangeProfiles: List<String> = emptyList(),
    val isoRangeMin: Int? = null,
    val isoRangeMax: Int? = null,
    val exposureTimeRangeMinNs: Long? = null,
    val exposureTimeRangeMaxNs: Long? = null,
    val maxFrameDurationNs: Long? = null,
)

/**
 * Support for CameraX extensions and hardware-level low light boosts.
 */
@Serializable
data class ExtensionSupport(
    val bokeh: Boolean = false,
    val hdr: Boolean = false,
    val night: Boolean = false,
    val faceRetouch: Boolean = false,
    val auto: Boolean = false,
    val lowLightBoost: Boolean = false,
)

/**
 * Stabilization capabilities (optical and electronic).
 */
@Serializable
data class StabilizationSupport(
    val opticalImageStabilization: Boolean = false,
    val electronicVideoStabilization: Boolean = false,
    val previewStabilization: Boolean = false,
)

/**
 * Detailed RAW format and ultra-high-resolution support.
 */
@Serializable
data class PlatformRawCapabilities(
    val supportsRawSensor: Boolean = false,
    val supportsRaw10: Boolean = false,
    val supportsRaw12: Boolean = false,
    val supportsRawPrivate: Boolean = false,
    val supportsUltraHighResRaw: Boolean = false,
    val maxRawResolution: OutputSize? = null,
)

/**
 * Physical sub-sensor characteristics for logical multi-camera arrays.
 */
@Serializable
data class PhysicalSensorInfo(
    val id: String,
    val focalLengthMm: Float = 0f,
    val sensorPhysicalWidthMm: Float = 0f,
    val sensorPhysicalHeightMm: Float = 0f,
    val activeArrayWidth: Int = 0,
    val activeArrayHeight: Int = 0,
    val lensFacing: LensFacing = LensFacing.BACK,
)

/**
 * Complete profile for a single camera (logical or standalone).
 */
@Serializable
data class CameraDeviceProfile(
    val id: String,
    val lensFacing: LensFacing,
    val hardwareLevel: CameraHardwareLevel,
    val focalLengthsMm: List<Float> = emptyList(),
    val sensorInfo: SensorArrayInfo = SensorArrayInfo(),
    val resolutions: ResolutionInfo = ResolutionInfo(),
    val minZoom: Float = 1.0f,
    val maxZoom: Float = 1.0f,
    val controls: ControlCapabilities = ControlCapabilities(),
    val streamCapabilities: StreamCapabilities = StreamCapabilities(),
    val extensions: ExtensionSupport = ExtensionSupport(),
    val stabilization: StabilizationSupport = StabilizationSupport(),
    val rawCapabilities: PlatformRawCapabilities = PlatformRawCapabilities(),
    val physicalCameraIds: List<String> = emptyList(),
    val physicalSensors: List<PhysicalSensorInfo> = emptyList(),
) {
    /**
     * Compute sensible user-facing zoom multipliers based on physical optics.
     */
    fun computeAvailableZoomRatios(): List<Float> {
        val ratios = mutableListOf<Float>()
        if (minZoom < 0.95f) {
            ratios.add(minZoom)
        }
        ratios.add(1.0f)
        if (maxZoom >= 2.0f) ratios.add(2.0f)
        if (maxZoom >= 3.0f && !ratios.contains(3.0f)) ratios.add(3.0f)
        if (maxZoom >= 5.0f && !ratios.contains(5.0f)) ratios.add(5.0f)
        if (maxZoom >= 10.0f && !ratios.contains(10.0f)) ratios.add(10.0f)
        return ratios.distinct().sorted()
    }
}

/**
 * Computational photography and device capability performance tier.
 */
@Serializable
enum class PerformanceTier {
    /** Premium multi-camera device (Level 3, RAW, Reprocessing, 10+ GB RAM, Flagship SoC). */
    FLAGSHIP,
    /** High-end device (Level 3 / Full, RAW, 6-8 GB RAM, modern multi-core SoC). */
    HIGH_PERFORMANCE,
    /** Mid-range device (Full / Limited, 4-6 GB RAM, standard capture pipelines). */
    MID_RANGE,
    /** Entry-level / legacy hardware (Limited / Legacy, < 4 GB RAM, basic single-frame capture). */
    ENTRY_LEVEL;
}

/**
 * Root device profile aggregating all detected cameras and platform capabilities.
 */
@Serializable
data class CameraCapabilityProfile(
    val cameras: List<CameraDeviceProfile> = emptyList(),
    val defaultBackCameraId: String? = null,
    val defaultFrontCameraId: String? = null,
    val performanceTier: PerformanceTier = PerformanceTier.MID_RANGE,
    val performanceScore: Int = 0,
    val detectedQuirks: List<String> = emptyList(),
    val deviceFingerprint: String = "",
    val deviceModel: String = "",
    val deviceManufacturer: String = "",
    val androidApiLevel: Int = 0,
    val totalRamGb: Float = 0f,
    val cpuCores: Int = 0,
    val discoveryTimestampMs: Long = 0L,
    val summary: String = "",
) {
    val primaryBackCamera: CameraDeviceProfile?
        get() = cameras.firstOrNull { it.id == defaultBackCameraId }
            ?: cameras.firstOrNull { it.lensFacing == LensFacing.BACK }

    val primaryFrontCamera: CameraDeviceProfile?
        get() = cameras.firstOrNull { it.id == defaultFrontCameraId }
            ?: cameras.firstOrNull { it.lensFacing == LensFacing.FRONT }
}
