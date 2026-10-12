package com.webappypie.optilens.core.camera.model

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.round
import kotlin.math.sqrt

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
        /** Diagonal of a 36 x 24 mm full-frame sensor, used for 35 mm-equivalent focal length. */
        private const val FULL_FRAME_DIAGONAL_MM = 43.2666f

        /** Candidate digital-crop stops offered when no lens backs them. */
        private val DIGITAL_STOPS = listOf(2.0f, 5.0f, 10.0f)

        /**
         * 35 mm-equivalent focal length, or null when focal length or sensor size is unknown.
         * Raw focal lengths are not comparable across sensors of different sizes.
         */
        fun equivalentFocalLengthMm(focalMm: Float, sensorWidthMm: Float, sensorHeightMm: Float): Float? {
            if (focalMm <= 0f || sensorWidthMm <= 0f || sensorHeightMm <= 0f) return null
            val diagonal = sqrt(sensorWidthMm * sensorWidthMm + sensorHeightMm * sensorHeightMm)
            return focalMm * FULL_FRAME_DIAGONAL_MM / diagonal
        }

        fun formatLabel(ratio: Float): String {
            val tenths = round(ratio * 10f) / 10f
            return if (abs(tenths - round(tenths)) < 0.05f) "${round(tenths).toInt()}x" else "${tenths}x"
        }

        /**
         * Derive truthful quick-zoom stops for the camera that is actually bound.
         *
         * - 1x is the bound camera's own lens.
         * - A sub-1x or tele stop is offered only when the bound (logical) camera can reach it via
         *   zoom ratio, and is optical only when one of ITS physical sensors has a matching
         *   35 mm-equivalent focal length. Lenses the OS does not expose to this app are never shown.
         * - Remaining stops (2x/5x/10x within the zoom range) are digital crops.
         */
        fun deriveFromProfile(profile: CameraDeviceProfile?): List<ZoomStop> {
            if (profile == null) {
                return listOf(ZoomStop(ratio = 1.0f, label = "1x", isOptical = true))
            }

            val mainEq = equivalentFocalLengthMm(
                focalMm = profile.focalLengthsMm.firstOrNull() ?: 0f,
                sensorWidthMm = profile.sensorInfo.physicalWidthMm,
                sensorHeightMm = profile.sensorInfo.physicalHeightMm,
            )

            // Physical lenses of this logical camera, as ratios relative to the main lens.
            val physicalRatios: List<Pair<Float, String>> = if (mainEq == null) {
                emptyList()
            } else {
                profile.physicalSensors.mapNotNull { phys ->
                    val eq = equivalentFocalLengthMm(
                        phys.focalLengthMm,
                        phys.sensorPhysicalWidthMm,
                        phys.sensorPhysicalHeightMm,
                    ) ?: return@mapNotNull null
                    (eq / mainEq) to phys.id
                }
            }

            fun opticalMatch(ratio: Float, tolerance: Float): Pair<Float, String>? =
                physicalRatios.firstOrNull { abs(it.first - ratio) <= tolerance }

            val stops = mutableListOf<ZoomStop>()

            // Ultrawide: only reachable when the bound camera's zoom range goes below 1x.
            if (profile.minZoom < 0.95f) {
                val match = opticalMatch(profile.minZoom, tolerance = 0.15f)
                stops += ZoomStop(
                    ratio = profile.minZoom,
                    label = formatLabel(profile.minZoom),
                    isOptical = match != null,
                    physicalSensorId = match?.second,
                )
            }

            stops += ZoomStop(ratio = 1.0f, label = "1x", isOptical = true)

            // Telephoto lenses that belong to this logical camera and lie within its zoom range.
            physicalRatios
                .filter { (ratio, _) -> ratio >= 1.5f && ratio <= profile.maxZoom + 0.01f }
                .forEach { (ratio, id) ->
                    val rounded = round(ratio * 10f) / 10f
                    stops += ZoomStop(ratio = rounded, label = formatLabel(rounded), isOptical = true, physicalSensorId = id)
                }

            // Digital crops, skipping any that coincide with an optical stop.
            DIGITAL_STOPS
                .filter { it <= profile.maxZoom + 0.01f }
                .filter { candidate -> stops.none { abs(it.ratio - candidate) < 0.3f } }
                .forEach { stops += ZoomStop(ratio = it, label = formatLabel(it), isOptical = false) }

            return stops.distinctBy { it.ratio }.sortedBy { it.ratio }
        }
    }
}
