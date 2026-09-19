package com.webappypie.optilens.core.camera.discovery

import android.app.ActivityManager
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.params.DynamicRangeProfiles
import android.hardware.camera2.params.StreamConfigurationMap
import android.os.Build
import android.util.Size
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import com.webappypie.optilens.core.camera.model.CameraCapabilityProfile
import com.webappypie.optilens.core.camera.model.CameraDeviceProfile
import com.webappypie.optilens.core.camera.model.CameraHardwareLevel
import com.webappypie.optilens.core.camera.model.ControlCapabilities
import com.webappypie.optilens.core.camera.model.ExtensionSupport
import com.webappypie.optilens.core.camera.model.LensFacing
import com.webappypie.optilens.core.camera.model.OutputSize
import com.webappypie.optilens.core.camera.model.PhysicalSensorInfo
import com.webappypie.optilens.core.camera.model.PlatformRawCapabilities
import com.webappypie.optilens.core.camera.model.ResolutionInfo
import com.webappypie.optilens.core.camera.model.SensorArrayInfo
import com.webappypie.optilens.core.camera.model.StabilizationSupport
import com.webappypie.optilens.core.camera.model.StreamCapabilities
import com.webappypie.optilens.core.camera.quirks.DeviceQuirkRegistry
import com.webappypie.optilens.core.camera.tier.PerformanceTierEvaluator
import com.webappypie.optilens.core.common.coroutines.AppDispatchers
import com.webappypie.optilens.core.logging.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

interface CameraCapabilityDetector {
    suspend fun detectCapabilities(): CameraCapabilityProfile
}

