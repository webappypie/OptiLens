package com.webappypie.optilens.core.camera

/**
 * Describes the hardware camera capabilities detected on the device.
 *
 * Populated lazily at runtime by CameraCapabilityDetector (Phase 03).
 * Phase 01 defines the data contract only — no implementation yet.
 */
data class CameraCapability(
    /** Whether the device has a rear physical camera. */
    val hasRearCamera: Boolean = false,
    /** Whether the device has a front physical camera. */
    val hasFrontCamera: Boolean = false,
    /** Whether OIS (optical image stabilisation) is available on the rear camera. */
    val hasOis: Boolean = false,
    /** Whether the rear camera sensor supports RAW output. */
    val supportsRaw: Boolean = false,
    /** Whether the rear camera can do hardware-level HDR capture. */
    val supportsHdrCapture: Boolean = false,
    /** Maximum number of simultaneous capture streams supported. */
    val maxCaptureStreams: Int = 1,
    /** Whether the device supports logical multi-camera (ultra-wide / tele). */
    val hasLogicalMultiCamera: Boolean = false,
    /** Telephoto (zoom) multipliers available (e.g. [1.0f, 2.0f, 5.0f]). */
    val availableZoomRatios: List<Float> = listOf(1.0f),
)

/**
 * Converts a detailed [com.webappypie.optilens.core.camera.model.CameraCapabilityProfile]
 * into the legacy [CameraCapability] for backwards compatibility with [CameraController].
 */
fun com.webappypie.optilens.core.camera.model.CameraCapabilityProfile.toLegacyCameraCapability(): CameraCapability {
    val backCam = primaryBackCamera
    val hasFront = primaryFrontCamera != null
    val hasBack = backCam != null

    return CameraCapability(
        hasRearCamera = hasBack,
        hasFrontCamera = hasFront,
        hasOis = backCam?.stabilization?.opticalImageStabilization == true,
        supportsRaw = backCam?.streamCapabilities?.supportsRaw == true,
        supportsHdrCapture = backCam?.extensions?.hdr == true || backCam?.streamCapabilities?.supportsTenBitHdr == true,
        maxCaptureStreams = if (backCam?.hardwareLevel == com.webappypie.optilens.core.camera.model.CameraHardwareLevel.LEVEL_3) 3 else 2,
        hasLogicalMultiCamera = backCam?.streamCapabilities?.isLogicalMultiCamera == true,
        availableZoomRatios = backCam?.computeAvailableZoomRatios() ?: listOf(1.0f),
    )
}
