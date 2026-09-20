package com.webappypie.optilens.core.camera.specialized

import com.webappypie.optilens.core.camera.model.MotionState
import com.webappypie.optilens.core.camera.model.QualityMetrics
import com.webappypie.optilens.core.camera.model.SubjectMotionLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PetModeEngineTest {

    private lateinit var petEngine: PetModeEngine

    @Before
    fun setUp() {
        petEngine = PetModeEngine()
    }

    @Test
    fun `static pet scene enforces minimum 1 over 250s shutter speed`() {
        val staticQuality = QualityMetrics(luminance = 90f)
        val staticMotion = MotionState(
            subjectMotionLevel = SubjectMotionLevel.STATIC,
            subjectMotionScore = 0.05f
        )

        val config = petEngine.computeCaptureConfig(staticQuality, staticMotion)

        assertFalse(config.subjectMotionDetected)
        // 4_000_000 ns = 1/250s
        assertEquals(PetModeEngine.MIN_SHUTTER_NANOS_STATIC, config.targetShutterSpeedNanos)
        assertTrue("Shutter speed must be at least 1/250s (<= 4_000_000 ns)", config.targetShutterSpeedNanos <= 4_000_000L)
    }

    @Test
    fun `active pet motion accelerates shutter speed to 1 over 500s or 1 over 1000s`() {
        val activeMotion = MotionState(
            subjectMotionLevel = SubjectMotionLevel.HIGH_MOTION,
            subjectMotionScore = 0.65f
        )

        // Moderate light + high motion -> 1/500s (2_000_000 ns)
        val moderateLightQuality = QualityMetrics(luminance = 80f)
        val configModerate = petEngine.computeCaptureConfig(moderateLightQuality, activeMotion)
        assertTrue(configModerate.subjectMotionDetected)
        assertEquals(PetModeEngine.TARGET_SHUTTER_NANOS_MOTION, configModerate.targetShutterSpeedNanos)

        // Bright daylight + high motion -> 1/1000s (1_000_000 ns)
        val brightQuality = QualityMetrics(luminance = 150f)
        val configBright = petEngine.computeCaptureConfig(brightQuality, activeMotion)
        assertTrue(configBright.subjectMotionDetected)
        assertEquals(PetModeEngine.AGGRESSIVE_SHUTTER_NANOS, configBright.targetShutterSpeedNanos)
    }

    @Test
    fun `fur detail filter enhances micro contrast on fine textures without touching flat regions`() {
        val width = 16
        val height = 16
        val input = ByteArray(width * height) { 100.toByte() }

        // Inject fine fur variance around pixel (5, 5) -> variance in 6..55
        // e.g. center = 115, neighbors = 100
        val centerIdx = 5 * width + 5
        input[centerIdx] = 115.toByte()

        val output = petEngine.processFurDetails(input, width, height, strength = 1.0f)

        // Flat pixel (10, 10) has 0 variance, should remain exactly 100
        val flatIdx = 10 * width + 10
        assertEquals(100.toByte(), output[flatIdx])

        // Fur pixel (5, 5) detail should be boosted (115 boosted above 115)
        val boostedVal = output[centerIdx].toInt() and 0xFF
        assertTrue("Fur micro-contrast must boost detail above original value ($boostedVal > 115)", boostedVal > 115)
    }
}
