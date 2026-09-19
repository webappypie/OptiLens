package com.webappypie.optilens.core.imaging.fusion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

class ChromaCleanupTest {

    private fun computeSkinProbability(u: Float, v: Float): Float {
        val uMean = 109.0f
        val vMean = 152.0f
        val uSigma = 14.0f
        val vSigma = 18.0f

        val du = (u - uMean) / uSigma
        val dv = (v - vMean) / vSigma
        val d2 = du * du + dv * dv
        return exp(-0.5f * d2)
    }

    private fun computeChromaCleanupWeight(y: Float, skinProb: Float): Float {
        if (y >= 85.0f || skinProb > 0.30f) return 0.0f
        return (1.0f - (y / 85.0f)).coerceIn(0.0f, 1.0f)
    }

    private fun computeSharpeningLuminanceWeight(y: Float, isConservativeNightMode: Boolean): Float {
        if (!isConservativeNightMode) return 1.0f
        return when {
            y <= 35.0f -> 0.0f
            y >= 60.0f -> 1.0f
            else -> (y - 35.0f) / 25.0f
        }
    }

    private fun applyNightHighlightProtection(luminance: Float, knee: Float = 0.65f): Float {
        if (luminance <= knee) return luminance
        val overshoot = luminance - knee
        val compressed = knee + (1.0f - knee) * (ln(1.0f + 2.5f * overshoot) / ln(1.0f + 2.5f * (1.0f - knee)))
        return compressed.coerceIn(0.0f, 1.0f)
    }

    @Test
    fun chromaCleanup_activatesInShadowsAndSuppressesNoise() {
        val darkShadowLuminance = 25.0f
        val skinProb = computeSkinProbability(128.0f, 128.0f) // Neutral gray shadow

        val weight = computeChromaCleanupWeight(darkShadowLuminance, skinProb)

        assertTrue("Cleanup weight should be strong in dark shadows: $weight", weight > 0.60f)
    }

    @Test
    fun chromaCleanup_disablesInMidtonesAndHighlights() {
        val midtoneLuminance = 110.0f
        val skinProb = 0.0f

        val weight = computeChromaCleanupWeight(midtoneLuminance, skinProb)

        assertEquals("Cleanup must be zero in mid-tones to avoid color bleeding", 0.0f, weight, 0.0001f)
    }

    @Test
    fun chromaCleanup_preservesSkinTonesInLowLight() {
        val shadowLuminance = 45.0f
        // Skin chrominance
        val skinProb = computeSkinProbability(109.0f, 152.0f)
        assertTrue(skinProb > 0.80f)

        val weight = computeChromaCleanupWeight(shadowLuminance, skinProb)

        assertEquals("Chroma cleanup must be disabled on faces to prevent desaturation", 0.0f, weight, 0.0001f)
    }

    @Test
    fun conservativeSharpening_mutesShadowNoiseAmplification() {
        val deepShadowY = 20.0f
        val midtoneY = 50.0f
        val highlightY = 90.0f

        val shadowWeight = computeSharpeningLuminanceWeight(deepShadowY, isConservativeNightMode = true)
        val midtoneWeight = computeSharpeningLuminanceWeight(midtoneY, isConservativeNightMode = true)
        val highlightWeight = computeSharpeningLuminanceWeight(highlightY, isConservativeNightMode = true)

        assertEquals("Deep shadows should have zero sharpness boost", 0.0f, shadowWeight, 0.0001f)
        assertTrue("Mid-tones should have partial sharpness ramp: $midtoneWeight", midtoneWeight in 0.5f..0.7f)
        assertEquals("Highlights should have full sharpness boost", 1.0f, highlightWeight, 0.0001f)
    }

    @Test
    fun highlightProtection_compressesNeonAndLampsGracefully() {
        val normalHigh = 0.60f
        val intenseNeon = 0.95f

        val protectedNormal = applyNightHighlightProtection(normalHigh)
        val protectedNeon = applyNightHighlightProtection(intenseNeon)

        assertEquals("Below knee, luminance should be preserved linearly", normalHigh, protectedNormal, 0.0001f)
        assertTrue("Intense neon should be smoothly compressed below 1.0: $protectedNeon", protectedNeon < 0.98f && protectedNeon > 0.65f)
    }
}
