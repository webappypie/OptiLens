package com.webappypie.optilens.core.imaging.sr

import com.webappypie.optilens.core.camera.model.ZoomStop
import kotlin.math.abs

/**
 * Result of the optical-first routing determination.
 *
 * Enforces the core OptiLens rule:
 * "Always route to physical hardware optics first before considering digital crop or super-resolution."
 */
data class ZoomRoutingDecision(
    val targetZoomRatio: Float,
    val opticalBaseStop: ZoomStop,
    val digitalCropFactor: Float,
    val isPureOptical: Boolean,
    val superResEligible: Boolean,
    val recommendedSuperResScale: Float,
    val explanation: String,
)

/**
 * Optical-First Router for camera zoom control and computational pipeline dispatch.
 */
object OpticalZoomRouter {

    /**
     * Determines the optimal optical base lens and digital crop/super-resolution strategy.
     *
     * @param targetZoom Desired zoom ratio (e.g. 0.6f, 1.0f, 1.5f, 2.0f, 3.5f, 5.0f).
     * @param availableStops Hardware zoom stops derived truthful from physical camera capabilities.
     * @param is4xProEnabled Whether 4x Pro super resolution is active and permitted.
     */
    fun routeZoom(
        targetZoom: Float,
        availableStops: List<ZoomStop>,
        is4xProEnabled: Boolean = false,
    ): ZoomRoutingDecision {
        val opticalStops = availableStops.filter { it.isOptical }.sortedBy { it.ratio }
        val fallbackOpticalStop = opticalStops.firstOrNull { it.ratio == 1.0f }
            ?: opticalStops.firstOrNull()
            ?: ZoomStop(ratio = 1.0f, label = "1x", isOptical = true)

        // 1. Check if target matches any physical optical lens within 5% tolerance
        val matchingOptical = opticalStops.firstOrNull { abs(it.ratio - targetZoom) < 0.08f }
        if (matchingOptical != null) {
            return ZoomRoutingDecision(
                targetZoomRatio = targetZoom,
                opticalBaseStop = matchingOptical,
                digitalCropFactor = 1.0f,
                isPureOptical = true,
                superResEligible = false,
                recommendedSuperResScale = 1.0f,
                explanation = "Direct optical capture using physical lens '${matchingOptical.label}' (zero digital degradation).",
            )
        }

        // 2. Target zoom is between or beyond optical lenses.
        // Find the closest optical base lens with ratio <= targetZoom to minimize crop degradation.
        val baseLens = opticalStops.filter { it.ratio <= targetZoom }.maxByOrNull { it.ratio }
            ?: fallbackOpticalStop

        val cropFactor = targetZoom / baseLens.ratio
        val isSuperResEligible = cropFactor >= 1.12f

        val recommendedScale = when {
            !isSuperResEligible -> 1.0f
            is4xProEnabled && cropFactor >= 3.0f -> 4.0f
            else -> 2.0f
        }

        val explanation = if (isSuperResEligible) {
            "Cropping ${String.format(java.util.Locale.US, "%.1fx", cropFactor)} from physical '${baseLens.label}' lens with Super Resolution ${recommendedScale.toInt()}x enhancement."
        } else {
            "Minor digital crop of ${String.format(java.util.Locale.US, "%.2fx", cropFactor)} from physical '${baseLens.label}' lens."
        }

        return ZoomRoutingDecision(
            targetZoomRatio = targetZoom,
            opticalBaseStop = baseLens,
            digitalCropFactor = cropFactor,
            isPureOptical = false,
            superResEligible = isSuperResEligible,
            recommendedSuperResScale = recommendedScale,
            explanation = explanation,
        )
    }
}
