package com.webappypie.optilens.core.camera.night

import com.webappypie.optilens.core.camera.model.SubjectMotionLevel
import com.webappypie.optilens.core.camera.thermal.DeviceThermalState
import com.webappypie.optilens.core.camera.thermal.ThermalDegradationPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NightExposurePlannerTest {

    private val planner = NightExposurePlanner()

    @Test
    fun planExposure_tripodDarkScene_plansDeep12FrameStack() {
        val stability = StabilityAssessment(
            classification = StabilityClassification.TRIPOD,
            averageAngularVelocity = 0.01f,
            isTripod = true,
        )

        val plan = planner.planExposure(
            luminance = 10f,
            stability = stability,
            subjectMotion = SubjectMotionLevel.STATIC,
        )

        assertEquals(12, plan.frameCount)
        assertEquals(250_000_000L, plan.targetShutterNanos) // 1/4s
        assertTrue(plan.enableChromaCleanup)
        assertTrue(plan.enableHighlightProtection)
        assertTrue(plan.conservativeSharpening)
    }

    @Test
    fun planExposure_handheldStable_plans8FramesAtLowLight() {
        val stability = StabilityAssessment(
            classification = StabilityClassification.HANDHELD_STABLE,
            averageAngularVelocity = 0.04f,
            isTripod = false,
        )

        val plan = planner.planExposure(
            luminance = 15f,
            stability = stability,
            subjectMotion = SubjectMotionLevel.STATIC,
        )

        assertEquals(8, plan.frameCount)
        assertEquals(80_000_000L, plan.targetShutterNanos) // 1/12s
        assertTrue(plan.isoBoostFactor > 1.0f)
    }

    @Test
    fun planExposure_handheldModerate_plans4Frames() {
        val stability = StabilityAssessment(
            classification = StabilityClassification.HANDHELD_MODERATE,
            averageAngularVelocity = 0.18f,
            isTripod = false,
        )

        val plan = planner.planExposure(
            luminance = 15f,
            stability = stability,
            subjectMotion = SubjectMotionLevel.STATIC,
        )

        assertEquals(4, plan.frameCount)
        assertEquals(33_333_333L, plan.targetShutterNanos) // 1/30s
    }

    @Test
    fun planExposure_unsteady_plans2Frames() {
        val stability = StabilityAssessment(
            classification = StabilityClassification.UNSTEADY,
            averageAngularVelocity = 0.50f,
            isTripod = false,
        )

        val plan = planner.planExposure(
            luminance = 15f,
            stability = stability,
            subjectMotion = SubjectMotionLevel.STATIC,
        )

        assertEquals(2, plan.frameCount)
        assertEquals(20_000_000L, plan.targetShutterNanos) // 1/50s
    }

    @Test
    fun planExposure_highSubjectMotion_clampsTo2FramesAndFastShutter() {
        val stability = StabilityAssessment(
            classification = StabilityClassification.TRIPOD,
            averageAngularVelocity = 0.01f,
            isTripod = true,
        )

        val plan = planner.planExposure(
            luminance = 10f,
            stability = stability,
            subjectMotion = SubjectMotionLevel.HIGH_MOTION,
        )

        assertEquals(2, plan.frameCount)
        assertEquals(25_000_000L, plan.targetShutterNanos) // 1/40s to freeze
    }

    @Test
    fun planExposure_severeThermalThrottling_clampsFrameCountAndFilters() {
        val stability = StabilityAssessment(
            classification = StabilityClassification.TRIPOD,
            averageAngularVelocity = 0.01f,
            isTripod = true,
        )
        val thermalPolicy = ThermalDegradationPolicy.forThermalState(DeviceThermalState.SEVERE)

        val plan = planner.planExposure(
            luminance = 10f,
            stability = stability,
            thermalPolicy = thermalPolicy,
        )

        assertTrue(plan.frameCount <= 4)
        assertFalse(plan.enableChromaCleanup) // Heavy filter disabled under severe thermals
    }
}
