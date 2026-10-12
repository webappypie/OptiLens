package com.webappypie.optilens.core.imaging.enhance

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** Scene adaptation of [LocalEnhancementPipeline]: haze, highlights, faces. */
class SceneAdaptiveEnhancementTest {

    private fun rgb(r: Int, g: Int, b: Int) =
        -0x1000000 or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

    private fun luma(p: Int) = 0.299f * ((p shr 16) and 0xFF) + 0.587f * ((p shr 8) and 0xFF) + 0.114f * (p and 0xFF)

    private fun lin(v: Float) = LocalEnhancementPipeline.srgbDecode(v)
    private fun enc(v: Float) = (LocalEnhancementPipeline.srgbEncode(v.coerceIn(0f, 1f)) * 255f + 0.5f).toInt()

    private fun image(w: Int, h: Int, fn: (Int, Int) -> Int) =
        ArgbImage(w, h, IntArray(w * h) { fn(it % w, it / w) })

    private fun enhance(
        src: ArgbImage,
        config: EnhancementConfig = EnhancementConfig.BALANCED,
        hints: EnhancementHints = EnhancementHints.NONE,
    ): Pair<ArgbImage, EnhancementReport> = runBlocking {
        val out = ArgbImage(src.width, src.height)
        val report = withContext(Dispatchers.Default) { LocalEnhancementPipeline(config, hints).process(src, out, 2) }
        out to report
    }

    private fun lumaStd(img: ArgbImage, y0: Int, y1: Int): Double {
        val values = (y0 until y1).flatMap { y -> (0 until img.width).map { x -> luma(img.pixels[y * img.width + x]).toDouble() } }
        val mean = values.average()
        return sqrt(values.sumOf { (it - mean) * (it - mean) } / values.size)
    }

    /** Bright sky on top, textured distant hills below, seen through haze (scattering model, linear light). */
    private fun hazyLandscape(w: Int = 480, h: Int = 320, t: Float = 0.5f): ArgbImage {
        val air = floatArrayOf(0.80f, 0.82f, 0.86f)
        return image(w, h) { x, y ->
            if (y < h * 0.4f) {
                val v = 0.78f + 0.04f * y / h
                rgb(enc(v), enc(v * 1.02f), enc(v * 1.06f))
            } else {
                val tex = 0.5f + 0.5f * sin(x * 0.21f) * cos(y * 0.17f)
                val j = floatArrayOf(0.03f + 0.10f * tex, 0.06f + 0.14f * tex, 0.03f + 0.08f * tex)
                rgb(enc(j[0] * t + air[0] * (1 - t)), enc(j[1] * t + air[1] * (1 - t)), enc(j[2] * t + air[2] * (1 - t)))
            }
        }
    }

    @Test
    fun `haze over a distant landscape is measured and removed`() {
        val src = hazyLandscape()
        val (out, report) = enhance(src)
        val (noDehaze, _) = enhance(src, EnhancementConfig.BALANCED.copy(dehaze = 0f))
        assertEquals(EnhancementScene.LANDSCAPE, report.scene)
        assertTrue("dehaze strength ${report.dehazeStrength}", report.dehazeStrength > 0.2f)
        val ground = src.height * 5 / 10
        val withHaze = lumaStd(out, ground, src.height)
        val without = lumaStd(noDehaze, ground, src.height)
        assertTrue("distant contrast should increase: $without -> $withHaze", withHaze > without * 1.15)
        // The sky is protected: it is not darkened into a grey band.
        val skyBefore = (10 until 40).map { luma(src.pixels[it * src.width + 200]) }.average()
        val skyAfter = (10 until 40).map { luma(out.pixels[it * src.width + 200]) }.average()
        assertTrue("sky $skyBefore -> $skyAfter", skyAfter > skyBefore - 20)
    }

    @Test
    fun `dim low-contrast indoor scene is not dehazed`() {
        val rnd = Random(3)
        val src = image(320, 240) { x, y ->
            val v = 95 + (x + y) / 30 + rnd.nextInt(-3, 4)
            rgb(v, v - 2, v - 6)
        }
        val (_, report) = enhance(src)
        assertEquals(0f, report.dehazeStrength)
    }

    @Test
    fun `clean landscape without haze is left alone by dehaze`() {
        val src = hazyLandscape(t = 1f)
        val (_, report) = enhance(src)
        assertEquals(0f, report.dehazeStrength)
    }

