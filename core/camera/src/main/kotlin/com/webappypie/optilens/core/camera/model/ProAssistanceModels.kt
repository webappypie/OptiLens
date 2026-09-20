package com.webappypie.optilens.core.camera.model

import kotlinx.serialization.Serializable

/**
 * Hardware-supported RAW sensor acquisition formats.
 */
@Serializable
enum class RawCaptureFormat(val label: String, val extension: String, val mimeType: String) {
    RAW_SENSOR("RAW DNG (16-bit)", "dng", "image/x-adobe-dng"),
    RAW10("RAW10 (10-bit)", "raw10", "image/x-raw-sensor"),
    RAW12("RAW12 (12-bit)", "raw12", "image/x-raw-sensor"),
    RAW_PRIVATE("RAW Private (OEM)", "raw", "application/octet-stream");

    companion object {
        val DEFAULT = RAW_SENSOR
    }
}

/**
 * Optical and exposure metadata for the currently active lens and capture request.
 */
@Serializable
data class LensMetadata(
    val focalLengthMm: Float = 0f,
    val focalLength35mmEquivalent: Int = 0,
    val apertureFNumber: Float = 0f,
    val minFocusDistanceDiopters: Float = 0f,
    val currentFocusDistanceDiopters: Float? = null,
    val currentIso: Int? = null,
    val currentShutterSpeedNanos: Long? = null,
    val sensorWidthMm: Float = 0f,
    val sensorHeightMm: Float = 0f,
    val isFixedFocus: Boolean = false,
) {
    /**
     * User-facing formatted optical readout strip.
     * e.g. "24mm eq · f/1.9 · ISO 100 · 1/250s · 0.8m"
     */
    val readoutSummary: String
        get() {
            val parts = mutableListOf<String>()
            if (focalLength35mmEquivalent > 0) {
                parts.add("${focalLength35mmEquivalent}mm eq")
            } else if (focalLengthMm > 0f) {
                parts.add(String.format(java.util.Locale.US, "%.1fmm", focalLengthMm))
            }
            if (apertureFNumber > 0f) {
                parts.add(String.format(java.util.Locale.US, "f/%.1f", apertureFNumber))
            }
            if (currentIso != null) {
                parts.add("ISO $currentIso")
            }
            if (currentShutterSpeedNanos != null && currentShutterSpeedNanos > 0L) {
                parts.add(ProCameraState.formatShutterSpeed(currentShutterSpeedNanos))
            }
            if (isFixedFocus) {
                parts.add("Fixed Focus")
            } else if (currentFocusDistanceDiopters != null && currentFocusDistanceDiopters > 0.01f) {
                val distMeters = 1.0f / currentFocusDistanceDiopters
                parts.add(String.format(java.util.Locale.US, "%.1fm", distMeters))
            }
            return parts.joinToString(" · ")
        }

    companion object {
        val EMPTY = LensMetadata()
    }
}

/**
 * Viewfinder focus peaking data detecting high-frequency contrast in the plane of focus.
 */
data class FocusPeakingData(
    val edgePoints: FloatArray = FloatArray(0),
    val peakScore: Float = 0f,
    val isEnabled: Boolean = false,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as FocusPeakingData
        if (!edgePoints.contentEquals(other.edgePoints)) return false
        if (peakScore != other.peakScore) return false
        if (isEnabled != other.isEnabled) return false
        return true
    }

    override fun hashCode(): Int {
        var result = edgePoints.contentHashCode()
        result = 31 * result + peakScore.hashCode()
        result = 31 * result + isEnabled.hashCode()
        return result
    }

    companion object {
        val EMPTY = FocusPeakingData()
    }
}

/**
 * Viewfinder exposure zebra stripes data indicating regions exceeding highlight clipping thresholds.
 */
data class ExposureZebraData(
    val clippedRegions: FloatArray = FloatArray(0),
    val clippedPercent: Float = 0f,
    val isEnabled: Boolean = false,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ExposureZebraData
        if (!clippedRegions.contentEquals(other.clippedRegions)) return false
        if (clippedPercent != other.clippedPercent) return false
        if (isEnabled != other.isEnabled) return false
        return true
    }

    override fun hashCode(): Int {
        var result = clippedRegions.contentHashCode()
        result = 31 * result + clippedPercent.hashCode()
        result = 31 * result + isEnabled.hashCode()
        return result
    }

    companion object {
        val EMPTY = ExposureZebraData()
    }
}

/**
 * Live viewfinder histogram channel display mode.
 */
@Serializable
enum class HistogramMode(val label: String) {
    LUMINANCE("Luma"),
    RGB("RGB"),
    BOTH("RGB + Luma");

    fun next(): HistogramMode = when (this) {
        LUMINANCE -> RGB
        RGB -> BOTH
        BOTH -> LUMINANCE
    }
}
