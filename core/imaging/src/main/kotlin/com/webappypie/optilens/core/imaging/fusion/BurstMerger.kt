package com.webappypie.optilens.core.imaging.fusion

import com.webappypie.optilens.core.imaging.enhance.ArgbImage
import com.webappypie.optilens.core.imaging.enhance.ArgbRowSink
import com.webappypie.optilens.core.imaging.enhance.LocalEnhancementPipeline
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** One frame of a burst, decodable at reduced scale and in full-resolution row bands. */
interface BurstFrameSource {
    val width: Int
    val height: Int

    /** Exposure time × ISO (any consistent unit); ≤ 0 when unknown (treated as equal to the reference). */
    val exposureProduct: Double

    /** The whole frame downsampled by roughly [factor] (any rounding; the size is read from the result). */
    fun readDownsampled(factor: Int): ArgbImage

    /** Full-resolution rows `[y, y + rows)` into [dst] (stride = [width]); may be called concurrently. */
    fun readRows(y: Int, rows: Int, dst: IntArray)
}

/** What the merge measured and did. */
data class BurstMergeReport(
    val frameCount: Int,
    val referenceIndex: Int,
    /** Frames that contributed (reference included). */
    val framesMerged: Int,
    /** Indices rejected entirely (blurred, misaligned or a different exposure that could not be normalised). */
    val rejectedFrames: List<Int>,
    /** Mean merge weight of the non-reference frames that were used (1 = fully merged, 0 = all rejected as motion). */
    val meanAcceptance: Float,
    val alignMs: Long,
    val mergeMs: Long,
)

/**
 * Burst noise reduction in the style of HDR+ (Hasinoff et al. 2016), simplified for JPEG frames:
 *
 * 1. **Reference** = sharpest frame (gradient energy at ½ scale); frames much blurrier than it
 *    (hand shake during exposure) are dropped.
 * 2. **Alignment**: global translation by a coarse-to-fine pyramid search, then a per-tile
 *    (≈96 px) refinement with sub-pixel parabola fitting; the displacement field is median
 *    smoothed and bilinearly interpolated per pixel, so small rotations are followed too.
 * 3. **Exposure normalisation** in linear light using each frame's measured mid-tone
 *    brightness relative to the reference (exposure time × ISO only as a fallback), so frames
 *    whose exposure drifted can still be merged; clipped pixels of brighter frames are excluded.
 * 4. **Robust merge**: each pixel of each frame is compared with the reference in a
 *    noise-stabilised (√linear) domain. The noise level is measured per band and frame
 *    (median of differences); differences beyond 2–4σ — moving subjects, residual
 *    misalignment — get zero weight, so the reference is kept there (no ghosts).
 *
 * The result only averages real captured samples of the same scene point; nothing is
 * synthesised. Noise drops by up to √N in static areas; moving areas keep single-frame noise.
 */
class BurstMerger {

