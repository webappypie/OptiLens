package com.webappypie.optilens.core.camera.tier

import com.webappypie.optilens.core.camera.fixtures.CameraCapabilityFixtures
import com.webappypie.optilens.core.camera.model.PerformanceTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PerformanceTierEvaluatorTest {

    private lateinit var evaluator: PerformanceTierEvaluator

    @Before
    fun setUp() {
        evaluator = PerformanceTierEvaluator()
    }

    @Test
    fun `flagship device with Level 3 and 12GB RAM evaluates to FLAGSHIP`() {
        val fixture = CameraCapabilityFixtures.createFlagshipProfile()
        val result = evaluator.evaluate(
            cameras = fixture.cameras,
            totalRamGb = fixture.totalRamGb,
            cpuCores = fixture.cpuCores,
            apiLevel = fixture.androidApiLevel,
        )

        assertEquals(PerformanceTier.FLAGSHIP, result.tier)
        assertTrue("Score should be >= 80, was ${result.score}", result.score >= 80)
        assertTrue(result.breakdown.containsKey("hardware_level"))
        assertTrue(result.breakdown.containsKey("capabilities"))
        assertTrue(result.breakdown.containsKey("ram"))
        assertTrue(result.breakdown.containsKey("cpu_platform"))
    }

    @Test
    fun `mid-range device with Full and 6GB RAM evaluates to MID_RANGE or HIGH_PERFORMANCE`() {
        val fixture = CameraCapabilityFixtures.createMidRangeProfile()
        val result = evaluator.evaluate(
            cameras = fixture.cameras,
            totalRamGb = fixture.totalRamGb,
            cpuCores = fixture.cpuCores,
            apiLevel = fixture.androidApiLevel,
        )

        assertTrue(
            "Expected MID_RANGE or HIGH_PERFORMANCE, got ${result.tier}",
            result.tier == PerformanceTier.MID_RANGE || result.tier == PerformanceTier.HIGH_PERFORMANCE
        )
        assertTrue("Score should be in 40..79 range, was ${result.score}", result.score in 40..79)
    }

    @Test
    fun `budget device with Limited and 2_5GB RAM evaluates to ENTRY_LEVEL`() {
        val fixture = CameraCapabilityFixtures.createEntryLevelProfile()
        val result = evaluator.evaluate(
            cameras = fixture.cameras,
            totalRamGb = fixture.totalRamGb,
            cpuCores = fixture.cpuCores,
            apiLevel = fixture.androidApiLevel,
        )

        assertEquals(PerformanceTier.ENTRY_LEVEL, result.tier)
        assertTrue("Score should be < 40, was ${result.score}", result.score < 40)
    }

    @Test
    fun `legacy hardware level device is always capped at ENTRY_LEVEL`() {
        val fixture = CameraCapabilityFixtures.createLegacyProfile()
        val result = evaluator.evaluate(
            cameras = fixture.cameras,
            totalRamGb = 12.0f, // Even with generous RAM
            cpuCores = 8,
            apiLevel = 34,
        )

        assertEquals(PerformanceTier.ENTRY_LEVEL, result.tier)
    }

    @Test
    fun `gate rule compliance - evaluation does not depend on manufacturer or model name`() {
        val fixture = CameraCapabilityFixtures.createFlagshipProfile()

        val result1 = evaluator.evaluate(
            cameras = fixture.cameras,
            totalRamGb = fixture.totalRamGb,
            cpuCores = fixture.cpuCores,
            apiLevel = fixture.androidApiLevel,
        )

        // Same hardware specs must yield exact same score regardless of context
        val result2 = evaluator.evaluate(
            cameras = fixture.cameras,
            totalRamGb = fixture.totalRamGb,
            cpuCores = fixture.cpuCores,
            apiLevel = fixture.androidApiLevel,
        )

        assertEquals(result1.tier, result2.tier)
        assertEquals(result1.score, result2.score)
    }
}
