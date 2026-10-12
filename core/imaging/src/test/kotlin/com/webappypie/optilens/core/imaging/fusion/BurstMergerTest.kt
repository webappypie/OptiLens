package com.webappypie.optilens.core.imaging.fusion

import com.webappypie.optilens.core.imaging.enhance.ArgbImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class BurstMergerTest {

    private val w = 320
    private val h = 240

    /** Textured test scene in sRGB 0..255 (float), sampled at any real coordinate. */
    private fun scene(x: Float, y: Float): Float {
        val base = 110f + 60f * sin(x * 0.11f) * cos(y * 0.07f) + 30f * sin((x + 2 * y) * 0.031f)
        val edge = if (((x / 37).toInt() + (y / 29).toInt()) % 2 == 0) 25f else -25f
        return (base + edge).coerceIn(5f, 245f)
    }

    private fun rgb(v: Int) = -0x1000000 or (v shl 16) or (v shl 8) or v

    private class TestFrame(val img: ArgbImage, override val exposureProduct: Double = 1.0) : BurstFrameSource {
        override val width = img.width
        override val height = img.height
        override fun readRows(y: Int, rows: Int, dst: IntArray) = img.readRows(y, rows, dst)
        override fun readDownsampled(factor: Int): ArgbImage {
            val dw = (width + factor - 1) / factor
            val dh = (height + factor - 1) / factor
            val out = IntArray(dw * dh)
            for (y in 0 until dh) for (x in 0 until dw) {
                var s = 0
                var n = 0
                for (j in 0 until factor) for (i in 0 until factor) {
                    val xx = x * factor + i
                    val yy = y * factor + j
                    if (xx < width && yy < height) { s += img.pixels[yy * width + xx] and 0xFF; n++ }
                }
                val v = s / n
                out[y * dw + x] = -0x1000000 or (v shl 16) or (v shl 8) or v
            }
            return ArgbImage(dw, dh, out)
        }
    }

    /** Frame showing the scene displaced by (dx, dy), with Gaussian noise and an optional gain. */
    private fun frame(dx: Float, dy: Float, noise: Float, seed: Int, gain: Float = 1f, blur: Int = 0,
                      overlay: ((Int, Int) -> Int?)? = null): ArgbImage {
        val rnd = Random(seed)
        val px = IntArray(w * h) { i ->
            val x = i % w
            val y = i / w
            overlay?.invoke(x, y)?.let { return@IntArray rgb(it) }
            var v = 0f
            var n = 0
            for (b in -blur..blur) { v += scene(x + dx + b, y + dy); n++ }
            v /= n
            val lin = (v / 255f).pow(2.2f) * gain
            val enc = lin.pow(1f / 2.2f) * 255f
            val g = (rnd.nextFloat() + rnd.nextFloat() + rnd.nextFloat() - 1.5f) * 2f * noise
            rgb((enc + g).toInt().coerceIn(0, 255))
        }
        return ArgbImage(w, h, px)
    }

    private fun rmse(a: IntArray, b: IntArray, x0: Int = 20, y0: Int = 20, x1: Int = w - 20, y1: Int = h - 20): Double {
        var s = 0.0
        var n = 0
        for (y in y0 until y1) for (x in x0 until x1) {
            val d = (a[y * w + x] and 0xFF) - (b[y * w + x] and 0xFF)
            s += d * d; n++
        }
        return sqrt(s / n)
    }

    private fun merge(frames: List<BurstFrameSource>): Pair<ArgbImage, BurstMergeReport> = runBlocking {
        val out = ArgbImage(w, h)
        val report = withContext(Dispatchers.Default) { BurstMerger().merge(frames, out, 2) }
        out to report
    }

    @Test
    fun `aligned merge of shifted noisy frames reduces noise`() {
        val shifts = listOf(0f to 0f, 3.4f to -2.2f, -5f to 4f, 1.5f to 6.3f, -2.7f to -3.1f, 6f to 1f)
        // The reference must be frame 0 for the comparison: give it the least noise-independent blur.
        val frames = shifts.mapIndexed { i, (dx, dy) -> TestFrame(frame(dx, dy, 14f, seed = i)) }
        val (out, report) = merge(frames)
        val ref = frames[report.referenceIndex].img
        val (rdx, rdy) = shifts[report.referenceIndex]
        val refTruth = IntArray(w * h) { rgb(scene(it % w + rdx, it / w + rdy).toInt()) }
        val single = rmse(ref.pixels, refTruth)
        val merged = rmse(out.pixels, refTruth)
        assertEquals(6, report.framesMerged)
        assertTrue("merged $merged vs single $single", merged < single * 0.65)
    }

    @Test
    fun `moving object is not ghosted`() {
        val square: (Int, Int) -> Int? = { x, y -> if (x in 150..190 && y in 100..140) 250 else null }
        val frames = listOf(
            TestFrame(frame(0f, 0f, 4f, seed = 1, overlay = square)),
            TestFrame(frame(0f, 0f, 4f, seed = 2)),
            TestFrame(frame(0f, 0f, 4f, seed = 3)),
        )
        val (out, report) = merge(frames)
        // Whichever frame is the reference, the square region must equal that reference (no blend).
        val ref = frames[report.referenceIndex].img.pixels
        for (y in 105..135) for (x in 155..185) {
            val d = kotlin.math.abs((out.pixels[y * w + x] and 0xFF) - (ref[y * w + x] and 0xFF))
            assertTrue("ghost at $x,$y: diff $d", d <= 12)
        }
    }

    @Test
    fun `exposure drift is normalised before merging`() {
        val frames = listOf(
            TestFrame(frame(0f, 0f, 10f, seed = 1), exposureProduct = 1.0),
            TestFrame(frame(0f, 0f, 10f, seed = 2, gain = 0.5f), exposureProduct = 0.5),
            TestFrame(frame(0f, 0f, 10f, seed = 3), exposureProduct = 1.0),
        )
        val (out, report) = merge(frames)
        assertEquals(3, report.framesMerged)
        assertTrue("acceptance ${report.meanAcceptance}", report.meanAcceptance > 0.7f)
        // Output keeps the reference frame's exposure.
        val meanOut = out.pixels.map { it and 0xFF }.average()
        val meanRef = frames[report.referenceIndex].img.pixels.map { it and 0xFF }.average()
        assertTrue("brightness kept: $meanOut vs $meanRef", kotlin.math.abs(meanOut - meanRef) < 4)
    }

    @Test
    fun `blurred frame is rejected and the sharp one is the reference`() {
        val frames = listOf(
            TestFrame(frame(0f, 0f, 2f, seed = 1, blur = 6)),
            TestFrame(frame(0f, 0f, 2f, seed = 2)),
        )
        val (_, report) = merge(frames)
        assertEquals(1, report.referenceIndex)
        assertEquals(listOf(0), report.rejectedFrames)
    }

    @Test
    fun `single frame passes through unchanged`() {
        val f = TestFrame(frame(0f, 0f, 5f, seed = 9))
        val (out, report) = merge(listOf(f))
        assertEquals(1, report.framesMerged)
        assertArrayEquals(f.img.pixels, out.pixels)
    }

    @Test
    fun `different scene is rejected rather than blended`() {
        val other = ArgbImage(w, h, IntArray(w * h) { rgb(((it * 7919) % 200) + 20) })
        val frames = listOf(TestFrame(frame(0f, 0f, 3f, seed = 1)), TestFrame(other))
        val (out, report) = merge(frames)
        val ref = frames[report.referenceIndex].img.pixels
        assertTrue(rmse(out.pixels, ref) < 3.0)
    }
}
