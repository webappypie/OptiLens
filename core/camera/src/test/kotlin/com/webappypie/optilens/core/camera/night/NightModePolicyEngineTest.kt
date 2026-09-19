package com.webappypie.optilens.core.camera.night

import com.webappypie.optilens.core.camera.fixtures.CameraCapabilityFixtures
import com.webappypie.optilens.core.camera.model.PerformanceTier
import com.webappypie.optilens.core.camera.model.SubjectMotionLevel
import com.webappypie.optilens.core.camera.thermal.DeviceThermalState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NightModePolicyEngineTest {

    private val planner = NightExposurePlanner()
    private val engine = NightModePolicyEngine(planner)

    private val vendorSupportedProfile = CameraCapabilityFixtures.createFlagshipProfile().cameras.first()
    private val vendorUnsupportedProfile = CameraCapabilityFixtures.createLegacyProfile().cameras.first()

    @Test
    fun evaluatePolicy_forcedCustomPreference_returnsCustomComputational() {
        val stability = StabilityAssessment(
            classification = StabilityClassification.HANDHELD_STABLE,
            averageAngularVelocity = 0.04f,
            isTripod = false,
        )

        val plan = engine.evaluatePolicy(
            activeCameraProfile = vendorSupportedProfile,
            performanceTier = PerformanceTier.FLAGSHIP,
            stability = stability,
            luminance = 15f,
            preference = NightPolicyPreference.PREFER_CUSTOM,
        )

        assertEquals(NightModeType.CUSTOM_COMPUTATIONAL, plan.mode)
        assertTrue(plan.reason.contains("User preference"))
        assertTrue(plan.isPreviewBoostRecommended)
    }

    @Test
    fun evaluatePolicy_forcedVendorPreferenceWithSupport_returnsVendorExtension() {
        val stability = StabilityAssessment(
            classification = StabilityClassification.HANDHELD_STABLE,
            averageAngularVelocity = 0.04f,
            isTripod = false,
        )

        val plan = engine.evaluatePolicy(
            activeCameraProfile = vendorSupportedProfile,
            performanceTier = PerformanceTier.MID_RANGE,
            stability = stability,
            luminance = 15f,
            preference = NightPolicyPreference.PREFER_VENDOR,
        )

        assertEquals(NightModeType.VENDOR_EXTENSION, plan.mode)
        assertTrue(plan.reason.contains("User preference"))
    }

    @Test
    fun evaluatePolicy_autoFlagshipHandheldStatic_selectsVendorExtension() {
        val stability = StabilityAssessment(
            classification = StabilityClassification.HANDHELD_STABLE,
            averageAngularVelocity = 0.04f,
            isTripod = false,
        )

        val plan = engine.evaluatePolicy(
            activeCameraProfile = vendorSupportedProfile,
            performanceTier = PerformanceTier.FLAGSHIP,
            stability = stability,
            subjectMotion = SubjectMotionLevel.STATIC,
            luminance = 12f,
            thermalState = DeviceThermalState.NORMAL,
            preference = NightPolicyPreference.AUTO,
        )

        assertEquals(NightModeType.VENDOR_EXTENSION, plan.mode)
        assertFalse(plan.movingSubjectFallbackTriggered)
    }

    @Test
    fun evaluatePolicy_autoOnTripod_selectsCustomComputationalForDeepStack() {
        val stability = StabilityAssessment(
            classification = StabilityClassification.TRIPOD,
            averageAngularVelocity = 0.01f,
            isTripod = true,
        )

        val plan = engine.evaluatePolicy(
            activeCameraProfile = vendorSupportedProfile,
            performanceTier = PerformanceTier.FLAGSHIP,
            stability = stability,
            subjectMotion = SubjectMotionLevel.STATIC,
            luminance = 8f,
            preference = NightPolicyPreference.AUTO,
        )

        assertEquals(NightModeType.CUSTOM_COMPUTATIONAL, plan.mode)
        assertTrue(plan.reason.contains("Tripod detected"))
        assertTrue(plan.exposurePlan.frameCount >= 10)
    }

    @Test
    fun evaluatePolicy_autoSubjectMoving_selectsCustomComputationalWithFallback() {
        val stability = StabilityAssessment(
            classification = StabilityClassification.HANDHELD_STABLE,
            averageAngularVelocity = 0.05f,
            isTripod = false,
        )

        val plan = engine.evaluatePolicy(
            activeCameraProfile = vendorSupportedProfile,
            performanceTier = PerformanceTier.FLAGSHIP,
            stability = stability,
            subjectMotion = SubjectMotionLevel.HIGH_MOTION,
            luminance = 14f,
            preference = NightPolicyPreference.AUTO,
        )

        assertEquals(NightModeType.CUSTOM_COMPUTATIONAL, plan.mode)
        assertTrue(plan.movingSubjectFallbackTriggered)
        assertTrue(plan.reason.contains("Subject motion"))
    }

    @Test
    fun evaluatePolicy_autoThermalThrottling_selectsCustomWithCappedFrames() {
        val stability = StabilityAssessment(
            classification = StabilityClassification.HANDHELD_STABLE,
            averageAngularVelocity = 0.05f,
            isTripod = false,
        )

        val plan = engine.evaluatePolicy(
            activeCameraProfile = vendorSupportedProfile,
            performanceTier = PerformanceTier.FLAGSHIP,
            stability = stability,
            subjectMotion = SubjectMotionLevel.STATIC,
            luminance = 10f,
            thermalState = DeviceThermalState.SEVERE,
            preference = NightPolicyPreference.AUTO,
        )

        assertEquals(NightModeType.CUSTOM_COMPUTATIONAL, plan.mode)
        assertTrue(plan.reason.contains("Thermal load"))
        assertTrue(plan.exposurePlan.frameCount <= 4)
    }

    @Test
    fun evaluatePolicy_autoNoVendorSupport_selectsCustomComputational() {
        val stability = StabilityAssessment(
            classification = StabilityClassification.HANDHELD_STABLE,
            averageAngularVelocity = 0.05f,
            isTripod = false,
        )

        val plan = engine.evaluatePolicy(
            activeCameraProfile = vendorUnsupportedProfile,
            performanceTier = PerformanceTier.ENTRY_LEVEL,
            stability = stability,
            luminance = 20f,
            preference = NightPolicyPreference.AUTO,
        )

        assertEquals(NightModeType.CUSTOM_COMPUTATIONAL, plan.mode)
        assertTrue(plan.reason.contains("unavailable"))
    }
}
