package com.webappypie.optilens.core.camera

import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Shutter-priority and ISO-priority exposure for Camera2 manual mode.
 *
 * Camera2 only accepts manual exposure as a pair (AE off + sensitivity + exposure time). Setting
 * just one of them leaves the other undefined. These helpers keep the exposure that
 * auto-exposure last metered, optionally shifted by EV compensation.
 *
 * Gain includes CONTROL_POST_RAW_SENSITIVITY_BOOST: in low light auto-exposure applies a large
 * digital boost on top of the reported SENSOR_SENSITIVITY (e.g. "ISO 130" plus up to 32x boost on
 * the Redmi Note 11 Pro+). Ignoring it produced near-black manual-shutter photos on device.
 */
object ExposurePriority {

    private const val NEUTRAL_BOOST = 100

    /** Latest auto-exposure metering from the preview stream. */
    data class AutoExposure(
        val iso: Int,
        val exposureTimeNs: Long,
        /** CONTROL_POST_RAW_SENSITIVITY_BOOST (100 = none). */
        val postRawBoost: Int = NEUTRAL_BOOST,
    ) {
        val isValid: Boolean get() = iso > 0 && exposureTimeNs > 0 && postRawBoost > 0

        /** Total sensor + digital gain in ISO units. */
        val totalGain: Double get() = iso.toDouble() * postRawBoost / NEUTRAL_BOOST
    }

    /** A complete manual exposure request. */
    data class ManualExposure(val iso: Int, val exposureTimeNs: Long, val postRawBoost: Int)

    /** 2^(evIndex * evStep): >1 brightens, <1 darkens. */
    fun evScale(evIndex: Int, evStep: Float): Double =
        if (evIndex == 0 || evStep <= 0f) 1.0 else 2.0.pow(evIndex * evStep.toDouble())

    /**
     * Shutter priority: keeps [targetExposureNs] and supplies the gain needed to reproduce the
     * metered exposure — analog ISO first (up to the sensor range), the rest as post-RAW boost.
     *
     * With [maxTotalGain] (scene modes), a shutter that would need more gain than that is
     * lengthened instead, but never beyond [slowestShutterNs] — trading some motion freeze for
     * usable noise in dim light ("fast shutter, light permitting"). Past that bound the gain
     * rises again (within the sensor's ranges). Preview metering can be much slower than the
     * still auto exposure in low light, so the metered time is not used as the bound.
     */
    fun forShutter(
        metered: AutoExposure,
        targetExposureNs: Long,
        isoRange: ClosedRange<Int>?,
        boostRange: ClosedRange<Int>?,
        evScale: Double = 1.0,
        maxTotalGain: Double? = null,
        slowestShutterNs: Long? = null,
    ): ManualExposure {
        require(metered.isValid && targetExposureNs > 0)
        val exposureProduct = metered.totalGain * metered.exposureTimeNs * evScale
        var timeNs = targetExposureNs
        if (maxTotalGain != null && exposureProduct / timeNs > maxTotalGain) {
            val bound = maxOf(slowestShutterNs ?: metered.exposureTimeNs, targetExposureNs)
            timeNs = (exposureProduct / maxTotalGain).roundToLong().coerceAtMost(bound)
        }
        val neededGain = exposureProduct / timeNs
        val iso = neededGain.roundToInt().coerceAtLeast(1).let { g -> isoRange?.let { g.coerceIn(it) } ?: g }
        val boost = if (boostRange == null) {
            NEUTRAL_BOOST
        } else {
            (neededGain / iso * NEUTRAL_BOOST).roundToInt().coerceIn(boostRange)
        }
        return ManualExposure(iso = iso, exposureTimeNs = timeNs, postRawBoost = boost)
    }

    /** ISO priority: keeps [targetIso] (no digital boost) and derives the exposure time. */
    fun forIso(
        metered: AutoExposure,
        targetIso: Int,
        exposureRangeNs: ClosedRange<Long>?,
        evScale: Double = 1.0,
    ): ManualExposure {
        require(metered.isValid && targetIso > 0)
        val time = (metered.totalGain * metered.exposureTimeNs / targetIso * evScale).roundToLong().coerceAtLeast(1L)
        val clamped = exposureRangeNs?.let { time.coerceIn(it) } ?: time
        return ManualExposure(iso = targetIso, exposureTimeNs = clamped, postRawBoost = NEUTRAL_BOOST)
    }
}
