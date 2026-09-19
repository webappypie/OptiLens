package com.webappypie.optilens.core.camera.model

import kotlinx.serialization.Serializable
import kotlin.math.abs

/**
 * Representation of a quick-zoom stop derived from actual hardware optics.
 *
 * Enforces the strict OptiLens rule:
 * "Never label digital crop as optical 3x/5x."
 *
 * @param ratio Magnification multiplier relative to primary 1x lens (e.g. 0.6f, 1.0f, 2.0f, 3.0f, 5.0f).
 * @param label User-facing readout (e.g. "0.6x", "1x", "2x", "5x").
 * @param isOptical True ONLY if this stop is backed by a dedicated physical lens/sensor.
 * @param physicalSensorId ID of the physical camera sub-sensor if optical, or null if digital crop.
 */
@Serializable
data class ZoomStop(
    val ratio: Float,
    val label: String,
    val isOptical: Boolean,
    val physicalSensorId: String? = null,
) {
    companion object {
        /**
         * Derive truthful quick-zoom stops from a [CameraDeviceProfile].
         *
         * Inspects physical sub-sensor focal lengths against the primary sensor's
         * focal length to determine which multipliers are genuinely optical.
         */
        fun deriveFromProfile(profile: CameraDeviceProfile?): List<ZoomStop> {
            if (profile == null) {
                return listOf(
                    ZoomStop(ratio = 1.0f, label = "1x", isOptical = true),
                )
            }

            val primaryFocalMm = profile.focalLengthsMm.firstOrNull() ?: 4.5f
            val stops = mutableListOf<ZoomStop>()

            // 1. Check physical sub-sensors for genuine optical stops
            val physicalSensors = profile.physicalSensors
            val opticalRatios = mutableMapOf<Float, String>()

            for (phys in physicalSensors) {
                if (phys.focalLengthMm > 0f && primaryFocalMm > 0f) {
                    val rawRatio = phys.focalLengthMm / primaryFocalMm
                    // Round to nearest tenth for clean matching (e.g. 0.5x, 0.6x, 3.0x, 5.0x)
                    val roundedRatio = (kotlin.math.round(rawRatio * 10f) / 10f)
                    opticalRatios[roundedRatio] = phys.id
                }
            }

            // Ultrawide stop (e.g. 0.5x or 0.6x)
            if (profile.minZoom < 0.95f) {
                val uwRatio = profile.minZoom
                val isOptical = opticalRatios.keys.any { abs(it - uwRatio) < 0.15f }
                val physId = opticalRatios.entries.firstOrNull { abs(it.key - uwRatio) < 0.15f }?.value
                val label = if (uwRatio < 1.0f) String.format(java.util.Locale.US, "%.1fx", uwRatio) else "${uwRatio.toInt()}x"
                stops.add(ZoomStop(ratio = uwRatio, label = label, isOptical = isOptical, physicalSensorId = physId))
            }

            // Primary 1x is always optical on the main camera
            stops.add(ZoomStop(ratio = 1.0f, label = "1x", isOptical = true, physicalSensorId = null))

            // Standard common intermediate stops up to maxZoom
            val candidateMultipliers = listOf(2.0f, 3.0f, 5.0f, 10.0f)
            for (mult in candidateMultipliers) {
                if (mult <= profile.maxZoom) {
                    // Check if any physical sensor corresponds to this multiplier within 15%
                    val matchingOptical = opticalRatios.entries.firstOrNull { abs(it.key - mult) < 0.25f }
                    val isOptical = matchingOptical != null
                    val label = "${mult.toInt()}x"
                    stops.add(
                        ZoomStop(
                            ratio = mult,
                            label = label,
                            isOptical = isOptical,
                            physicalSensorId = matchingOptical?.value,
                        )
                    )
                }
            }

            return stops.distinctBy { it.ratio }.sortedBy { it.ratio }
        }
    }
}
