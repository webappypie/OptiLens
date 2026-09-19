package com.webappypie.optilens.core.camera.fixtures

import com.webappypie.optilens.core.camera.model.CameraCapabilityProfile
import com.webappypie.optilens.core.camera.model.CameraDeviceProfile
import com.webappypie.optilens.core.camera.model.CameraHardwareLevel
import com.webappypie.optilens.core.camera.model.ControlCapabilities
import com.webappypie.optilens.core.camera.model.ExtensionSupport
import com.webappypie.optilens.core.camera.model.LensFacing
import com.webappypie.optilens.core.camera.model.OutputSize
import com.webappypie.optilens.core.camera.model.PerformanceTier
import com.webappypie.optilens.core.camera.model.PhysicalSensorInfo
import com.webappypie.optilens.core.camera.model.PlatformRawCapabilities
import com.webappypie.optilens.core.camera.model.ResolutionInfo
import com.webappypie.optilens.core.camera.model.SensorArrayInfo
import com.webappypie.optilens.core.camera.model.StabilizationSupport
import com.webappypie.optilens.core.camera.model.StreamCapabilities

object CameraCapabilityFixtures {

    fun createFlagshipProfile(): CameraCapabilityProfile {
        val backMain = CameraDeviceProfile(
            id = "0",
            lensFacing = LensFacing.BACK,
            hardwareLevel = CameraHardwareLevel.LEVEL_3,
            focalLengthsMm = listOf(6.9f, 2.2f, 19.0f),
            sensorInfo = SensorArrayInfo(
                activeArrayWidth = 8192,
                activeArrayHeight = 6144,
                pixelArrayWidth = 8192,
                pixelArrayHeight = 6144,
                physicalWidthMm = 9.8f,
                physicalHeightMm = 7.3f,
                orientationDegrees = 90,
                timestampSource = "REALTIME",
            ),
            resolutions = ResolutionInfo(
                maxJpegSize = OutputSize(8192, 6144),
                maxRawSize = OutputSize(8192, 6144),
                maxYuvSize = OutputSize(4096, 3072),
                maxPrivateSize = OutputSize(4096, 3072),
                supportedJpegSizes = listOf(OutputSize(8192, 6144), OutputSize(4096, 3072), OutputSize(1920, 1080)),
                supportedRawSizes = listOf(OutputSize(8192, 6144)),
                ultraHighResolutionSizes = listOf(OutputSize(8192, 6144)),
            ),
            minZoom = 0.5f,
            maxZoom = 30.0f,
            controls = ControlCapabilities(
                afModes = listOf("AUTO", "CONTINUOUS_PICTURE", "MACRO"),
                aeModes = listOf("ON", "ON_AUTO_FLASH"),
                awbModes = listOf("AUTO", "DAYLIGHT"),
                aeCompensationMin = -12,
                aeCompensationMax = 12,
                aeCompensationStep = 0.333f,
                minZoom = 0.5f,
                maxZoom = 30.0f,
                hasFlash = true,
            ),
            streamCapabilities = StreamCapabilities(
                supportsRaw = true,
                supportsBurstCapture = true,
                supportsYuvReprocessing = true,
                supportsPrivateReprocessing = true,
                supportsManualSensor = true,
                supportsManualPostProcessing = true,
                isLogicalMultiCamera = true,
                supportsUltraHighResolution = true,
                supportsTenBitHdr = true,
                dynamicRangeProfiles = listOf("HLG10", "HDR10_PLUS"),
                isoRangeMin = 50,
                isoRangeMax = 12800,
                exposureTimeRangeMinNs = 10000L,
                exposureTimeRangeMaxNs = 30000000000L,
            ),
            extensions = ExtensionSupport(
                bokeh = true,
                hdr = true,
                night = true,
                faceRetouch = true,
                auto = true,
                lowLightBoost = true,
            ),
            stabilization = StabilizationSupport(
                opticalImageStabilization = true,
                electronicVideoStabilization = true,
                previewStabilization = true,
            ),
            rawCapabilities = PlatformRawCapabilities(
                supportsRawSensor = true,
                supportsRaw10 = true,
                supportsRaw12 = true,
                supportsUltraHighResRaw = true,
                maxRawResolution = OutputSize(8192, 6144),
            ),
            physicalCameraIds = listOf("2", "3"),
            physicalSensors = listOf(
                PhysicalSensorInfo(id = "2", focalLengthMm = 2.2f, lensFacing = LensFacing.BACK),
                PhysicalSensorInfo(id = "3", focalLengthMm = 19.0f, lensFacing = LensFacing.BACK),
            ),
        )

        val front = CameraDeviceProfile(
            id = "1",
            lensFacing = LensFacing.FRONT,
            hardwareLevel = CameraHardwareLevel.FULL,
            focalLengthsMm = listOf(2.8f),
            resolutions = ResolutionInfo(
                maxJpegSize = OutputSize(3840, 2160),
            ),
            controls = ControlCapabilities(minZoom = 1.0f, maxZoom = 2.0f),
            streamCapabilities = StreamCapabilities(supportsRaw = false),
        )

        return CameraCapabilityProfile(
            cameras = listOf(backMain, front),
            defaultBackCameraId = "0",
            defaultFrontCameraId = "1",
            performanceTier = PerformanceTier.FLAGSHIP,
            performanceScore = 95,
            detectedQuirks = emptyList(),
            deviceFingerprint = "google/cheetah/cheetah:14/UP1A.231005.007/10754064:user/release-keys",
            deviceModel = "Pixel 7 Pro",
            deviceManufacturer = "Google",
            androidApiLevel = 34,
            totalRamGb = 11.5f,
            cpuCores = 8,
            discoveryTimestampMs = 1700000000000L,
            summary = "Flagship Test Device",
        )
    }

