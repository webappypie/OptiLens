package com.webappypie.optilens.core.camera.specialized

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.sqrt

class FoodModeEngineTest {

    private lateinit var foodEngine: FoodModeEngine

    @Before
    fun setUp() {
        foodEngine = FoodModeEngine()
    }

    @Test
    fun `white balance stabilizes towards warm-neutral 5200K target`() {
        // Very cool input (e.g. 7000K fluorescent)
        val configCoolInput = foodEngine.getStabilizedWhiteBalance(7000)
        // Coerced to max 5600 and smoothed from 5200: (0.85 * 5200) + (0.15 * 5600) = 4420 + 840 = 5260
        assertTrue("Kelvin should remain within 5000K..5400K appetizing range", configCoolInput.targetKelvin in 5000..5400)

        // Very warm input (e.g. 3000K candle)
        val configWarmInput = foodEngine.getStabilizedWhiteBalance(3000)
        assertTrue("Kelvin should remain within 5000K..5400K appetizing range", configWarmInput.targetKelvin in 5000..5400)
    }

    @Test
    fun `restrained saturation enhances mid-chroma without radioactive clipping`() {
        val size = 4
        // U and V centered at 128 (neutral gray)
        val u = ByteArray(size) { 128.toByte() }
        val v = ByteArray(size) { 128.toByte() }

        // Inject moderate chroma: u = 128 + 30 = 158, v = 128 + 40 = 168 (chromaMag = 50)
        u[1] = 158.toByte()
        v[1] = 168.toByte()

        // Inject extreme chroma: u = 128 + 90 = 218, v = 128 + 90 = 218 (chromaMag = 127)
        u[2] = 218.toByte()
        v[2] = 218.toByte()

        val (outU, outV) = foodEngine.processRestrainedSaturation(u, v, 2, 2)

        // Neutral gray remains neutral
        assertEquals(128.toByte(), outU[0])
        assertEquals(128.toByte(), outV[0])

        // Moderate chroma boosted by ~1.12
        val modU = (outU[1].toInt() and 0xFF) - 128
        val modV = (outV[1].toInt() and 0xFF) - 128
        val modChroma = sqrt((modU * modU + modV * modV).toDouble())
        assertTrue("Moderate chroma ($modChroma) should be boosted above 50", modChroma > 50.0)

        // Saturated peak compressed by soft-knee roll-off
        val extU = (outU[2].toInt() and 0xFF) - 128
        val extV = (outV[2].toInt() and 0xFF) - 128
        val extChroma = sqrt((extU * extU + extV * extV).toDouble())
        assertTrue("Extreme chroma ($extChroma) must be softly compressed <= 115", extChroma <= 115.0)
    }

    @Test
    fun `controlled local contrast clamps haloing strictly within limit`() {
        val width = 8
        val height = 8
        val input = ByteArray(width * height) { 100.toByte() }

        // Inject a stark edge at center
        val centerIdx = 4 * width + 4
        input[centerIdx] = 220.toByte()

        val limit = 16
        val output = foodEngine.processControlledLocalContrast(input, width, height, maxBoost = limit)

        val boostedLuma = output[centerIdx].toInt() and 0xFF
        val delta = boostedLuma - 220
        assertTrue("Boost delta ($delta) must not exceed localContrastLimit ($limit)", delta <= limit)
    }
}