    suspend fun merge(
        frames: List<BurstFrameSource>,
        sink: ArgbRowSink,
        parallelism: Int = 1,
    ): BurstMergeReport = coroutineScope {
        require(frames.isNotEmpty()) { "No frames to merge" }
        val w = frames[0].width
        val h = frames[0].height
        require(frames.all { it.width == w && it.height == h }) { "Burst frames differ in size" }

        val t0 = System.nanoTime()

        // 1. Brightness of each frame (measured: metadata ISO can miss vendor sensitivity boosts),
        //    then reference selection: sharpest frame at half scale, compared at a common exposure
        //    so a darker frame is not mistaken for a blurrier one.
        // Only statistics are kept per frame; frames are re-decoded when aligned (bounded memory).
        val means = DoubleArray(frames.size)
        val rawSharpness = frames.indices.map { i ->
            ensureActive()
            val img = frames[i].readDownsampled(2)
            means[i] = meanMidtoneLinear(img)
            gradientEnergy(Plane(img.width, img.height, lumaSqrt(img, 1f)))
        }
        fun gainTo(target: Int, i: Int): Float {
            val mt = means[target]
            val mi = means[i]
            if (mt > 0.003 && mi > 0.003) return (mt / mi).toFloat()
            val et = frames[target].exposureProduct
            val ei = frames[i].exposureProduct
            return if (et > 0 && ei > 0) (et / ei).toFloat() else 1f
        }
        // Energy of √(g·y) differences scales with g: compare all frames at frame 0's exposure.
        val sharpness = frames.indices.map { i ->
            val g = gainTo(0, i).toDouble()
            rawSharpness[i].first * g to rawSharpness[i].second * g
        }
        // Geometric mean of the two axes: directional shake blur lowers one of them strongly.
        val refIndex = sharpness.indices.maxByOrNull { sharpness[it].first * sharpness[it].second } ?: 0

        val rejected = mutableListOf<Int>()
        val gains = FloatArray(frames.size) { gainTo(refIndex, it) }

        // 2. Alignment at half scale.
        val refHalfImg = frames[refIndex].readDownsampled(2)
        val refHalf = Plane(refHalfImg.width, refHalfImg.height, lumaSqrt(refHalfImg, 1f))
        val scaleX = w.toFloat() / refHalf.w
        val scaleY = h.toFloat() / refHalf.h
        val refPyramid = pyramid(refHalf)
        val tilesX = (refHalf.w + TILE - 1) / TILE
        val tilesY = (refHalf.h + TILE - 1) / TILE
        val fields = arrayOfNulls<DisplacementField>(frames.size)

        for (i in frames.indices) {
            ensureActive()
            if (i == refIndex) continue
            val blurred = sharpness[i].first < MIN_RELATIVE_SHARPNESS * sharpness[refIndex].first ||
                sharpness[i].second < MIN_RELATIVE_SHARPNESS * sharpness[refIndex].second
            if (blurred || gains[i] !in 0.25f..4f) {
                rejected += i
                continue
            }
            val img = frames[i].readDownsampled(2)
            if (img.width != refHalf.w || img.height != refHalf.h) {
                rejected += i
                continue
            }
            val half = Plane(img.width, img.height, lumaSqrt(img, gains[i]))
            val field = alignFrame(refPyramid, pyramid(half), tilesX, tilesY, scaleX, scaleY)
            if (field == null || field.maxAbsDy() > MAX_SHIFT_ROWS || field.maxAbsDx() > MAX_SHIFT_ROWS) {
                rejected += i
            } else {
                fields[i] = field
            }
        }
        val t1 = System.nanoTime()

        // 3. Robust merge in parallel row bands.
        val used = frames.indices.filter { it != refIndex && fields[it] != null }
        val acceptanceSum = DoubleArray(frames.size)
        val acceptanceCount = LongArray(frames.size)
        val bands = (h + BAND - 1) / BAND
        val next = AtomicInteger(0)
        (0 until max(1, min(parallelism, bands))).map {
            async {
                val ws = MergeWorkspace(w)
                val localAcc = DoubleArray(frames.size)
                val localCnt = LongArray(frames.size)
                while (true) {
                    val b = next.getAndIncrement()
                    if (b >= bands) break
                    ensureActive()
                    val y0 = b * BAND
                    val y1 = min(h, y0 + BAND)
                    mergeBand(frames, refIndex, used, fields, gains, tilesX, tilesY, scaleX, scaleY, y0, y1, ws, sink, localAcc, localCnt)
                }
                synchronized(acceptanceSum) {
                    for (k in frames.indices) {
                        acceptanceSum[k] += localAcc[k]
                        acceptanceCount[k] += localCnt[k]
                    }
                }
            }
        }.awaitAll()
        val t2 = System.nanoTime()

        val acceptance = used.map { if (acceptanceCount[it] > 0) acceptanceSum[it] / acceptanceCount[it] else 0.0 }
        BurstMergeReport(
            frameCount = frames.size,
            referenceIndex = refIndex,
            framesMerged = 1 + used.size,
            rejectedFrames = rejected.sorted(),
            meanAcceptance = if (acceptance.isEmpty()) 0f else acceptance.average().toFloat(),
            alignMs = (t1 - t0) / 1_000_000,
            mergeMs = (t2 - t1) / 1_000_000,
        )
    }

    // ── Alignment ───────────────────────────────────────────────────────────

    internal class Plane(val w: Int, val h: Int, val data: FloatArray)

    /** Per-tile displacement (full-resolution pixels) of the frame relative to the reference. */
    internal class DisplacementField(val tilesX: Int, val tilesY: Int, val dx: FloatArray, val dy: FloatArray) {
        fun maxAbsDx() = dx.maxOf { abs(it) }
        fun maxAbsDy() = dy.maxOf { abs(it) }
    }

    private fun pyramid(base: Plane): List<Plane> {
        val levels = mutableListOf(base)
        while (levels.size < 4 && levels.last().w >= 64 && levels.last().h >= 64) {
            levels += downsample2(levels.last())
        }
        return levels
    }

