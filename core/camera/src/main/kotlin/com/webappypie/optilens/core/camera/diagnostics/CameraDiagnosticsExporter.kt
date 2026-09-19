package com.webappypie.optilens.core.camera.diagnostics

import com.webappypie.optilens.core.camera.model.CameraCapabilityProfile
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Generates technical JSON and Markdown diagnostic reports from a [CameraCapabilityProfile].
 */
@Singleton
class CameraDiagnosticsExporter @Inject constructor() {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    /**
     * Serializes the complete capability profile to formatted JSON.
     */
    fun exportToJson(profile: CameraCapabilityProfile): String {
        return json.encodeToString(profile)
    }

    /**
     * Formats the capability profile into a comprehensive Markdown diagnostics report.
     */
    fun exportToMarkdown(profile: CameraCapabilityProfile): String = buildString {
        appendLine("# OptiLens Camera Hardware Diagnostics")
        appendLine()
        appendLine("## Device Information")
        appendLine("- **Device:** ${profile.deviceManufacturer} ${profile.deviceModel}")
        appendLine("- **Fingerprint:** `${profile.deviceFingerprint}`")
        appendLine("- **Android API Level:** ${profile.androidApiLevel}")
        appendLine("- **Total RAM:** ${String.format("%.1f", profile.totalRamGb)} GB")
        appendLine("- **CPU Cores:** ${profile.cpuCores}")
        appendLine("- **Performance Tier:** **${profile.performanceTier}** (Score: ${profile.performanceScore}/100)")
        appendLine("- **Cameras Enumerated:** ${profile.cameras.size}")
        appendLine("- **Active Quirks:** ${if (profile.detectedQuirks.isEmpty()) "None" else profile.detectedQuirks.joinToString(", ")}")
        appendLine()

        appendLine("## Camera Units")
        for (cam in profile.cameras) {
            appendLine("### Camera ID: `${cam.id}` (${cam.lensFacing})")
            appendLine("- **Hardware Level:** `${cam.hardwareLevel}`")
            appendLine("- **Focal Lengths (mm):** ${if (cam.focalLengthsMm.isEmpty()) "N/A" else cam.focalLengthsMm.joinToString(", ") { "${it}mm" }}")
            appendLine("- **Sensor Array:** Active ${cam.sensorInfo.activeArrayWidth}x${cam.sensorInfo.activeArrayHeight} | Pixel ${cam.sensorInfo.pixelArrayWidth}x${cam.sensorInfo.pixelArrayHeight}")
            appendLine("- **Physical Sensor Size:** ${cam.sensorInfo.physicalWidthMm} x ${cam.sensorInfo.physicalHeightMm} mm")
            appendLine("- **Sensor Orientation:** ${cam.sensorInfo.orientationDegrees}° (Timestamp: ${cam.sensorInfo.timestampSource})")
            appendLine("- **Zoom Range:** ${cam.minZoom}x to ${cam.maxZoom}x")
            appendLine("- **Flash:** ${if (cam.controls.hasFlash) "Available" else "Not available"}")
            appendLine()

            appendLine("#### Supported Formats & Maximum Output Sizes")
            appendLine("| Stream Format | Maximum Resolution | Supported |")
            appendLine("|---|---|---|")
            appendLine("| JPEG | ${cam.resolutions.maxJpegSize?.toString() ?: "None"} | ${cam.resolutions.supportedJpegSizes.isNotEmpty()} |")
            appendLine("| RAW_SENSOR | ${cam.resolutions.maxRawSize?.toString() ?: "None"} | ${cam.streamCapabilities.supportsRaw} |")
            appendLine("| YUV_420_888 | ${cam.resolutions.maxYuvSize?.toString() ?: "None"} | ${cam.resolutions.supportedYuvSizes.isNotEmpty()} |")
            appendLine("| PRIVATE | ${cam.resolutions.maxPrivateSize?.toString() ?: "None"} | ${cam.resolutions.maxPrivateSize != null} |")
            if (cam.resolutions.ultraHighResolutionSizes.isNotEmpty()) {
                appendLine("| Ultra High-Res | ${cam.resolutions.ultraHighResolutionSizes.first()} | true |")
            }
            appendLine()

            appendLine("#### Capabilities & Advanced Features")
            appendLine("- **RAW Formats:** Sensor: ${cam.rawCapabilities.supportsRawSensor}, RAW10: ${cam.rawCapabilities.supportsRaw10}, RAW12: ${cam.rawCapabilities.supportsRaw12}, RAW_PRIVATE: ${cam.rawCapabilities.supportsRawPrivate}")
            appendLine("- **Reprocessing:** YUV Reprocessing: ${cam.streamCapabilities.supportsYuvReprocessing} | Private Reprocessing: ${cam.streamCapabilities.supportsPrivateReprocessing}")
            appendLine("- **Manual Controls:** Manual Sensor: ${cam.streamCapabilities.supportsManualSensor} (ISO ${cam.streamCapabilities.isoRangeMin}..${cam.streamCapabilities.isoRangeMax}, Exp ${cam.streamCapabilities.exposureTimeRangeMinNs}..${cam.streamCapabilities.exposureTimeRangeMaxNs} ns)")
            appendLine("- **Multi-Camera Support:** ${cam.streamCapabilities.isLogicalMultiCamera} (Sub-cameras: ${cam.physicalCameraIds.size})")
            appendLine("- **Stabilization:** Optical (OIS): ${cam.stabilization.opticalImageStabilization} | Electronic (EIS): ${cam.stabilization.electronicVideoStabilization}")
            appendLine("- **HDR & Color:** 10-bit HDR: ${cam.streamCapabilities.supportsTenBitHdr} | Profiles: ${if (cam.streamCapabilities.dynamicRangeProfiles.isEmpty()) "Standard 8-bit" else cam.streamCapabilities.dynamicRangeProfiles.joinToString(", ")}")
            appendLine("- **CameraX Extensions:** Bokeh: ${cam.extensions.bokeh}, HDR: ${cam.extensions.hdr}, Night: ${cam.extensions.night}, Face Retouch: ${cam.extensions.faceRetouch}, Low-Light Boost: ${cam.extensions.lowLightBoost}")
            appendLine()
        }
    }
}
