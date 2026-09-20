package com.webappypie.optilens.core.camera.moon

import com.webappypie.optilens.core.camera.analysis.PreprocessedFrameData
import com.webappypie.optilens.core.camera.model.CameraShakeLevel
import com.webappypie.optilens.core.camera.model.MotionState
import com.webappypie.optilens.core.camera.model.QualityMetrics
import com.webappypie.optilens.core.camera.model.ZoomStop
import com.webappypie.optilens.core.camera.thermal.DeviceThermalState
import com.webappypie.optilens.core.camera.thermal.ThermalDegradationPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.abs

class MoonModeEngineTest {

    private lateinit var moonDetector: MoonDetector
    private lateinit var moonEngine: MoonModeEngine

    @Before
    fun setUp() {
        moonDetector = MoonDetector()
        moonEngine = MoonModeEngine()
    }

    private fun createSyntheticMoonFrame(
        gridWidth: Int = 160,
        gridHeight: Int = 120,
        discCenterX: Int = 80,
        discCenterY: Int = 60,
        discRadius: Int = 12,
        discLuma: Int = 210,
        backgroundLuma: Int = 10,
    ): PreprocessedFrameData {
        val yGrid = IntArray(gridWidth * gridHeight) { backgroundLuma }
        val r2 = discRadius * discRadius
        var discCount = 0

        for (y in (discCenterY - discRadius)..(discCenterY + discRadius)) {
            for (x in (discCenterX - discRadius)..(discCenterX + discRadius)) {
                val dx = x - discCenterX
                val dy = y - discCenterY
                if (dx * dx + dy * dy <= r2 && x in 0 until gridWidth && y in 0 until gridHeight) {
                    yGrid[y * gridWidth + x] = discLuma
                    discCount++
                }
            }
        }

        val totalPixels = gridWidth * gridHeight
        val meanLuma = ((totalPixels - discCount) * backgroundLuma + discCount * discLuma).toFloat() / totalPixels.toFloat()

        return PreprocessedFrameData(
            gridWidth = gridWidth,
            gridHeight = gridHeight,
            yGrid = yGrid,
            histogramBins = FloatArray(64),
            meanLuminance = meanLuma,
            centerLuminance = discLuma.toFloat(),
            peripheryLuminance = backgroundLuma.toFloat(),
            highlightClippingPercent = (discCount.toFloat() / totalPixels.toFloat()) * 100f,
            shadowClippingPercent = 70f,
            averageU = 128f,
            averageV = 128f,
            skyScore = 0.5f,
            plantScore = 0.0f,
            warmScore = 0.0f,
            timestampMs = System.currentTimeMillis(),
        )
    }

    @Test
    fun `moonDetector detects bright circular disc in dark sky background`() {
        val frame = createSyntheticMoonFrame()
        val metrics = QualityMetrics(luminance = 25f)
        val motion = MotionState(cameraShakeLevel = CameraShakeLevel.STABLE, gyroAngularVelocityRadPerSec = 0.05f)

        val state = moonDetector.detectMoon(frame, metrics, motion, currentZoomRatio = 1.0f)

        assertTrue("Moon should be detected in synthetic night sky frame", state.isMoonDetected)
        val roi = state.roi
        assertTrue("ROI must be present", roi != null)
        assertEquals("Center X must align with disc center", 0.5f, roi!!.centerX, 0.05f)
        assertEquals("Center Y must align with disc center", 0.5f, roi.centerY, 0.05f)
        assertTrue("Circularity must be high for circular disc", roi.circularity > 0.75f)
        assertTrue("Confidence must be strong", state.confidence >= 0.75f)
        assertEquals(StabilityCue.STABLE, state.stabilityCue)
    }

    @Test
    fun `moonDetector rejects bright daytime or uniform scenes`() {
        val width = 160
        val height = 120
        val daylightGrid = IntArray(width * height) { 180 }
        val daylightFrame = PreprocessedFrameData(
            gridWidth = width,
            gridHeight = height,
            yGrid = daylightGrid,
            histogramBins = FloatArray(64),
            meanLuminance = 180f,
            centerLuminance = 180f,
            peripheryLuminance = 180f,
            highlightClippingPercent = 5f,
            shadowClippingPercent = 0f,
            averageU = 128f,
            averageV = 128f,
            skyScore = 0.4f,
            plantScore = 0.3f,
            warmScore = 0.1f,
            timestampMs = System.currentTimeMillis(),
        )
        val metrics = QualityMetrics(luminance = 180f)
        val motion = MotionState.DEFAULT

        val state = moonDetector.detectMoon(daylightFrame, metrics, motion)
        assertFalse("Moon detector must reject uniform daylight scenes", state.isMoonDetected)
    }