    private fun alignFrame(
        ref: List<Plane>,
        frame: List<Plane>,
        tilesX: Int,
        tilesY: Int,
        scaleX: Float,
        scaleY: Float,
    ): DisplacementField? {
        // Coarse-to-fine global translation.
        val top = min(ref.size, frame.size) - 1
        val coarse = ref[top]
        val radius = (max(coarse.w, coarse.h) / 10).coerceIn(4, 16)
        var best = searchTranslation(ref[top], frame[top], 0, 0, radius, 1)
        for (level in top - 1 downTo 0) {
            best = searchTranslation(ref[level], frame[level], best.first * 2, best.second * 2, 2, if (level == 0) 2 else 1)
        }
        val (gx, gy) = best
        val r0 = ref[0]
        val f0 = frame[0]
        val globalCost = regionCost(r0, f0, gx.toFloat(), gy.toFloat(), r0.w / 10, r0.h / 10, r0.w - r0.w / 10, r0.h - r0.h / 10, 2)
        if (!globalCost.isFinite() || globalCost > MAX_GLOBAL_COST) return null

        // Per-tile refinement with sub-pixel parabola fit.
        val dxs = FloatArray(tilesX * tilesY)
        val dys = FloatArray(tilesX * tilesY)
        val costs = FloatArray(25)
        for (ty in 0 until tilesY) {
            for (tx in 0 until tilesX) {
                val x0 = tx * TILE
                val y0 = ty * TILE
                val x1 = min(r0.w, x0 + TILE)
                val y1 = min(r0.h, y0 + TILE)
                var bx = gx.toFloat()
                var by = gy.toFloat()
                if (tileStd(r0, x0, y0, x1, y1) >= FLAT_TILE_STD) {
                    var bestCost = Float.MAX_VALUE
                    var bi = 0
                    var bj = 0
                    for (j in -2..2) for (i in -2..2) {
                        val c = regionCost(r0, f0, (gx + i).toFloat(), (gy + j).toFloat(), x0, y0, x1, y1, 2)
                        costs[(j + 2) * 5 + (i + 2)] = c
                        if (c < bestCost) { bestCost = c; bi = i; bj = j }
                    }
                    if (bestCost.isFinite()) {
                        bx = gx + bi + subPixel(costs, bi, bj, horizontal = true)
                        by = gy + bj + subPixel(costs, bi, bj, horizontal = false)
                    }
                }
                dxs[ty * tilesX + tx] = bx
                dys[ty * tilesX + tx] = by
            }
        }
        val sdx = median3x3(dxs, tilesX, tilesY)
        val sdy = median3x3(dys, tilesX, tilesY)
        for (k in sdx.indices) {
            sdx[k] *= scaleX
            sdy[k] *= scaleY
        }
        return DisplacementField(tilesX, tilesY, sdx, sdy)
    }

    private fun subPixel(costs: FloatArray, bi: Int, bj: Int, horizontal: Boolean): Float {
        val c0 = costs[(bj + 2) * 5 + (bi + 2)]
        val (cm, cp) = if (horizontal) {
            if (bi <= -2 || bi >= 2) return 0f
            costs[(bj + 2) * 5 + (bi + 1)] to costs[(bj + 2) * 5 + (bi + 3)]
        } else {
            if (bj <= -2 || bj >= 2) return 0f
            costs[(bj + 1) * 5 + (bi + 2)] to costs[(bj + 3) * 5 + (bi + 2)]
        }
        val denom = cm - 2f * c0 + cp
        if (denom <= 1e-9f || !cm.isFinite() || !cp.isFinite()) return 0f
        return ((cm - cp) / (2f * denom)).coerceIn(-0.5f, 0.5f)
    }

    private fun searchTranslation(
        ref: Plane,
        frame: Plane,
        cx: Int,
        cy: Int,
        radius: Int,
        step: Int,
    ): Pair<Int, Int> {
        val mx = ref.w / 10
        val my = ref.h / 10
        var best = Float.MAX_VALUE
        var bx = cx
        var by = cy
        for (dy in cy - radius..cy + radius) {
            for (dx in cx - radius..cx + radius) {
                val c = regionCost(ref, frame, dx.toFloat(), dy.toFloat(), mx, my, ref.w - mx, ref.h - my, step)
                if (c < best) { best = c; bx = dx; by = dy }
            }
        }
        return bx to by
    }