@Singleton
class AndroidCameraCapabilityDetector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cameraManager: CameraManager,
    private val quirkRegistry: DeviceQuirkRegistry,
    private val tierEvaluator: PerformanceTierEvaluator,
    private val dispatchers: AppDispatchers,
    private val logger: AppLogger,
) : CameraCapabilityDetector {

    override suspend fun detectCapabilities(): CameraCapabilityProfile = withContext(dispatchers.io) {
        val startTime = System.currentTimeMillis()
        logger.d(TAG, "Starting comprehensive camera capability discovery...")

        val cameraIds = try {
            cameraManager.cameraIdList
        } catch (e: Exception) {
            logger.e(TAG, "Failed to retrieve camera IDs: ${e.message}", e)
            emptyArray()
        }

        val cameraProfiles = mutableListOf<CameraDeviceProfile>()
        var defaultBackId: String? = null
        var defaultFrontId: String? = null

        // Try initializing CameraX ExtensionManager for extension availability
        val extensionAvailabilityMap = queryCameraXExtensions()

        for (id in cameraIds) {
            try {
                val chars = cameraManager.getCameraCharacteristics(id)
                val profile = inspectCameraCharacteristics(id, chars, extensionAvailabilityMap[id])
                cameraProfiles.add(profile)

                if (profile.lensFacing == LensFacing.BACK && defaultBackId == null) {
                    defaultBackId = id
                } else if (profile.lensFacing == LensFacing.FRONT && defaultFrontId == null) {
                    defaultFrontId = id
                }
            } catch (e: Exception) {
                logger.w(TAG, "Failed to read characteristics for camera ID $id: ${e.message}")
            }
        }

        // Query system hardware metrics
        val totalRamGb = querySystemRamGb()
        val cpuCores = Runtime.getRuntime().availableProcessors()
        val apiLevel = Build.VERSION.SDK_INT

        // Evaluate performance tier heuristic
        val evaluation = tierEvaluator.evaluate(
            cameras = cameraProfiles,
            totalRamGb = totalRamGb,
            cpuCores = cpuCores,
            apiLevel = apiLevel,
        )

        // Evaluate device quirks
        val applicableQuirks = quirkRegistry.getApplicableQuirks(
            cameras = cameraProfiles,
        ).map { it.name }

        val duration = System.currentTimeMillis() - startTime
        logger.i(TAG, "Camera capability discovery complete in ${duration}ms. Tier: ${evaluation.tier}, Cameras: ${cameraProfiles.size}")

        val summary = buildSummary(
            cameras = cameraProfiles,
            tier = evaluation.tier,
            score = evaluation.score,
            quirks = applicableQuirks,
            ramGb = totalRamGb,
            cpuCores = cpuCores,
        )

        CameraCapabilityProfile(
            cameras = cameraProfiles,
            defaultBackCameraId = defaultBackId,
            defaultFrontCameraId = defaultFrontId,
            performanceTier = evaluation.tier,
            performanceScore = evaluation.score,
            detectedQuirks = applicableQuirks,
            deviceFingerprint = Build.FINGERPRINT,
            deviceModel = Build.MODEL,
            deviceManufacturer = Build.MANUFACTURER,
            androidApiLevel = apiLevel,
            totalRamGb = totalRamGb,
            cpuCores = cpuCores,
            discoveryTimestampMs = System.currentTimeMillis(),
            summary = summary,
        )
    }

    private fun inspectCameraCharacteristics(
        id: String,
        chars: CameraCharacteristics,
        extensions: ExtensionSupport?,
    ): CameraDeviceProfile {
        // Lens Facing
        val facingInt = chars.get(CameraCharacteristics.LENS_FACING)
        val lensFacing = LensFacing.fromCamera2(facingInt)

        // Hardware Level
        val hwLevelInt = chars.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
        val hardwareLevel = CameraHardwareLevel.fromCamera2(hwLevelInt)

        // Focal Lengths
        val focalLengths = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.toList() ?: emptyList()

        // Sensor Array Info
        val activeArray = chars.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
        val pixelArray = chars.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
        val physicalSize = chars.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
        val orientation = chars.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
        val timestampSource = when (chars.get(CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE)) {
            1 -> "REALTIME"
            else -> "UNKNOWN"
        }
        val sensorInfo = SensorArrayInfo(
            activeArrayWidth = activeArray?.width() ?: 0,
            activeArrayHeight = activeArray?.height() ?: 0,
            pixelArrayWidth = pixelArray?.width ?: 0,
            pixelArrayHeight = pixelArray?.height ?: 0,
            physicalWidthMm = physicalSize?.width ?: 0f,
            physicalHeightMm = physicalSize?.height ?: 0f,
            orientationDegrees = orientation,
            timestampSource = timestampSource,
        )

        // Stream Configuration Map & Resolutions
        val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val resolutions = extractResolutions(map)

        // Zoom Range
        val minZoom: Float
        val maxZoom: Float
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val range = chars.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)
            if (range != null) {
                minZoom = range.lower
                maxZoom = range.upper
            } else {
                minZoom = 1.0f
                maxZoom = chars.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1.0f
            }
        } else {
            minZoom = 1.0f
            maxZoom = chars.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1.0f
        }

        // Flash
        val hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false

        // Controls
        val afModes = chars.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)
            ?.map { afModeToString(it) } ?: emptyList()
        val aeModes = chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES)
            ?.map { aeModeToString(it) } ?: emptyList()
        val awbModes = chars.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES)
            ?.map { awbModeToString(it) } ?: emptyList()
        val aeCompRange = chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)
        val aeCompStep = chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP)

        val controls = ControlCapabilities(
            afModes = afModes,
            aeModes = aeModes,
            awbModes = awbModes,
            aeCompensationMin = aeCompRange?.lower ?: 0,
            aeCompensationMax = aeCompRange?.upper ?: 0,
            aeCompensationStep = aeCompStep?.toFloat() ?: 0f,
            minZoom = minZoom,
            maxZoom = maxZoom,
            hasFlash = hasFlash,
        )

        // Stream Capabilities & Capabilities Array
        val capsArray = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()
        val supportsRaw = capsArray.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)
        val supportsBurst = capsArray.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BURST_CAPTURE)
        val supportsYuvReprocess = capsArray.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_YUV_REPROCESSING)
        val supportsPrivReprocess = capsArray.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_PRIVATE_REPROCESSING)
        val supportsManualSensor = capsArray.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR)
        val supportsManualPostProc = capsArray.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING)
        val isLogicalMultiCam = capsArray.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA)

        // API 31+ Ultra-High-Resolution capability
        val supportsUltraHighRes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            capsArray.contains(16) // REQUEST_AVAILABLE_CAPABILITIES_ULTRA_HIGH_RESOLUTION_SENSOR
        } else false

        // API 33+ 10-bit Dynamic Range capability
        val supportsTenBitHdr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            capsArray.contains(18) // REQUEST_AVAILABLE_CAPABILITIES_DYNAMIC_RANGE_TEN_BIT
        } else false

        val dynamicRangeProfiles = extractDynamicRangeProfiles(chars)

        val isoRange = chars.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
        val expTimeRange = chars.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
        val maxFrameDuration = chars.get(CameraCharacteristics.SENSOR_INFO_MAX_FRAME_DURATION)

        val streamCapabilities = StreamCapabilities(
            supportsRaw = supportsRaw,
            supportsBurstCapture = supportsBurst,
            supportsYuvReprocessing = supportsYuvReprocess,
            supportsPrivateReprocessing = supportsPrivReprocess,
            supportsManualSensor = supportsManualSensor,
            supportsManualPostProcessing = supportsManualPostProc,
            isLogicalMultiCamera = isLogicalMultiCam,
            supportsUltraHighResolution = supportsUltraHighRes,
            supportsTenBitHdr = supportsTenBitHdr,
            dynamicRangeProfiles = dynamicRangeProfiles,
            isoRangeMin = isoRange?.lower,
            isoRangeMax = isoRange?.upper,
            exposureTimeRangeMinNs = expTimeRange?.lower,
            exposureTimeRangeMaxNs = expTimeRange?.upper,
            maxFrameDurationNs = maxFrameDuration,
        )

        // Stabilization
        val oisModes = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION) ?: intArrayOf()
        val hasOis = oisModes.contains(CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON)
        val videoStabModes = chars.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES) ?: intArrayOf()
        val hasEis = videoStabModes.contains(CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_ON)
        val hasPreviewStab = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            videoStabModes.contains(2) // CONTROL_VIDEO_STABILIZATION_MODE_PREVIEW_STABILIZATION
        } else false

        val stabilization = StabilizationSupport(
            opticalImageStabilization = hasOis,
            electronicVideoStabilization = hasEis,
            previewStabilization = hasPreviewStab,
        )

        // RAW Modes
        val rawCapabilities = extractRawCapabilities(map, supportsRaw, supportsUltraHighRes)

        // Low-light Boost (Android 15+ API 35)
        val hasLowLightBoost = if (Build.VERSION.SDK_INT >= 35) {
            chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES)?.contains(6) == true // CONTROL_AE_MODE_ON_LOW_LIGHT_BOOST_OR_CONTINUOUS
        } else false

        val extensionSupport = extensions?.copy(lowLightBoost = hasLowLightBoost)
            ?: ExtensionSupport(lowLightBoost = hasLowLightBoost)

        // Physical camera enumeration (API 28+)
        val physicalIds = chars.physicalCameraIds.toList()
        val physicalSensors = mutableListOf<PhysicalSensorInfo>()
        for (physId in physicalIds) {
            try {
                val physChars = cameraManager.getCameraCharacteristics(physId)
                val physFocal = physChars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull() ?: 0f
                val physArray = physChars.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
                val physSize = physChars.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
                val physFacing = LensFacing.fromCamera2(physChars.get(CameraCharacteristics.LENS_FACING))

                physicalSensors.add(
                    PhysicalSensorInfo(
                        id = physId,
                        focalLengthMm = physFocal,
                        sensorPhysicalWidthMm = physSize?.width ?: 0f,
                        sensorPhysicalHeightMm = physSize?.height ?: 0f,
                        activeArrayWidth = physArray?.width() ?: 0,
                        activeArrayHeight = physArray?.height() ?: 0,
                        lensFacing = physFacing,
                    )
                )
            } catch (e: Exception) {
                logger.w(TAG, "Failed inspecting physical camera $physId: ${e.message}")
            }
        }

        return CameraDeviceProfile(
            id = id,
            lensFacing = lensFacing,
            hardwareLevel = hardwareLevel,
            focalLengthsMm = focalLengths,
            sensorInfo = sensorInfo,
            resolutions = resolutions,
            minZoom = minZoom,
            maxZoom = maxZoom,
            controls = controls,
            streamCapabilities = streamCapabilities,
            extensions = extensionSupport,
            stabilization = stabilization,
            rawCapabilities = rawCapabilities,
            physicalCameraIds = physicalIds,
            physicalSensors = physicalSensors,
        )
    }

    private fun extractResolutions(map: StreamConfigurationMap?): ResolutionInfo {
        if (map == null) return ResolutionInfo()

        fun mapSizes(format: Int): List<OutputSize> {
            return map.getOutputSizes(format)
                ?.map { OutputSize(it.width, it.height) }
                ?.sortedByDescending { it.width * it.height }
                ?: emptyList()
        }

        val jpegSizes = mapSizes(ImageFormat.JPEG)
        val rawSizes = mapSizes(ImageFormat.RAW_SENSOR)
        val yuvSizes = mapSizes(ImageFormat.YUV_420_888)
        val privSizes = mapSizes(ImageFormat.PRIVATE)

        val ultraHighResSizes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            map.getHighResolutionOutputSizes(ImageFormat.JPEG)
                ?.map { OutputSize(it.width, it.height) }
                ?.sortedByDescending { it.width * it.height }
                ?: emptyList()
        } else emptyList()

        return ResolutionInfo(
            maxJpegSize = jpegSizes.firstOrNull(),
            maxRawSize = rawSizes.firstOrNull(),
            maxYuvSize = yuvSizes.firstOrNull(),
            maxPrivateSize = privSizes.firstOrNull(),
            supportedJpegSizes = jpegSizes,
            supportedRawSizes = rawSizes,
            supportedYuvSizes = yuvSizes,
            ultraHighResolutionSizes = ultraHighResSizes,
        )
    }

    private fun extractRawCapabilities(
        map: StreamConfigurationMap?,
        supportsRaw: Boolean,
        supportsUltraHighRes: Boolean,
    ): PlatformRawCapabilities {
        if (map == null || !supportsRaw) {
            return PlatformRawCapabilities()
        }

        fun hasFormat(format: Int) = map.outputFormats?.contains(format) == true

        val hasRawSensor = hasFormat(ImageFormat.RAW_SENSOR)
        val hasRaw10 = hasFormat(ImageFormat.RAW10)
        val hasRaw12 = hasFormat(ImageFormat.RAW12)
        val hasRawPrivate = hasFormat(ImageFormat.RAW_PRIVATE)

        val rawSizes = map.getOutputSizes(ImageFormat.RAW_SENSOR)
            ?.map { OutputSize(it.width, it.height) }
            ?.sortedByDescending { it.width * it.height }

        return PlatformRawCapabilities(
            supportsRawSensor = hasRawSensor,
            supportsRaw10 = hasRaw10,
            supportsRaw12 = hasRaw12,
            supportsRawPrivate = hasRawPrivate,
            supportsUltraHighResRaw = supportsUltraHighRes && hasRawSensor,
            maxRawResolution = rawSizes?.firstOrNull(),
        )
    }

    private fun extractDynamicRangeProfiles(chars: CameraCharacteristics): List<String> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return emptyList()

        val profilesObj = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_DYNAMIC_RANGE_PROFILES) ?: return emptyList()
        val supportedList = mutableListOf<String>()

        val profiles = profilesObj.supportedProfiles
        if (profiles.contains(DynamicRangeProfiles.HLG10)) supportedList.add("HLG10")
        if (profiles.contains(DynamicRangeProfiles.HDR10)) supportedList.add("HDR10")
        if (profiles.contains(DynamicRangeProfiles.HDR10_PLUS)) supportedList.add("HDR10_PLUS")
        if (profiles.contains(DynamicRangeProfiles.DOLBY_VISION_10B_HDR_OEM)) supportedList.add("DOLBY_VISION_OEM")
        if (profiles.contains(DynamicRangeProfiles.DOLBY_VISION_10B_HDR_OEM_PO)) supportedList.add("DOLBY_VISION_OEM_PO")
        if (profiles.contains(DynamicRangeProfiles.DOLBY_VISION_8B_HDR_OEM)) supportedList.add("DOLBY_VISION_8B")

        return supportedList
    }

    private suspend fun queryCameraXExtensions(): Map<String, ExtensionSupport> {
        val result = mutableMapOf<String, ExtensionSupport>()
        try {
            val cameraProvider = ProcessCameraProvider.getInstance(context).get(3, TimeUnit.SECONDS)
            val extensionsManager = ExtensionsManager.getInstanceAsync(context, cameraProvider).get(3, TimeUnit.SECONDS)

            for (selector in listOf(CameraSelector.DEFAULT_BACK_CAMERA, CameraSelector.DEFAULT_FRONT_CAMERA)) {
                try {
                    val cameraInfo = selector.filter(cameraProvider.availableCameraInfos).firstOrNull() ?: continue
                    val camera2Info = Camera2CameraInfo.from(cameraInfo)
                    val id = camera2Info.cameraId

                    val bokeh = extensionsManager.isExtensionAvailable(selector, ExtensionMode.BOKEH)
                    val hdr = extensionsManager.isExtensionAvailable(selector, ExtensionMode.HDR)
                    val night = extensionsManager.isExtensionAvailable(selector, ExtensionMode.NIGHT)
                    val faceRetouch = extensionsManager.isExtensionAvailable(selector, ExtensionMode.FACE_RETOUCH)
                    val auto = extensionsManager.isExtensionAvailable(selector, ExtensionMode.AUTO)

                    result[id] = ExtensionSupport(
                        bokeh = bokeh,
                        hdr = hdr,
                        night = night,
                        faceRetouch = faceRetouch,
                        auto = auto,
                    )
                } catch (e: Exception) {
                    logger.w(TAG, "Selector extension check skipped: ${e.message}")
                }
            }
        } catch (e: Exception) {
            logger.w(TAG, "CameraX ExtensionsManager unavailable: ${e.message}")
        }
        return result
    }

    private fun querySystemRamGb(): Float {
        return try {
            val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            actManager.getMemoryInfo(memInfo)
            memInfo.totalMem / (1024f * 1024f * 1024f)
        } catch (e: Exception) {
            4.0f // Fallback conservative estimate
        }
    }

    private fun buildSummary(
        cameras: List<CameraDeviceProfile>,
        tier: com.webappypie.optilens.core.camera.model.PerformanceTier,
        score: Int,
        quirks: List<String>,
        ramGb: Float,
        cpuCores: Int,
    ): String = buildString {
        appendLine("=== OptiLens Camera Capability Discovery Report ===")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
        appendLine("Platform: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("System: ${String.format("%.1f", ramGb)} GB RAM | $cpuCores Cores")
        appendLine("Performance Tier: $tier (Score: $score/100)")
        appendLine("Cameras Discovered: ${cameras.size}")
        if (quirks.isNotEmpty()) {
            appendLine("Active Quirks: ${quirks.joinToString(", ")}")
        } else {
            appendLine("Active Quirks: None (Standard HAL)")
        }
        appendLine()
        for (cam in cameras) {
            appendLine("Camera [${cam.id}] Facing: ${cam.lensFacing} | HW Level: ${cam.hardwareLevel}")
            appendLine("  Max JPEG: ${cam.resolutions.maxJpegSize ?: "None"}")
            appendLine("  Max RAW: ${cam.resolutions.maxRawSize ?: "Not supported"}")
            appendLine("  Zoom Range: ${cam.minZoom}x - ${cam.maxZoom}x")
            appendLine("  OIS: ${cam.stabilization.opticalImageStabilization} | EIS: ${cam.stabilization.electronicVideoStabilization}")
            appendLine("  Multi-Camera: ${cam.streamCapabilities.isLogicalMultiCamera} (Sub-sensors: ${cam.physicalCameraIds.size})")
        }
    }

    private fun afModeToString(mode: Int): String = when (mode) {
        0 -> "OFF"
        1 -> "AUTO"
        2 -> "MACRO"
        3 -> "CONTINUOUS_VIDEO"
        4 -> "CONTINUOUS_PICTURE"
        5 -> "EDOF"
        else -> "MODE_$mode"
    }

    private fun aeModeToString(mode: Int): String = when (mode) {
        0 -> "OFF"
        1 -> "ON"
        2 -> "ON_AUTO_FLASH"
        3 -> "ON_ALWAYS_FLASH"
        4 -> "ON_AUTO_FLASH_REDEYE"
        5 -> "ON_EXTERNAL_FLASH"
        6 -> "ON_LOW_LIGHT_BOOST"
        else -> "MODE_$mode"
    }

    private fun awbModeToString(mode: Int): String = when (mode) {
        0 -> "OFF"
        1 -> "AUTO"
        2 -> "INCANDESCENT"
        3 -> "FLUORESCENT"
        4 -> "WARM_FLUORESCENT"
        5 -> "DAYLIGHT"
        6 -> "CLOUDY_DAYLIGHT"
        7 -> "TWILIGHT"
        8 -> "SHADE"
        else -> "MODE_$mode"
    }

    companion object {
        private const val TAG = "CameraCapabilityDetector"
    }
}
