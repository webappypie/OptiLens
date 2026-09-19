package com.webappypie.optilens.core.camera.portrait

import com.webappypie.optilens.core.camera.model.CameraDeviceProfile
import com.webappypie.optilens.core.camera.model.DetectedFace
import com.webappypie.optilens.core.camera.model.PerformanceTier
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Intelligent decision policy engine selecting between OEM vendor Bokeh extensions
 * and OptiLens custom optical disc depth synthesis with face-aware detail protection.
 */
@Singleton
class PortraitPolicyEngine @Inject constructor() {

    /**
     * Evaluates camera capabilities, scene illumination, face landmarks, and user preferences
     * to formulate an optimal [PortraitExecutionPlan].
     */
    fun evaluatePolicy(
        activeCameraProfile: CameraDeviceProfile?,
        performanceTier: PerformanceTier = PerformanceTier.MID_RANGE,
        detectedFaces: List<DetectedFace> = emptyList(),
        sceneLuminance: Float = 100.0f,
        aperture: PortraitAperture = PortraitAperture.DEFAULT,
        skinSmoothingStrength: Float = 0.25f,
        preference: PortraitPolicyPreference = PortraitPolicyPreference.AUTO,
    ): PortraitExecutionPlan {
        val hasVendorBokeh = activeCameraProfile?.extensions?.bokeh == true
        val faceCount = detectedFaces.size

        // 1. Backlit portrait detection & face exposure balancing
        var isBacklit = false
        var faceEvCompensation = 0.0f

        val faceLuminances = detectedFaces.mapNotNull { it.meanLuminance }
        if (faceLuminances.isNotEmpty()) {
            val avgFaceLum = faceLuminances.average().toFloat()
            if (sceneLuminance > avgFaceLum + 40.0f || (sceneLuminance / avgFaceLum.coerceAtLeast(10.0f)) > 1.6f) {
                isBacklit = true
                faceEvCompensation = ((sceneLuminance - avgFaceLum) / 50.0f).coerceIn(0.4f, 1.8f)
            }
        } else if (sceneLuminance > 140.0f && faceCount > 0) {
            // Bright background with detected foreground faces - apply mild compensatory lift
            isBacklit = true
            faceEvCompensation = 0.6f
        }

        // 2. User forced preference: PREFER_CUSTOM
        if (preference == PortraitPolicyPreference.PREFER_CUSTOM) {
            return PortraitExecutionPlan(
                mode = PortraitModeType.CUSTOM_SOFTWARE_BOKEH,
                aperture = aperture,
                faceCount = faceCount,
                faceExposureCompensationEv = faceEvCompensation,
                isBacklitScene = isBacklit,
                skinSmoothingStrength = skinSmoothingStrength,
                protectEyeDetails = true,
                reason = "User preference: Forced custom software optical disc bokeh and face-aware processing",
            )
        }

        // 3. User forced preference: PREFER_VENDOR
        if (preference == PortraitPolicyPreference.PREFER_VENDOR && hasVendorBokeh) {
            return PortraitExecutionPlan(
                mode = PortraitModeType.VENDOR_BOKEH,
                aperture = aperture,
                faceCount = faceCount,
                faceExposureCompensationEv = faceEvCompensation,
                isBacklitScene = isBacklit,
                skinSmoothingStrength = skinSmoothingStrength,
                protectEyeDetails = true,
                reason = "User preference: Forced OEM vendor Bokeh extension",
            )
        }

        // 4. AUTO Decision Policy
        // Vendor extensions are preferred when:
        // - Supported on verified hardware tier (Flagship or High Performance)
        // - Single or couple portrait (<= 2 faces; vendor algorithms often blur background family members)
        // - Standard default aperture (vendor bokeh does not allow fine f-stop customization)
        val isVendorEligible = hasVendorBokeh &&
            performanceTier != PerformanceTier.ENTRY_LEVEL &&
            faceCount in 1..2 &&
            !isBacklit &&
            aperture == PortraitAperture.DEFAULT

        if (isVendorEligible) {
            return PortraitExecutionPlan(
                mode = PortraitModeType.VENDOR_BOKEH,
                aperture = aperture,
                faceCount = faceCount,
                faceExposureCompensationEv = faceEvCompensation,
                isBacklitScene = isBacklit,
                skinSmoothingStrength = skinSmoothingStrength,
                protectEyeDetails = true,
                reason = "OEM vendor Bokeh extension selected for verified hardware tier",
            )
        }

        // 5. Custom Software Bokeh Selection
        val reason = when {
            faceCount > 2 -> "Multi-face portrait ($faceCount faces): Custom depth mapping protects all subjects in plane of focus"
            aperture != PortraitAperture.DEFAULT -> "Aperture customized to ${aperture.label}: Custom optical disc circle-of-confusion simulation engaged"
            !hasVendorBokeh -> "Custom software optical disc bokeh (OEM vendor Bokeh extension unavailable)"
            else -> "Custom software optical bokeh selected for natural detail preservation"
        }

        return PortraitExecutionPlan(
            mode = PortraitModeType.CUSTOM_SOFTWARE_BOKEH,
            aperture = aperture,
            faceCount = faceCount,
            faceExposureCompensationEv = faceEvCompensation,
            isBacklitScene = isBacklit,
            skinSmoothingStrength = skinSmoothingStrength,
            protectEyeDetails = true,
            reason = reason,
        )
    }
}