    /** Mean |ref(x,y) − frame(x+dx, y+dy)| over the overlap of the region; +∞ if too little overlap. */
    private fun regionCost(ref: Plane, frame: Plane, dx: Float, dy: Float, x0: Int, y0: Int, x1: Int, y1: Int, step: Int): Float {
        val ix = dx.toInt()
        val iy = dy.toInt()
        var sum = 0f
        var n = 0
        var y = y0
        while (y < y1) {
            val fy = y + iy
            if (fy in 0 until frame.h) {
                val ro = y * ref.w
                val fo = fy * frame.w
                var x = x0
                while (x < x1) {
                    val fx = x + ix
                    if (fx in 0 until frame.w) {
                        sum += abs(ref.data[ro + x] - frame.data[fo + fx])
                        n++
                    }
                    x += step
                }
            }
            y += step
        }
        val expected = ((x1 - x0) / step) * ((y1 - y0) / step)
        return if (n == 0 || n < expected / 2) Float.POSITIVE_INFINITY else sum / n
    }

    private fun tileStd(p: Plane, x0: Int, y0: Int, x1: Int, y1: Int): Float {
        var s = 0.0
        var s2 = 0.0
        var n = 0
        for (y in y0 until y1 step 2) for (x in x0 until x1 step 2) {
            val v = p.data[y * p.w + x].toDouble()
            s += v; s2 += v * v; n++
        }
        if (n == 0) return 0f
        val m = s / n
        return sqrt(max(0.0, s2 / n - m * m)).toFloat()
    }

    private fun median3x3(v: FloatArray, w: Int, h: Int): FloatArray {
        val out = FloatArray(v.size)
        val buf = FloatArray(9)
        for (y in 0 until h) for (x in 0 until w) {
            var n = 0
            for (j in -1..1) for (i in -1..1) {
                val xx = x + i
                val yy = y + j
                if (xx in 0 until w && yy in 0 until h) buf[n++] = v[yy * w + xx]
            }
            buf.sort(0, n)
            out[y * w + x] = buf[n / 2]
        }
        return out
    }

    // ── Merge ───────────────────────────────────────────────────────────────

    private class MergeWorkspace(val width: Int) {
        val refRows = IntArray(width * BAND)
        val frameRows = IntArray(width * (BAND + 2 * MAX_SHIFT_ROWS + 4))
        val accR = FloatArray(width * BAND)
        val accG = FloatArray(width * BAND)
        val accB = FloatArray(width * BAND)
        val accW = FloatArray(width * BAND)
        val refS = FloatArray(width * BAND)
        val sR = FloatArray(width * BAND)
        val sG = FloatArray(width * BAND)
        val sB = FloatArray(width * BAND)
        val diff = FloatArray(width * BAND)
        val hist = IntArray(HIST_BINS)
        val out = IntArray(width * BAND)
    }

