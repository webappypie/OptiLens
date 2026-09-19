package com.webappypie.optilens.core.camera.burst.model

/**
 * Optical, radiometric, and timing metadata extracted from Camera2 TotalCaptureResult
 * for an individual frame within a multi-frame burst sequence.
 *
 * @param timestampNs Sensor hardware capture timestamp in nanoseconds.
 * @param exposureTimeNs Duration the sensor was exposed to light in nanoseconds.
 * @param iso Sensor sensitivity gain (ISO rating).
 * @param focusDistanceDiopters Lens focus distance in diopters (0.0f = infinity).
 * @param lensState Optical lens state (e.g. stationary or moving).
 * @param aeState Camera2 auto-exposure state.
 * @param awbState Camera2 auto-white-balance state.
 * @param aperture Lens aperture f-number if supported by hardware.
 * @param focalLengthMm Physical focal length of the active lens in millimeters.
 * @param orientationDegrees Display rotation applied to the frame.
 */
data class FrameMetadata(
    val timestampNs: Long = 0L,
    val exposureTimeNs: Long = 0L,
    val iso: Int = 100,
    val focusDistanceDiopters: Float? = null,
    val lensState: Int? = null,
    val aeState: Int? = null,
    val awbState: Int? = null,
    val aperture: Float? = null,
    val focalLengthMm: Float? = null,
    val orientationDegrees: Int = 0,
) {
    /** Exposure time formatted as a photographic fraction (e.g. "1/250s", "1/60s", "1.2s"). */
    val formattedExposureTime: String
        get() {
            if (exposureTimeNs <= 0L) return "0s"
            val seconds = exposureTimeNs / 1_000_000_000.0
            return if (seconds >= 1.0) {
                String.format("%.1fs", seconds)
            } else {
                val fraction = (1.0 / seconds).toInt()
                "1/${fraction}s"
            }
        }
}
