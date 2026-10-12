package com.webappypie.optilens.core.imaging.enhance

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

class LocalEnhancementPipelineTest {

    private fun rgb(r: Int, g: Int, b: Int) =
        -0x1000000 or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

    private fun r(p: Int) = (p shr 16) and 0xFF
    private fun g(p: Int) = (p shr 8) and 0xFF
    private fun b(p: Int) = p and 0xFF
    private fun luma(p: Int) = 0.299f * r(p) + 0.587f * g(p) + 0.114f * b(p)

    private fun image(w: Int, h: Int, fn: (Int, Int) -> Int) =
        ArgbImage(w, h, IntArray(w * h) { fn(it % w, it / w) })

    private fun enhance(
        src: ArgbImage,
        config: EnhancementConfig = EnhancementConfig.BALANCED,
        workers: Int = 2,
    ): Pair<ArgbImage, EnhancementReport> = runBlocking {
        val out = ArgbImage(src.width, src.height)
        val report = withContext(Dispatchers.Default) {
            LocalEnhancementPipeline(config).process(src, out, workers)
        }
        out to report
    }

    /** Natural-looking test scene: smooth illumination, a few objects and texture. */
    private fun scene(w: Int, h: Int, brightness: Float, seed: Int = 1, noise: Float = 0f): ArgbImage {
        val rnd = Random(seed)
        return image(w, h) { x, y ->
            val illum = 0.5f + 0.5f * (x.toFloat() / w)
            val obj = if ((x / 40 + y / 40) % 3 == 0) 0.6f else 1f
            val tex = 1f + 0.08f * kotlin.math.sin(x * 0.7f) * kotlin.math.cos(y * 0.5f)
            val base = 200f * brightness * illum * obj * tex
            fun n() = if (noise > 0f) (rnd.nextFloat() - 0.5f) * 2f * noise else 0f
            rgb((base * 1.05f + n()).toInt(), (base + n()).toInt(), (base * 0.9f + n()).toInt())
        }
    }

    @Test
    fun `output keeps dimensions and is fully opaque`() {
        val (out, report) = enhance(scene(301, 157, 0.6f))
        assertEquals(301, out.width)
        assertEquals(157, out.height)
        assertEquals(301, report.width)
        assertTrue(out.pixels.all { (it ushr 24) == 0xFF })
    }

    @Test
    fun `neutral grey stays neutral`() {
        val src = image(200, 120) { x, _ -> val v = 30 + x; rgb(v, v, v) }
        val (out, _) = enhance(src)
        for (p in out.pixels) {
            assertTrue("hue shift in $p", abs(r(p) - g(p)) <= 1 && abs(g(p) - b(p)) <= 1)
        }
    }

    @Test
    fun `underexposed photo is brightened but not flattened to grey`() {
        val src = scene(400, 300, 0.18f)
        val (out, report) = enhance(src)
        val before = src.pixels.map { luma(it) }.average()
        val after = out.pixels.map { luma(it) }.average()
        assertTrue("exposure should increase, got ${report.exposureStops}", report.exposureStops > 0.3f)
        assertTrue("mean luma $before -> $after", after > before * 1.3)
        assertTrue("must not exceed a natural mid-tone level: $after", after < 170)
        // Darkest objects stay darker than the brightest ones (no flattening).
        val sorted = out.pixels.map { luma(it) }.sorted()
        val spread = sorted[sorted.size * 95 / 100] - sorted[sorted.size * 5 / 100]
        val srcSorted = src.pixels.map { luma(it) }.sorted()
        val srcSpread = srcSorted[srcSorted.size * 95 / 100] - srcSorted[srcSorted.size * 5 / 100]
        assertTrue("tonal spread $srcSpread -> $spread", spread > srcSpread)
    }

    @Test
    fun `well exposed clean photo changes only slightly`() {
        val src = scene(400, 300, 0.75f)
        val (out, _) = enhance(src)
        var diff = 0L
        for (i in src.pixels.indices) diff += abs(luma(src.pixels[i]) - luma(out.pixels[i])).toLong()
        val meanDiff = diff.toDouble() / src.pixels.size
        assertTrue("mean change $meanDiff should be small", meanDiff < 14.0)
    }

    @Test
    fun `clipped whites stay white and black stays black`() {
        val src = image(300, 200) { x, _ ->
            when {
                x < 60 -> rgb(0, 0, 0)
                x > 240 -> rgb(255, 255, 255)
                else -> { val v = 20 + x / 3; rgb(v, v, v) }
            }
        }
        val (out, report) = enhance(src)
        for (y in 10 until 190) {
            assertEquals(rgb(255, 255, 255), out.pixels[y * 300 + 280])
            assertEquals(rgb(0, 0, 0), out.pixels[y * 300 + 20])
        }
        assertTrue(report.clippedHighlightFraction in 0.18f..0.22f)
    }