    private fun mergeBand(
        frames: List<BurstFrameSource>,
        refIndex: Int,
        used: List<Int>,
        fields: Array<DisplacementField?>,
        gains: FloatArray,
        tilesX: Int,
        tilesY: Int,
        scaleX: Float,
        scaleY: Float,
        y0: Int,
        y1: Int,
        ws: MergeWorkspace,
        sink: ArgbRowSink,
        acceptanceSum: DoubleArray,
        acceptanceCount: LongArray,
    ) {
        val w = ws.width
        val h = frames[0].height
        val rows = y1 - y0
        val n = rows * w
        frames[refIndex].readRows(y0, rows, ws.refRows)
        for (i in 0 until n) {
            val p = ws.refRows[i]
            val r = LIN[(p shr 16) and 0xFF]
            val g = LIN[(p shr 8) and 0xFF]
            val b = LIN[p and 0xFF]
            ws.accR[i] = r; ws.accG[i] = g; ws.accB[i] = b; ws.accW[i] = 1f
            ws.refS[i] = sqrt(KR * r + KG * g + KB * b)
        }

        val tileW = TILE * scaleX
        val tileH = TILE * scaleY
        for (k in used) {
            val field = fields[k]!!
            val gain = gains[k]
            val minDy = floor(field.dy.min()).toInt()
            val maxDy = ceil(field.dy.max()).toInt()
            val srcTop = (y0 + minDy - 1).coerceIn(0, h - 1)
            val srcBottom = (y1 + maxDy + 2).coerceIn(srcTop + 1, h)
            frames[k].readRows(srcTop, srcBottom - srcTop, ws.frameRows)

            // Pass 1: displaced bilinear samples and their √-luma difference to the reference.
            var hn = 0
            ws.hist.fill(0)
            for (y in y0 until y1) {
                val gy = ((y + 0.5f) / tileH - 0.5f).coerceIn(0f, (tilesY - 1).toFloat())
                val j0 = gy.toInt()
                val j1 = min(j0 + 1, tilesY - 1)
                val fyT = gy - j0
                for (x in 0 until w) {
                    val i = (y - y0) * w + x
                    val gx = ((x + 0.5f) / tileW - 0.5f).coerceIn(0f, (tilesX - 1).toFloat())
                    val i0 = gx.toInt()
                    val i1 = min(i0 + 1, tilesX - 1)
                    val fxT = gx - i0
                    val dx = bilerp(field.dx, tilesX, i0, i1, j0, j1, fxT, fyT)
                    val dy = bilerp(field.dy, tilesX, i0, i1, j0, j1, fxT, fyT)
                    val sx = x + dx
                    val sy = y + dy
                    val ix = floor(sx).toInt()
                    val iy = floor(sy).toInt()
                    if (ix < 0 || iy < srcTop || ix + 1 >= w || iy + 1 >= srcBottom) {
                        ws.diff[i] = -1f
                        continue
                    }
                    val ax = sx - ix
                    val ay = sy - iy
                    val o00 = (iy - srcTop) * w + ix
                    val o10 = o00 + w
                    val p00 = ws.frameRows[o00]; val p01 = ws.frameRows[o00 + 1]
                    val p10 = ws.frameRows[o10]; val p11 = ws.frameRows[o10 + 1]
                    if (gain < 0.95f && (isClipped(p00) || isClipped(p01) || isClipped(p10) || isClipped(p11))) {
                        ws.diff[i] = -1f
                        continue
                    }
                    val r = bilinChannel(p00, p01, p10, p11, 16, ax, ay) * gain
                    val g = bilinChannel(p00, p01, p10, p11, 8, ax, ay) * gain
                    val b = bilinChannel(p00, p01, p10, p11, 0, ax, ay) * gain
                    ws.sR[i] = r; ws.sG[i] = g; ws.sB[i] = b
                    val d = abs(sqrt(max(0f, KR * r + KG * g + KB * b)) - ws.refS[i])
                    ws.diff[i] = d
                    ws.hist[min(HIST_BINS - 1, (d * HIST_SCALE).toInt())]++
                    hn++
                }
            }
            if (hn == 0) continue

            // Noise of this frame pair in this band: median |difference| / 0.6745.
            var acc = 0
            var medianBin = 0
            while (medianBin < HIST_BINS) {
                acc += ws.hist[medianBin]
                if (acc * 2 >= hn) break
                medianBin++
            }
            val sigma = max(MIN_SIGMA, (medianBin + 0.5f) / HIST_SCALE / 0.6745f)
            val lo = ACCEPT_SIGMA * sigma
            val hi = REJECT_SIGMA * sigma

            // Pass 2: robust weights and accumulation.
            var wsum = 0.0
            var cnt = 0L
            for (i in 0 until n) {
                val d = ws.diff[i]
                if (d < 0f) continue
                val wgt = when {
                    d <= lo -> 1f
                    d >= hi -> 0f
                    else -> (hi - d) / (hi - lo)
                }
                cnt++
                wsum += wgt
                if (wgt > 0f) {
                    ws.accR[i] += ws.sR[i] * wgt
                    ws.accG[i] += ws.sG[i] * wgt
                    ws.accB[i] += ws.sB[i] * wgt
                    ws.accW[i] += wgt
                }
            }
            acceptanceSum[k] += wsum
            acceptanceCount[k] += cnt
        }

        for (i in 0 until n) {
            val inv = 1f / ws.accW[i]
            ws.out[i] = OPAQUE or
                (LocalEnhancementPipeline.encode8(ws.accR[i] * inv) shl 16) or
                (LocalEnhancementPipeline.encode8(ws.accG[i] * inv) shl 8) or
                LocalEnhancementPipeline.encode8(ws.accB[i] * inv)
        }
        sink.writeRows(y0, rows, ws.out)
    }

