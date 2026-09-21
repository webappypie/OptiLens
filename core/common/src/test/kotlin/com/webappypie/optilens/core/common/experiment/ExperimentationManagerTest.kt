package com.webappypie.optilens.core.common.experiment

import com.webappypie.optilens.core.common.config.LocalRemoteConfigRepository
import com.webappypie.optilens.core.common.feature.CustomFeatureFlags
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExperimentationManagerTest {

    @Test
    fun `default experiments match LocalFeatureFlags conservative values`() = runTest {
        val repo = LocalRemoteConfigRepository()
        val manager = ExperimentationManager(repo)

        val snapshot = manager.currentSnapshot()
        assertEquals("control", snapshot.cohort)
        assertEquals(OnboardingVariant.CONTROL, snapshot.onboardingVariant)
        assertEquals(WatermarkExperiment.OFF_BY_DEFAULT, snapshot.watermarkExperiment)
        assertEquals(0.5f, snapshot.aiEnhanceDefaultSplit, 0.001f)
        assertEquals(5, snapshot.reviewTriggerThreshold)
    }

    @Test
    fun `remote config updates propagate through experimentation stream`() = runTest {
        val repo = LocalRemoteConfigRepository()
        val manager = ExperimentationManager(repo)

        repo.updateFlags(
            CustomFeatureFlags(
                experimentCohort = "variant_b",
                onboardingVariant = "feature_focus",
                enableWatermarkByDefault = true,
                aiEnhanceDefaultSplit = 0.7f,
                reviewTriggerThreshold = 7,
            )
        )

        val updated = manager.activeExperiments.first()
        assertEquals("variant_b", updated.cohort)
        assertEquals(OnboardingVariant.FEATURE_FOCUS, updated.onboardingVariant)
        assertEquals(WatermarkExperiment.ON_BY_DEFAULT, updated.watermarkExperiment)
        assertEquals(0.7f, updated.aiEnhanceDefaultSplit, 0.001f)
        assertEquals(7, updated.reviewTriggerThreshold)
    }

    @Test
    fun `deterministic cohort allocation produces consistent non-empty assignments`() {
        val repo = LocalRemoteConfigRepository()
        val manager = ExperimentationManager(repo)

        val cohort1 = manager.assignCohortDeterministically("user-device-seed-12345")
        val cohort2 = manager.assignCohortDeterministically("user-device-seed-12345")
        assertEquals(cohort1, cohort2)
        assertTrue(cohort1 in listOf("control", "variant_a", "variant_b"))
    }
}