    fun createMidRangeProfile(): CameraCapabilityProfile {
        val backMain = CameraDeviceProfile(
            id = "0",
            lensFacing = LensFacing.BACK,
            hardwareLevel = CameraHardwareLevel.FULL,
            focalLengthsMm = listOf(4.5f),
            resolutions = ResolutionInfo(
                maxJpegSize = OutputSize(4000, 3000),
                maxRawSize = OutputSize(4000, 3000),
            ),
            minZoom = 1.0f,
            maxZoom = 10.0f,
            controls = ControlCapabilities(hasFlash = true),
            streamCapabilities = StreamCapabilities(
                supportsRaw = true,
                supportsBurstCapture = true,
                supportsYuvReprocessing = false,
                supportsPrivateReprocessing = false,
                supportsManualSensor = true,
                isLogicalMultiCamera = false,
            ),
            stabilization = StabilizationSupport(opticalImageStabilization = true),
        )

        return CameraCapabilityProfile(
            cameras = listOf(backMain),
            defaultBackCameraId = "0",
            performanceTier = PerformanceTier.MID_RANGE,
            performanceScore = 55,
            deviceModel = "Galaxy A54",
            deviceManufacturer = "Samsung",
            androidApiLevel = 33,
            totalRamGb = 5.8f,
            cpuCores = 8,
            discoveryTimestampMs = 1700000000000L,
        )
    }

    fun createEntryLevelProfile(): CameraCapabilityProfile {
        val backMain = CameraDeviceProfile(
            id = "0",
            lensFacing = LensFacing.BACK,
            hardwareLevel = CameraHardwareLevel.LIMITED,
            focalLengthsMm = listOf(3.5f),
            resolutions = ResolutionInfo(
                maxJpegSize = OutputSize(2560, 1440),
            ),
            minZoom = 1.0f,
            maxZoom = 4.0f,
            controls = ControlCapabilities(hasFlash = false),
            streamCapabilities = StreamCapabilities(
                supportsRaw = false,
                supportsBurstCapture = false,
            ),
        )

        return CameraCapabilityProfile(
            cameras = listOf(backMain),
            defaultBackCameraId = "0",
            performanceTier = PerformanceTier.ENTRY_LEVEL,
            performanceScore = 25,
            deviceModel = "Redmi Go",
            deviceManufacturer = "Xiaomi",
            androidApiLevel = 29,
            totalRamGb = 2.5f,
            cpuCores = 4,
            discoveryTimestampMs = 1700000000000L,
        )
    }

    fun createLegacyProfile(): CameraCapabilityProfile {
        val backMain = CameraDeviceProfile(
            id = "0",
            lensFacing = LensFacing.BACK,
            hardwareLevel = CameraHardwareLevel.LEGACY,
            resolutions = ResolutionInfo(
                maxJpegSize = OutputSize(1920, 1080),
            ),
        )

        return CameraCapabilityProfile(
            cameras = listOf(backMain),
            defaultBackCameraId = "0",
            performanceTier = PerformanceTier.ENTRY_LEVEL,
            performanceScore = 15,
            deviceModel = "Old Device",
            deviceManufacturer = "Generic",
            androidApiLevel = 26,
            totalRamGb = 1.8f,
            cpuCores = 4,
            discoveryTimestampMs = 1700000000000L,
        )
    }
}
