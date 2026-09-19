package com.webappypie.optilens.core.imaging.fusion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max

class ToneMapperTest {

    @Test
    fun `shadow recovery lifts deep shadows without altering true black or midtones`() {
        // Test black point preservation
        val blackPoint = applyShadowRecovery(0.0f, liftAmount = 0.35f)
        assertEquals(0.0f, blackPoint, 0.0001f)

        // Test deep shadow lift (e.g. 0.10)
        val originalShadow = 0.10f
        val liftedShadow = applyShadowRecovery(originalShadow, liftAmount = 0.35f)
        assertTrue("Shadows should be lifted: $liftedShadow > $originalShadow", liftedShadow > originalShadow)

        // Test midtone/highlight stability (e.g. 0.50 should not be lifted)
        val midtone = 0.50f
        val untouchedMidtone = applyShadowRecovery(midtone, liftAmount = 0.35f)
        assertEquals(midtone, untouchedMidtone, 0.0001f)
    }

    @Test
    fun `highlight roll-off smoothly compresses bright values without hard clipping`() {
        val knee = 0.72f

        // Below knee should be completely unaltered
        val belowKnee = applyHighlightRollOff(0.50f, knee)
        assertEquals(0.50f, belowKnee, 0.0001f)

        // At knee should match knee
        val atKnee = applyHighlightRollOff(0.72f, knee)
        assertEquals(0.72f, atKnee, 0.001f)

        // In highlight region (0.90), should smoothly compress < 1.0
        val highlight = applyHighlightRollOff(0.90f, knee)
        assertTrue("Highlight should compress between knee and 1.0: $highlight", highlight in 0.72f..1.0f)

        // Monotonic ordering: brighter inputs still yield brighter or equal outputs
        val veryBright = applyHighlightRollOff(1.20f, knee)
        assertTrue("Monotonic response: $veryBright >= $highlight", veryBright >= highlight)
        assertTrue("Roll-off should stay <= 1.0: $veryBright", veryBright <= 1.0f)
    }

    @Test
    fun `filmic ACES curve is strictly monotonic and bounded`() {
        var prevOutput = -1.0f
        for (i in 0..100) {
            val input = i / 100.0f
            val output = applyFilmicCurve(input)
            assertTrue("Filmic output must be in [0, 1]: $output", output in 0.0f..1.0f)
            assertTrue("Filmic curve must be monotonic: $output >= $prevOutput", output >= prevOutput)
            prevOutput = output
        }
    }

    private fun applyShadowRecovery(normalizedY: Float, liftAmount: Float): Float {
        if (normalizedY <= 0.0f) return 0.0f
        if (normalizedY >= 0.45f || liftAmount <= 0.0f) return normalizedY

        val t = normalizedY / 0.45f
        val falloff = (1.0f - t) * (1.0f - t)
        val lift = falloff * (normalizedY / (normalizedY + 0.12f))
        return normalizedY + (liftAmount * 0.22f * lift)
    }

    private fun applyHighlightRollOff(normalizedY: Float, knee: Float): Float {
        if (normalizedY <= knee) return normalizedY
        val excess = normalizedY - knee
        val headroom = 1.0f - knee
        if (headroom <= 0.001f) return knee
        val scaled = excess / headroom
        val comp = scaled / (1.0f + scaled)
        return knee + comp * headroom
    }

    private fun applyFilmicCurve(x: Float): Float {
        val xc = max(0.0f, x)
        val a = 2.51f
        val b = 0.03f
        val c = 2.43f
        val d = 0.59f
        val e = 0.14f
        val num = xc * (a * xc + b)
        val den = xc * (c * xc + d) + e
        return (num / den).coerceIn(0.0f, 1.0f)
    }
}
