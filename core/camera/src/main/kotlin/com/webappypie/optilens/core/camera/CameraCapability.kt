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