    @Test
    fun `moderate colour cast is reduced`() {
        val src = scene(300, 200, 0.6f).let { s ->
            ArgbImage(s.width, s.height, IntArray(s.pixels.size) {
                val p = s.pixels[it]
                rgb((r(p) * 1.15f).toInt(), g(p), (b(p) * 0.85f).toInt())
            })
        }
        val (out, report) = enhance(src)
        assertTrue(report.whiteBalanceGainR < report.whiteBalanceGainB)
        fun rbRatio(img: ArgbImage) = img.pixels.sumOf { r(it).toDouble() } / img.pixels.sumOf { b(it).toDouble() }
        assertTrue("cast should shrink: ${rbRatio(src)} -> ${rbRatio(out)}", rbRatio(out) < rbRatio(src) - 0.05)
    }

    @Test
    fun `noisy dark photo gets measured noise and lower chroma noise`() {
        val rnd = Random(7)
        val src = image(320, 240) { _, _ ->
            val v = 40f
            rgb(
                (v + (rnd.nextFloat() - 0.5f) * 24).toInt(),
                (v + (rnd.nextFloat() - 0.5f) * 24).toInt(),
                (v + (rnd.nextFloat() - 0.5f) * 24).toInt(),
            )
        }
        val noNr = EnhancementConfig.BALANCED.copy(chromaNoiseReduction = 0f, lumaNoiseReduction = 0f, sharpening = 0f)
        val (plain, _) = enhance(src, noNr)
        val (denoised, report) = enhance(src)
        assertTrue("noise not detected: ${report.shadowNoiseSigma}", report.shadowNoiseSigma > 0.01f)
        assertTrue(report.lumaDenoiseRadius > 0)

        fun chromaStd(img: ArgbImage): Double {
            val v = img.pixels.map { (r(it) - g(it)).toDouble() }
            val m = v.average()
            return sqrt(v.sumOf { (it - m) * (it - m) } / v.size)
        }
        fun lumaStd(img: ArgbImage): Double {
            val v = img.pixels.map { luma(it).toDouble() }
            val m = v.average()
            return sqrt(v.sumOf { (it - m) * (it - m) } / v.size)
        }
        assertTrue("chroma ${chromaStd(plain)} -> ${chromaStd(denoised)}", chromaStd(denoised) < chromaStd(plain) * 0.7)
        assertTrue("luma ${lumaStd(plain)} -> ${lumaStd(denoised)}", lumaStd(denoised) < lumaStd(plain))
    }

    @Test
    fun `clean photo skips luminance denoise`() {
        val (_, report) = enhance(scene(300, 200, 0.6f))
        assertEquals(0, report.lumaDenoiseRadius)
    }

    @Test
    fun `sharp edge gets no halo overshoot`() {
        val src = image(200, 100) { x, _ -> if (x < 100) rgb(60, 60, 60) else rgb(180, 180, 180) }
        val (out, _) = enhance(src)
        val row = 50 * 200
        val dark = luma(out.pixels[row + 50])
        val bright = luma(out.pixels[row + 150])
        for (x in 90 until 110) {
            val v = luma(out.pixels[row + x])
            assertTrue("overshoot at $x: $v outside [$dark, $bright]", v >= dark - 3 && v <= bright + 3)
        }
    }

    @Test
    fun `strip parallelism does not change the result`() {
        val src = scene(257, 300, 0.3f, noise = 10f)
        val (one, _) = enhance(src, workers = 1)
        val (four, _) = enhance(src, workers = 4)
        assertArrayEquals(one.pixels, four.pixels)
    }

    @Test
    fun `progress is monotonic and reaches one`() = runBlocking {
        val src = scene(200, 400, 0.4f)
        val seen = mutableListOf<Float>()
        LocalEnhancementPipeline().process(src, ArgbImage(200, 400), 1) { synchronized(seen) { seen += it } }
        assertEquals(1f, seen.last(), 1e-4f)
        assertTrue(seen.zipWithNext().all { (a, b) -> b >= a })
    }

    @Test
    fun `cancellation stops processing`() {
        val src = scene(300, 2000, 0.4f)
        val job = Job()
        job.cancel()
        try {
            runBlocking(job) { LocalEnhancementPipeline().process(src, ArgbImage(300, 2000), 2) }
            fail("expected cancellation")
        } catch (_: CancellationException) {
        }
    }

    @Test
    fun `tiny images are handled`() {
        for ((w, h) in listOf(1 to 1, 2 to 3, 5 to 1)) {
            val (out, _) = enhance(image(w, h) { x, y -> rgb(40 + x * 10, 50 + y * 10, 60) })
            assertEquals(w * h, out.pixels.size)
        }
    }

    @Test
    fun `guided filter preserves a strong step`() {
        val w = 40
        val h = 10
        val guide = FloatArray(w * h) { if (it % w < 20) -6f else -1f }
        val (a, b) = LocalEnhancementPipeline.guidedCoefficients(guide, guide, w, h, 4, 0.25f)
        val q5 = a[5 * w + 5] * guide[5 * w + 5] + b[5 * w + 5]
        val q35 = a[5 * w + 35] * guide[5 * w + 35] + b[5 * w + 35]
        assertEquals(-6f, q5, 0.05f)
        assertEquals(-1f, q35, 0.05f)
    }
}
