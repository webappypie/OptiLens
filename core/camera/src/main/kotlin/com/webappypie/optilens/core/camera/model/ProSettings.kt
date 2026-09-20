package com.webappypie.optilens.core.camera.model

import kotlinx.serialization.Serializable

/**
 * Standard photographic white balance presets mapping to Camera2 CONTROL_AWB_MODE.
 */
enum class WhiteBalanceMode(val label: String, val camera2AwbMode: Int) {
    AUTO("Auto", 1),              // CONTROL_AWB_MODE_AUTO
    INCANDESCENT("Tungsten", 2),  // CONTROL_AWB_MODE_INCANDESCENT
    FLUORESCENT("Fluorescent", 3),// CONTROL_AWB_MODE_FLUORESCENT
    DAYLIGHT("Daylight", 5),      // CONTROL_AWB_MODE_DAYLIGHT
    CLOUDY("Cloudy", 6),          // CONTROL_AWB_MODE_CLOUDY_DAYLIGHT
    SHADOW("Shadow", 8);          // CONTROL_AWB_MODE_SHADE
}

/**
 * Complete state, capabilities, and visual aids of manual Pro controls.
 */
data class ProCameraState(
    val iso: Int? = null,
    val isoRange: ClosedRange<Int>? = null,
    val isIsoManualSupported: Boolean = false,

    val shutterSpeedNanos: Long? = null,
    val shutterSpeedRangeNanos: ClosedRange<Long>? = null,
    val isShutterManualSupported: Boolean = false,

    val focusDistanceDiopters: Float? = null,
    val isFocusManualSupported: Boolean = false,

    val whiteBalanceMode: WhiteBalanceMode = WhiteBalanceMode.AUTO,
    val isWhiteBalanceSupported: Boolean = true,

    val evIndex: Int = 0,
    val evRange: ClosedRange<Int> = 0..0,
    val evStep: Float = 0f,

    val rawFormat: RawCaptureFormat = RawCaptureFormat.RAW_SENSOR,
    val isRawEnabled: Boolean = false,
    val isRawSupported: Boolean = false,
    val supportsRaw10: Boolean = false,
    val supportsRaw12: Boolean = false,
    val supportsRawPrivate: Boolean = false,
    val saveCompanionJpeg: Boolean = true,

    val focusPeakingEnabled: Boolean = false,
    val exposureZebraEnabled: Boolean = false,
    val histogramMode: HistogramMode = HistogramMode.LUMINANCE,

    val lensMetadata: LensMetadata = LensMetadata.EMPTY,
) {
    /** Whether any manual control is currently overriding the camera's AUTO 3A engine. */
    val isAnyManualActive: Boolean
        get() = iso != null || shutterSpeedNanos != null || focusDistanceDiopters != null ||
                whiteBalanceMode != WhiteBalanceMode.AUTO || evIndex != 0

    companion object {
        /**
         * Format an exposure duration in nanoseconds to standard photographer notation.
         * e.g. 1_000_000_000L -> "1s", 16_666_666L -> "1/60", 1_000_000L -> "1/1000".
         */
        fun formatShutterSpeed(nanos: Long?): String {
            if (nanos == null || nanos <= 0L) return "AUTO"
            val seconds = nanos / 1_000_000_000.0
            return if (seconds >= 1.0) {
                String.format(java.util.Locale.US, "%.1fs", seconds).replace(".0s", "s")
            } else {
                val denominator = kotlin.math.round(1.0 / seconds).toInt()
                "1/$denominator"
            }
        }
    }
}