    internal companion object {
        const val TILE = 48
        const val BAND = 128
        const val MAX_SHIFT_ROWS = 160
        const val MIN_RELATIVE_SHARPNESS = 0.75f
        const val MAX_GLOBAL_COST = 0.12f
        const val FLAT_TILE_STD = 0.01f
        const val ACCEPT_SIGMA = 2f
        const val REJECT_SIGMA = 4f
        const val MIN_SIGMA = 0.002f
        const val HIST_BINS = 4096
        const val HIST_SCALE = 8192f
        const val KR = 0.2126f
        const val KG = 0.7152f
        const val KB = 0.0722f
        const val OPAQUE = -0x1000000
        private val LIN = LocalEnhancementPipeline.LIN

        fun isClipped(p: Int): Boolean =
            ((p shr 16) and 0xFF) >= 250 || ((p shr 8) and 0xFF) >= 250 || (p and 0xFF) >= 250

        private fun bilinChannel(p00: Int, p01: Int, p10: Int, p11: Int, shift: Int, ax: Float, ay: Float): Float {
            val a = LIN[(p00 shr shift) and 0xFF]
            val b = LIN[(p01 shr shift) and 0xFF]
            val c = LIN[(p10 shr shift) and 0xFF]
            val d = LIN[(p11 shr shift) and 0xFF]
            val top = a + (b - a) * ax
            val bottom = c + (d - c) * ax
            return top + (bottom - top) * ay
        }

        private fun bilerp(v: FloatArray, stride: Int, i0: Int, i1: Int, j0: Int, j1: Int, fx: Float, fy: Float): Float {
            val a = v[j0 * stride + i0]
            val b = v[j0 * stride + i1]
            val c = v[j1 * stride + i0]
            val d = v[j1 * stride + i1]
            val top = a + (b - a) * fx
            return top + (c + (d - c) * fx - top) * fy
        }

        /** Mean linear luminance of mid-tone pixels (neither near-black nor near-clipped). */
        fun meanMidtoneLinear(img: ArgbImage): Double {
            var s = 0.0
            var n = 0
            var i = 0
            while (i < img.pixels.size) {
                val p = img.pixels[i]
                val y = KR * LIN[(p shr 16) and 0xFF] + KG * LIN[(p shr 8) and 0xFF] + KB * LIN[p and 0xFF]
                if (y in 0.004f..0.85f) { s += y; n++ }
                i += 3
            }
            return if (n < 100) 0.0 else s / n
        }

        /** √(linear luminance × gain): noise is roughly level-independent in this domain. */
        fun lumaSqrt(img: ArgbImage, gain: Float): FloatArray = FloatArray(img.pixels.size) {
            val p = img.pixels[it]
            val y = KR * LIN[(p shr 16) and 0xFF] + KG * LIN[(p shr 8) and 0xFF] + KB * LIN[p and 0xFF]
            sqrt(y * gain)
        }

        fun downsample2(p: Plane): Plane {
            val w2 = max(1, p.w / 2)
            val h2 = max(1, p.h / 2)
            val out = FloatArray(w2 * h2)
            for (y in 0 until h2) for (x in 0 until w2) {
                val x0 = min(2 * x, p.w - 1); val x1 = min(2 * x + 1, p.w - 1)
                val y0 = min(2 * y, p.h - 1); val y1 = min(2 * y + 1, p.h - 1)
                out[y * w2 + x] = 0.25f * (p.data[y0 * p.w + x0] + p.data[y0 * p.w + x1] + p.data[y1 * p.w + x0] + p.data[y1 * p.w + x1])
            }
            return Plane(w2, h2, out)
        }

        /**
         * Mid-frequency gradient energy: 3×3 box-smoothed, differences over 2 px. The smoothing
         * suppresses pixel noise (which would make a noisy frame look "sharp") while hand-shake
         * blur still removes this band.
         */
        internal fun gradientEnergy(p: Plane): Pair<Double, Double> {
            val sm = FloatArray(p.w * p.h)
            for (y in 0 until p.h) for (x in 0 until p.w) {
                var acc = 0f
                var n = 0
                for (j in -1..1) for (i in -1..1) {
                    val xx = x + i
                    val yy = y + j
                    if (xx in 0 until p.w && yy in 0 until p.h) { acc += p.data[yy * p.w + xx]; n++ }
                }
                sm[y * p.w + x] = acc / n
            }
            var sx = 0.0
            var sy = 0.0
            var n = 0
            for (y in 0 until p.h - 2) for (x in 0 until p.w - 2) {
                val c = sm[y * p.w + x]
                val gx = sm[y * p.w + x + 2] - c
                val gy = sm[(y + 2) * p.w + x] - c
                sx += (gx * gx).toDouble()
                sy += (gy * gy).toDouble()
                n++
            }
            val d = max(1, n).toDouble()
            return sx / d to sy / d
        }
    }
}
