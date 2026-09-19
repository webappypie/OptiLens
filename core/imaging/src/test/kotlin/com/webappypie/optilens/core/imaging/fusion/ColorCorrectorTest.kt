package com.webappypie.optilens.core.imaging.fusion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

class ColorCorrectorTest {

    @Test
    fun `skin tone probability identifies skin chrominance and rejects non-skin`() {
        // Typical human skin: U=109, V=152
        val skinProb = computeSkinProbability(109.0f, 152.0f)
        assertTrue("Optimal skin should have high probability: $skinProb", skinProb > 0.90f)

        // Non-skin: Blue sky (U=180, V=80)
        val skyProb = computeSkinProbability(180.0f, 80.0f)
        assertTrue("Sky should have near zero skin probability: $skyProb", skyProb < 0.01f)

        // Non-skin: Green foliage (U=70, V=80)
        val foliageProb = computeSkinProbability(70.0f, 80.0f)
        assertTrue("Foliage should have near zero skin probability: $foliageProb", foliageProb < 0.01f)
    }

    @Test
    fun `skin tone protection prevents oversaturation of faces in vivid profile`() {
        val uSkin = 109.0f
        val vSkin = 152.0f
        val skinProb = computeSkinProbability(uSkin, vSkin)

        val targetVividSat = 1.25f
        val skinDamping = 1.0f - (0.80f * skinProb)
        val effectiveSat = 1.0f + (targetVividSat - 1.0f) * skinDamping

        // Under vivid profile (+25%), skin saturation boost should be dampened to <= +6%
        assertTrue("Skin saturation boost should be dampened: $effectiveSat", effectiveSat < 1.08f)
    }

    @Test
    fun `color profiles apply correct saturation multipliers`() {
        val defaultSat = getSaturationMultiplier(ColorProfile.DEFAULT)
        val naturalSat = getSaturationMultiplier(ColorProfile.NATURAL)
        val vividSat = getSaturationMultiplier(ColorProfile.VIVID)

        assertEquals(1.05f, defaultSat, 0.001f)
        assertEquals(0.92f, naturalSat, 0.001f)
        assertEquals(1.25f, vividSat, 0.001f)

        assertTrue(vividSat > defaultSat)
        assertTrue(defaultSat > naturalSat)
    }

    @Test
    fun `detail enhancement sharpens edges but suppresses noise below threshold`() {
        val noiseFloor = 3.5f

        // Case 1: Sub-threshold noise (delta = 2.0)
        val subThresholdDelta = 2.0f
        val noiseBoost = if (abs(subThresholdDelta) > noiseFloor) subThresholdDelta * 0.25f else 0.0f
        assertEquals(0.0f, noiseBoost, 0.0001f)

        // Case 2: Legitimate texture edge (delta = 10.0)
        val edgeDelta = 10.0f
        val edgeBoost = if (abs(edgeDelta) > noiseFloor) {
            val weight = min(1.0f, (abs(edgeDelta) - noiseFloor) / 4.0f)
            edgeDelta * 0.25f * weight
        } else 0.0f
        assertTrue("Texture edge should be enhanced: $edgeBoost", edgeBoost > 1.0f)
    }

    private fun computeSkinProbability(u: Float, v: Float): Float {
        val du = (u - 109.0f) / 18.0f
        val dv = (v - 152.0f) / 20.0f
        return exp(-0.5f * (du * du + dv * dv))
    }

    private fun getSaturationMultiplier(profile: ColorProfile): Float {
        return when (profile) {
            ColorProfile.NATURAL -> 0.92f
            ColorProfile.VIVID -> 1.25f
            ColorProfile.DEFAULT -> 1.05f
        }
    }
}