    @Test
    fun `faces turn off dehaze and keep skin texture`() {
        val src = hazyLandscape()
        val face = FaceRegion(0.35f, 0.55f, 0.65f, 0.95f)
        val (withFace, report) = enhance(src, hints = EnhancementHints(listOf(face)))
        assertEquals(EnhancementScene.PORTRAIT, report.scene)
        assertEquals(1, report.facesProtected)
        assertEquals(0f, report.dehazeStrength)

        // Inside the face the local texture (difference from a 3x3 mean) must change less with
        // protection than without it (same config, no dehaze in either run).
        val (unprotected, _) = enhance(src, EnhancementConfig.BALANCED.copy(dehaze = 0f, protectFaces = false))
        fun texture(img: ArgbImage): Double {
            var sum = 0.0
            var n = 0
            for (y in (src.height * 0.65f).toInt() until (src.height * 0.85f).toInt()) {
                for (x in (src.width * 0.42f).toInt() until (src.width * 0.58f).toInt()) {
                    var m = 0f
                    for (dy in -1..1) for (dx in -1..1) m += luma(img.pixels[(y + dy) * img.width + x + dx])
                    sum += abs(luma(img.pixels[y * img.width + x]) - m / 9f); n++
                }
            }
            return sum / n
        }
        val original = texture(src)
        val protectedDelta = abs(texture(withFace) - original)
        val unprotectedDelta = abs(texture(unprotected) - original)
        assertTrue("face texture change $protectedDelta should be below $unprotectedDelta", protectedDelta < unprotectedDelta)
    }

    @Test
    fun `backlit face raises exposure`() {
        val w = 400
        val h = 300
        val face = FaceRegion(0.4f, 0.3f, 0.6f, 0.7f)
        val src = image(w, h) { x, y ->
            val inFace = x in 160..240 && y in 90..210
            val tex = (6 * sin(x * 0.5f) * cos(y * 0.4f)).toInt()
            if (inFace) rgb(70 + tex, 52 + tex, 42 + tex) else rgb(205 + tex, 210 + tex, 215 + tex)
        }
        val (_, plain) = enhance(src)
        val (_, metered) = enhance(src, hints = EnhancementHints(listOf(face)))
        assertTrue("face metering ${metered.exposureStops} vs ${plain.exposureStops}", metered.exposureStops > plain.exposureStops + 0.1f)
    }

    @Test
    fun `highlight recovery darkens bright areas but keeps white white and order intact`() {
        val w = 600
        val h = 200
        // Deep shadows, a smooth ramp into bright-but-unclipped sky, and a clipped sun.
        val src = image(w, h) { x, y ->
            val tex = (3 * sin(y * 0.6f)).toInt()
            when {
                x < 150 -> rgb(8 + x / 10 + tex, 8 + x / 10 + tex, 10 + x / 10 + tex)
                x >= 560 -> rgb(255, 255, 255)
                else -> { val v = 120 + (x - 150) * 130 / 410; rgb(v, v, v) }
            }
        }
        val (out, report) = enhance(src)
        val (flat, _) = enhance(src, EnhancementConfig.BALANCED.copy(highlightRecovery = 0f))
        assertTrue("highlight stops ${report.highlightRecoveryStops}", report.highlightRecoveryStops > 0f)
        val row = 100 * w
        val bright = (470 until 540).map { luma(out.pixels[row + it]) }.average()
        val brightFlat = (470 until 540).map { luma(flat.pixels[row + it]) }.average()
        assertTrue("bright area $brightFlat -> $bright", bright < brightFlat)
        for (x in 570 until w) assertEquals(rgb(255, 255, 255), out.pixels[row + x])
        // Monotonic along the ramp (small tolerance for rounding/sharpening).
        var prev = 0f
        for (x in 200 until 555) { // away from the hard shadow edge at x = 150
            val v = luma(out.pixels[row + x])
            assertTrue("tone order broken at x=$x: $prev -> $v", v >= prev - 2f)
            prev = maxOf(prev, v)
        }
    }

    @Test
    fun `neutral grey stays neutral with every stage enabled`() {
        val src = image(240, 160) { x, y -> val v = 40 + x * 200 / 240 + (y % 3); rgb(v, v, v) }
        val (out, _) = enhance(src, hints = EnhancementHints(listOf(FaceRegion(0.3f, 0.3f, 0.6f, 0.7f))))
        for (p in out.pixels) {
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            assertTrue("hue shift in $r,$g,$b", abs(r - g) <= 1 && abs(g - b) <= 1)
        }
    }
}
