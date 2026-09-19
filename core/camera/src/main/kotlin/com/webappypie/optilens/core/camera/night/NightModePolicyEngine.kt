package com.webappypie.optilens.core.camera.night

import com.webappypie.optilens.core.camera.model.CameraDeviceProfile
import com.webappypie.optilens.core.camera.model.PerformanceTier
import com.webappypie.optilens.core.camera.model.SubjectMotionLevel
import com.webappypie.optilens.core.camera.thermal.DeviceThermalState
import com.webappypie.optilens.core.camera.thermal.ThermalDegradationPolicy
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Intelligent decision engine selecting between OEM vendor Night extensions and
 * OptiLens custom computational multi-frame stacking pipeline.
 */
@Singleton
class NightModePolicyEngine @Inject constructor(
    private val exposurePlanner: NightExposurePlanner,
) {
    /**
     * Formulates the optimal [NightExecutionPlan].
     */
    fun evaluatePolicy(
        activeCameraProfile: CameraDeviceProfile?,
        performanceTier: PerformanceTier = PerformanceTier.MID_RANGE,
        stability: StabilityAssessment,
        subjectMotion: SubjectMotionLevel = SubjectMotionLevel.STATIC,
        luminance: Float,
        thermalState: DeviceThermalState = DeviceThermalState.NORMAL,
        thermalPolicy: ThermalDegradationPolicy = ThermalDegradationPolicy.forThermalState(thermalState),
        preference: NightPolicyPreference = NightPolicyPreference.AUTO,
        isHighContrastOrNeon: Boolean = false,
    ): NightExecutionPlan {
        val hasVendorNight = activeCameraProfile?.extensions?.night == true
        val isTripod = stability.classification == StabilityClassification.TRIPOD
        val isSubjectMoving = subjectMotion == SubjectMotionLevel.HIGH_MOTION
        val isThermallyConstrained = thermalState == DeviceThermalState.MODERATE ||
            thermalState == DeviceThermalState.SEVERE ||
            thermalState == DeviceThermalState.CRITICAL

        // Compute tailored exposure plan
        val exposurePlan = exposurePlanner.planExposure(
            luminance = luminance,
            stability = stability,
            subjectMotion = subjectMotion,
            thermalPolicy = thermalPolicy,
            isHighContrastOrNeon = isHighContrastOrNeon,
        )

        // Low-light boost recommendation for viewfinder framing
        val isPreviewBoostRecommended = luminance < 28.0f

        // 1. Explicit user override: PREFER_CUSTOM
        if (preference == NightPolicyPreference.PREFER_CUSTOM) {
            return NightExecutionPlan(
                mode = NightModeType.CUSTOM_COMPUTATIONAL,
                exposurePlan = exposurePlan,
                reason = "User preference: Forced custom multi-frame computational mode",
                isPreviewBoostRecommended = isPreviewBoostRecommended,
                movingSubjectFallbackTriggered = isSubjectMoving,
            )
        }

        // 2. Explicit user override: PREFER_VENDOR
        if (preference == NightPolicyPreference.PREFER_VENDOR && hasVendorNight) {
            return NightExecutionPlan(
                mode = NightModeType.VENDOR_EXTENSION,
                exposurePlan = exposurePlan,
                reason = "User preference: Forced OEM vendor Night extension",
                isPreviewBoostRecommended = isPreviewBoostRecommended,
                movingSubjectFallbackTriggered = false,
            )
        }

        // 3. AUTO Decision Policy
        // Condition for Vendor Night:
        // - Vendor extension exists
        // - Premium/Flagship tier
        // - Handheld (NOT on a tripod, where our deep 10-12 frame custom stack produces superior dynamic range and SNR)
        // - Static subject (vendor extensions smear or artifact on fast moving subjects)
        // - Device is not thermally throttled
        val isVendorEligible = hasVendorNight &&
            (performanceTier == PerformanceTier.FLAGSHIP || performanceTier == PerformanceTier.HIGH_PERFORMANCE) &&
            !isTripod &&
            !isSubjectMoving &&
            !isThermallyConstrained

        if (isVendorEligible) {
            return NightExecutionPlan(
                mode = NightModeType.VENDOR_EXTENSION,
                exposurePlan = exposurePlan,
                reason = "OEM vendor Night extension selected for handheld scene on verified hardware tier",
                isPreviewBoostRecommended = isPreviewBoostRecommended,
                movingSubjectFallbackTriggered = false,
            )
        }

        // 4. Custom Computational Pipeline Selection
        val reason = when {
            isTripod -> "Tripod detected: Deep computational stacking (10-12 frames) with extended exposure"
            isSubjectMoving -> "Subject motion detected: Dynamic short-exposure burst with ghost mask fallback"
            isThermallyConstrained -> "Thermal load active: Degraded burst count (${exposurePlan.frameCount} frames) to protect device"
            !hasVendorNight -> "Custom multi-frame fusion pipeline (OEM vendor extension unavailable)"
            else -> "Custom multi-frame fusion pipeline selected for optimal tonal fidelity and de-ghosting"
        }

        return NightExecutionPlan(
            mode = NightModeType.CUSTOM_COMPUTATIONAL,
            exposurePlan = exposurePlan,
            reason = reason,
            isPreviewBoostRecommended = isPreviewBoostRecommended,
            movingSubjectFallbackTriggered = isSubjectMoving,
        )
    }
}