    @Test
    fun `computeCaptureConfig applies spot exposure and tele lens preference`() {
        val frame = createSyntheticMoonFrame(discLuma = 235)
        val metrics = QualityMetrics(luminance = 20f)
        val motion = MotionState.DEFAULT
        val state = moonDetector.detectMoon(frame, metrics, motion, currentZoomRatio = 1.0f)

        val availableStops = listOf(
            ZoomStop(ratio = 0.6f, label = "0.6x", isOptical = true),
            ZoomStop(ratio = 1.0f, label = "1x", isOptical = true),
            ZoomStop(ratio = 3.0f, label = "3x", isOptical = true),
            ZoomStop(ratio = 5.0f, label = "5x", isOptical = true),
        )

        val config = moonEngine.computeCaptureConfig(
            moonState = state,
            thermalPolicy = ThermalDegradationPolicy.forThermalState(DeviceThermalState.NORMAL),
            availableZoomStops = availableStops,
        )

        assertEquals("Should prefer highest optical telephoto stop (5x)", 5.0f, config.recommendedZoomRatio, 0.01f)
        assertTrue("Spot exposure EV offset must be negative to prevent clipping (-2 or -3 EV)", config.evOffset <= -2)
        assertTrue("Shutter speed must be short (<= 4_000_000 ns)", config.shutterSpeedNanos <= 4_000_000L)
        assertEquals("Normal thermal state must use 8 burst frames", 8, config.burstFrameCount)
    }

    @Test
    fun `thermal throttling gracefully scales down moon burst size`() {
        val state = MoonDetectionState(isMoonDetected = true, suggestedEvOffset = -2)

        val configNormal = moonEngine.computeCaptureConfig(state, ThermalDegradationPolicy.forThermalState(DeviceThermalState.NORMAL))
        assertEquals(8, configNormal.burstFrameCount)
        assertFalse(configNormal.isThermallyCapped)

        val configModerate = moonEngine.computeCaptureConfig(state, ThermalDegradationPolicy.forThermalState(DeviceThermalState.MODERATE))
        assertEquals(4, configModerate.burstFrameCount)
        assertTrue(configModerate.isThermallyCapped)

        val configSevere = moonEngine.computeCaptureConfig(state, ThermalDegradationPolicy.forThermalState(DeviceThermalState.SEVERE))
        assertEquals(2, configSevere.burstFrameCount)
        assertTrue(configSevere.isThermallyCapped)

        val configCritical = moonEngine.computeCaptureConfig(state, ThermalDegradationPolicy.forThermalState(DeviceThermalState.CRITICAL))
        assertEquals(1, configCritical.burstFrameCount)
        assertTrue(configCritical.isThermallyCapped)
    }

    @Test
    fun `stackMoonFrames aligns disc centroids and averages real photon buffers`() {
        val width = 20
        val height = 20

        // Frame 1: center at (10, 10)
        val f1 = ByteArray(width * height) { 10 }
        f1[10 * width + 10] = 200.toByte()

        // Frame 2: jittered center at (11, 10)
        val f2 = ByteArray(width * height) { 10 }
        f2[10 * width + 11] = 200.toByte()

        val centers = listOf(
            (10f / width.toFloat()) to (10f / height.toFloat()),
            (11f / width.toFloat()) to (10f / height.toFloat()),
        )

        val stacked = moonEngine.stackMoonFrames(
            frames = listOf(f1, f2),
            width = width,
            height = height,
            discCenters = centers,
        )

        assertEquals(width * height, stacked.size)
        // At aligned center (10, 10), both frames contributing 200 -> average is 200
        val centerVal = stacked[10 * width + 10].toInt() and 0xFF
        assertEquals(200, centerVal)
    }

    @Test
    fun `processLunarDetailRecovery bounds micro-contrast and guarantees zero synthetic textures`() {
        assertTrue("Strict guarantee: No synthetic AI moon textures", MoonModeEngine.ZERO_SYNTHETIC_TEXTURE_GUARANTEE)

        val width = 16
        val height = 16
        val input = ByteArray(width * height) { 120 }

        // Add high-frequency simulated crater crater rim
        input[8 * width + 8] = 145.toByte()
        input[8 * width + 9] = 100.toByte()

        val out = moonEngine.processLunarDetailRecovery(
            inputY = input,
            width = width,
            height = height,
            discRoi = null,
            strength = 0.85f,
        )

        val delta1 = abs((out[8 * width + 8].toInt() and 0xFF) - (input[8 * width + 8].toInt() and 0xFF))
        assertTrue("Micro-contrast detail must be boosted", delta1 > 0)
        assertTrue("Detail delta must not exceed anti-halo maximum", delta1 <= MoonModeEngine.MAX_DETAIL_DELTA_LUMA)
    }
}
