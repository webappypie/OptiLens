package com.webappypie.optilens.core.imaging.enhance

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Packed ARGB_8888 image read in row bands. [readRows] must tolerate concurrent calls. */
interface ArgbRowSource {
    val width: Int
    val height: Int

    /** Copies rows `[y, y + rows)` into [dst] with a row stride of [width]. */
    fun readRows(y: Int, rows: Int, dst: IntArray)
}

/** Receives finished rows. Calls cover disjoint row ranges and may arrive from several threads. */
fun interface ArgbRowSink {
    fun writeRows(y: Int, rows: Int, src: IntArray)
}

/** In-memory ARGB image; used by tests, benchmarks and small inputs. */
class ArgbImage(
    override val width: Int,
    override val height: Int,
    val pixels: IntArray = IntArray(width * height),
) : ArgbRowSource, ArgbRowSink {
    init {
        require(width > 0 && height > 0 && pixels.size == width * height) { "Invalid image size" }
    }

    override fun readRows(y: Int, rows: Int, dst: IntArray) =
        System.arraycopy(pixels, y * width, dst, 0, rows * width)

    override fun writeRows(y: Int, rows: Int, src: IntArray) =
        System.arraycopy(src, 0, pixels, y * width, rows * width)
}

/** What the pipeline measured and applied — real values, used for UI notes and benchmarks. */
data class EnhancementReport(
    val width: Int,
    val height: Int,
    /** Brightness change applied at the scene's median tone, in stops (0 = unchanged). */
    val exposureStops: Float,
    /** Slope applied to deep shadows in the log domain (1 = no shadow compression). */
    val shadowCompression: Float,
    val whiteBalanceGainR: Float,
    val whiteBalanceGainG: Float,
    val whiteBalanceGainB: Float,
    /** Estimated shadow noise, perceptual (sRGB) units 0..1. */
    val shadowNoiseSigma: Float,
    /** Luminance denoise window radius; 0 when the photo was too clean to need it. */
    val lumaDenoiseRadius: Int,
    /** Fraction of the frame whose pixels were already clipped white in the input (not recoverable). */
    val clippedHighlightFraction: Float,
    /** Scene type the analysis measured. */
    val scene: EnhancementScene = EnhancementScene.GENERAL,
    /** Haze removal strength actually applied (0 = none). */
    val dehazeStrength: Float = 0f,
    /** Maximum darkening of bright, unclipped areas in stops (0 = none). */
    val highlightRecoveryStops: Float = 0f,
    /** Number of faces whose skin texture was protected. */
    val facesProtected: Int = 0,
    val analysisMs: Long,
    val renderMs: Long,
)

/**
 * Single-image on-device photo enhancement. Classic, deterministic image processing — no
 * trained model and no generated content: every output pixel is a tone/colour/noise/detail
 * adjustment of the same input pixel and its immediate neighbours, so scene content,
 * faces and geometry cannot change.
 *
 * Stages:
 * 1. **Analysis** at ≤512 px (box-downsampled in linear light): robust shades-of-grey white
 *    balance estimate, log-luminance statistics, noise estimate from full-resolution rows
 *    (robust Laplacian quantile), clipped-highlight fraction.
 * 2. **Edge-aware multi-scale decomposition** of log-luminance with fast guided filters
 *    (He & Sun 2015) computed at low resolution and applied at full resolution:
 *    base (~1/24 of the frame), mid band, fine detail. Halo-free because the filter is
 *    edge-preserving and the coefficients are smooth.
 * 3. **Tone mapping** on the base layer only: measured exposure correction, smooth shadow
 *    compression, white point fixed (clipped whites stay white); mid band boosted
 *    ("clarity") except where noise or near-white highlights dominate; fine detail kept 1:1.
 *    Luminance is changed by a per-pixel ratio, so hue and chromaticity are preserved.
 * 4. **Chroma denoise**: chromaticity is pulled towards a luma-guided low-resolution estimate
 *    only where the difference is within the expected noise (real colour edges are kept).
 * 5. **Luma denoise** (only if noise is measured): local Wiener (Lee) filter whose noise
 *    level follows the per-pixel noise after tone mapping.
 * 6. **Sharpening** of fine detail with noise coring and clamping to the local 3×3 min/max
 *    (no overshoot → no halos), then vibrance with skin protection and gamut-safe encode.
 *
 * Scene adaptation (measured, never guessed from labels):
 * - **Portrait** (faces passed in [EnhancementHints]): faces are metered for exposure; clarity,
 *   sharpening and luma denoise are reduced on a soft face mask so skin texture and identity are
 *   kept; no haze removal.
 * - **Landscape / distant scenes**: atmospheric haze is measured with the dark-channel prior in
 *   linear light and removed with the scattering model `J = (I − A(1 − t)) / t`, transmission
 *   floored at [MIN_TRANSMISSION] and refined edge-aware; bright, neutral sky is protected.
 * - **Low light**: less sharpening and stronger noise reduction (exposure lift already shrinks
 *   with measured noise).
 * - **High contrast / mixed light**: bright, unclipped areas are compressed in the base layer
 *   (highlight recovery) while shadows are lifted; already-clipped pixels stay white.
 *
 * Full-resolution work runs in 64-row strips in parallel; memory is O(width × strip).
 */
class LocalEnhancementPipeline(
    private val config: EnhancementConfig = EnhancementConfig.BALANCED,
    private val hints: EnhancementHints = EnhancementHints.NONE,
) {

    suspend fun process(
        source: ArgbRowSource,
        sink: ArgbRowSink,
        parallelism: Int = 1,
        onProgress: ((Float) -> Unit)? = null,
    ): EnhancementReport = coroutineScope {
        val workers = parallelism.coerceIn(1, 8)
        val t0 = System.nanoTime()
        val plan = analyze(source, workers)
        val t1 = System.nanoTime()
        onProgress?.invoke(ANALYSIS_PROGRESS)
        render(source, sink, plan, workers, onProgress)
        val t2 = System.nanoTime()
        EnhancementReport(
            width = source.width,
            height = source.height,
            exposureStops = plan.exposure,
            shadowCompression = plan.shadowC,
            whiteBalanceGainR = plan.gainR,
            whiteBalanceGainG = plan.gainG,
            whiteBalanceGainB = plan.gainB,
            shadowNoiseSigma = plan.sigmaShadow,
            lumaDenoiseRadius = plan.denoiseRadius,
            clippedHighlightFraction = plan.clippedFraction,
            scene = plan.scene,
            dehazeStrength = plan.dehaze,
            highlightRecoveryStops = plan.highlightStops,
            facesProtected = plan.faceCount,
            analysisMs = (t1 - t0) / 1_000_000,
            renderMs = (t2 - t1) / 1_000_000,
        )
    }

    // ── Analysis ────────────────────────────────────────────────────────────

    internal class Plan(
        val f: Int,
        val lw: Int,
        val lh: Int,
        /** Per low-res cell: Ab, Bb, Am, Bm, Acr, Bcr, Acg, Bcg, At, Bt (transmission), face mask. */
        val grids: FloatArray,
        val gainR: Float,
        val gainG: Float,
        val gainB: Float,
        val anchor: Float,
        val exposure: Float,
        val shadowC: Float,
        val shadowDepth: Float,
        val clarity: Float,
        val sigmaShadow: Float,
        val sigmaAll: Float,
        val denoiseRadius: Int,
        val denoiseStrength: Float,
        val chromaStrength: Float,
        val sharpenAmount: Float,
        val vibrance: Float,
        val clippedFraction: Float,
        val scene: EnhancementScene,
        /** Airlight (linear, white-balanced) and applied haze strength; dehaze == 0 disables it. */
        val airR: Float,
        val airG: Float,
        val airB: Float,
        val dehaze: Float,
        val highlightStops: Float,
        val faceCount: Int,
    )

    private suspend fun analyze(source: ArgbRowSource, workers: Int): Plan = coroutineScope {
        val w = source.width
        val h = source.height
        val f = max(1, ceil(max(w, h) / ANALYSIS_LONG_EDGE.toDouble()).toInt())
        val lw = (w + f - 1) / f
        val lh = (h + f - 1) / f
        val n = lw * lh
        val lr = FloatArray(n)
        val lg = FloatArray(n)
        val lb = FloatArray(n)
        val clipped = AtomicInteger(0)

        // Box-average downsample in linear light (parallel over low-res rows).
        val nextRow = AtomicInteger(0)
        (0 until workers).map {
            async {
                val buf = IntArray(w * f)
                val acc = FloatArray(lw * 3)
                while (true) {
                    val j = nextRow.getAndIncrement()
                    if (j >= lh) break
                    ensureActive()
                    val y0 = j * f
                    val rows = min(f, h - y0)
                    source.readRows(y0, rows, buf)
                    acc.fill(0f)
                    var clip = 0
                    for (i in 0 until rows * w) {
                        val p = buf[i]
                        val r8 = (p shr 16) and 0xFF
                        val g8 = (p shr 8) and 0xFF
                        val b8 = p and 0xFF
                        if (r8 >= CLIP_LEVEL && g8 >= CLIP_LEVEL && b8 >= CLIP_LEVEL) clip++
                        val c = (i % w) / f * 3
                        acc[c] += LIN[r8]
                        acc[c + 1] += LIN[g8]
                        acc[c + 2] += LIN[b8]
                    }
                    clipped.addAndGet(clip)
                    for (i in 0 until lw) {
                        val cw = min(f, w - i * f)
                        val inv = 1f / (cw * rows)
                        lr[j * lw + i] = acc[i * 3] * inv
                        lg[j * lw + i] = acc[i * 3 + 1] * inv
                        lb[j * lw + i] = acc[i * 3 + 2] * inv
                    }
                }
            }
        }.awaitAll()
        ensureActive()

        val (sigmaShadow, sigmaAll) = estimateNoise(source)

        // White balance: shades-of-grey (p = 6) over well-exposed cells, applied partially.
        var sR = 0.0
        var sG = 0.0
        var sB = 0.0
        var used = 0
        for (i in 0 until n) {
            val r = lr[i]
            val g = lg[i]
            val b = lb[i]
            val y = KR * r + KG * g + KB * b
            if (y < 0.01f || max(r, max(g, b)) > 0.9f) continue
            sR += pow6(r)
            sG += pow6(g)
            sB += pow6(b)
            used++
        }
        var gainR = 1f
        var gainG = 1f
        var gainB = 1f
        if (used >= max(16, n / 20) && sR > 0 && sG > 0 && sB > 0) {
            val eR = (sR / used).pow(1.0 / 6.0)
            val eG = (sG / used).pow(1.0 / 6.0)
            val eB = (sB / used).pow(1.0 / 6.0)
            val lnR = ln(eG / eR).toFloat()
            val lnB = ln(eG / eB).toFloat()
            val cast = sqrt(lnR * lnR + lnB * lnB)
            if (cast > 0.03f) {
                // Moderate casts are mostly corrected; strong casts (tungsten, sunsets) are
                // probably part of the scene, so they are only softened.
                val castFactor = when {
                    cast <= 0.25f -> 1f
                    cast >= 0.6f -> 0.35f
                    else -> 1f - 0.65f * (cast - 0.25f) / 0.35f
                }
                val amount = config.whiteBalance * castFactor
                gainR = exp(lnR * amount).coerceIn(0.8f, 1.25f)
                gainB = exp(lnB * amount).coerceIn(0.8f, 1.25f)
                val norm = 1f / (KR * gainR + KG + KB * gainB)
                gainR *= norm
                gainG = norm
                gainB *= norm
            }
        }

        // Faces: soft elliptical mask per low-res cell (slightly enlarged to cover hairline and jaw).
        val faces = if (config.protectFaces) hints.faces.filter { it.area >= MIN_FACE_AREA } else emptyList()
        val faceMask = FloatArray(n)
        for (face in faces) {
            val cx = (face.left + face.right) * 0.5f * w / f
            val cy = (face.top + face.bottom) * 0.5f * h / f
            val rx = max(1f, (face.right - face.left) * 0.5f * w / f * 1.15f)
            val ry = max(1f, (face.bottom - face.top) * 0.5f * h / f * 1.3f)
            val x0 = max(0, floor(cx - rx).toInt())
            val x1 = min(lw - 1, ceil(cx + rx).toInt())
            val y0 = max(0, floor(cy - ry).toInt())
            val y1 = min(lh - 1, ceil(cy + ry).toInt())
            for (j in y0..y1) for (i in x0..x1) {
                val dx = (i + 0.5f - cx) / rx
                val dy = (j + 0.5f - cy) / ry
                val m = 1f - smoothstep(0.6f, 1f, dx * dx + dy * dy)
                if (m > faceMask[j * lw + i]) faceMask[j * lw + i] = m
            }
        }
        val faceArea = faces.sumOf { it.area.toDouble() }.toFloat()
        val isPortrait = faceArea >= PORTRAIT_FACE_AREA

        // White-balanced linear low-res image and its pre-dehaze log-luminance (the dehaze guide).
        val wr = FloatArray(n) { lr[it] * gainR }
        val wg = FloatArray(n) { lg[it] * gainG }
        val wb = FloatArray(n) { lb[it] * gainB }
        val lPre = FloatArray(n) { log2(KR * wr[it] + KG * wg[it] + KB * wb[it] + EPS) }

        val haze = if (config.dehaze > 0f && !isPortrait) estimateHaze(wr, wg, wb, lPre, lw, lh) else null
        val transA = haze?.first ?: FloatArray(n)
        val transB = haze?.second ?: FloatArray(n) { 1f }
        val hazeInfo = haze?.third
        if (hazeInfo != null) {
            for (i in 0 until n) {
                val t = (transA[i] * lPre[i] + transB[i]).coerceIn(MIN_TRANSMISSION, 1f)
                wr[i] = max(0f, (wr[i] - hazeInfo[0] * (1f - t)) / t)
                wg[i] = max(0f, (wg[i] - hazeInfo[1] * (1f - t)) / t)
                wb[i] = max(0f, (wb[i] - hazeInfo[2] * (1f - t)) / t)
            }
        }

        // Log-luminance and chromaticity at low resolution.
        val llow = FloatArray(n)
        val crLow = FloatArray(n)
        val cgLow = FloatArray(n)
        for (i in 0 until n) {
            val r = wr[i]
            val g = wg[i]
            val b = wb[i]
            val y = KR * r + KG * g + KB * b
            llow[i] = log2(y + EPS)
            val s = r + g + b
            if (s > 1e-6f) {
                crLow[i] = r / s
                cgLow[i] = g / s
            } else {
                crLow[i] = THIRD
                cgLow[i] = THIRD
            }
        }

        val rBase = max(2, (max(lw, lh) / 24f).roundToInt())
        val rMid = max(1, rBase / 5)
        val base = guidedCoefficients(llow, llow, lw, lh, rBase, BASE_EPS)
        val mid = guidedCoefficients(llow, llow, lw, lh, rMid, MID_EPS)
        val chR = guidedCoefficients(llow, crLow, lw, lh, CHROMA_RADIUS, CHROMA_EPS)
        val chG = guidedCoefficients(llow, cgLow, lw, lh, CHROMA_RADIUS, CHROMA_EPS)
        val grids = FloatArray(n * GRID_CHANNELS)
        for (i in 0 until n) {
            val o = i * GRID_CHANNELS
            grids[o] = base.first[i]
            grids[o + 1] = base.second[i]
            grids[o + 2] = mid.first[i]
            grids[o + 3] = mid.second[i]
            grids[o + 4] = chR.first[i]
            grids[o + 5] = chR.second[i]
            grids[o + 6] = chG.first[i]
            grids[o + 7] = chG.second[i]
            grids[o + 8] = transA[i]
            grids[o + 9] = transB[i]
            grids[o + 10] = faceMask[i]
        }

        // Tone statistics on the base layer.
        val baseLow = FloatArray(n) { base.first[it] * llow[it] + base.second[it] }
        // Face exposure (before sorting): mean base level where the face mask is strong.
        var faceSum = 0f
        var faceN = 0
        for (i in 0 until n) if (faceMask[i] > 0.5f) { faceSum += baseLow[i]; faceN++ }
        // Bright-but-unclipped share of the frame: candidates for highlight recovery.
        var brightN = 0
        for (i in 0 until n) if (baseLow[i] > -1.4f && baseLow[i] < -0.05f) brightN++
        baseLow.sort()
        val p05 = quantileSorted(baseLow, 0.05f)
        val p50 = quantileSorted(baseLow, 0.50f)
        val p95 = quantileSorted(baseLow, 0.95f)

        val scene = when {
            isPortrait -> EnhancementScene.PORTRAIT
            p50 < LOW_LIGHT_MEDIAN_STOPS || p95 < -4f -> EnhancementScene.LOW_LIGHT
            hazeInfo != null -> EnhancementScene.LANDSCAPE
            p95 - p05 > HIGH_CONTRAST_RANGE_STOPS -> EnhancementScene.HIGH_CONTRAST
            else -> EnhancementScene.GENERAL
        }

        val anchor = min(p50, -0.5f)
        var delta = TARGET_MEDIAN_STOPS - p50
        if (faceN > 0) {
            // A backlit face should end up properly exposed even if the frame median looks fine.
            val faceDelta = TARGET_FACE_STOPS - faceSum / faceN
            if (faceDelta > delta) delta += (faceDelta - delta) * 0.7f
        }
        var exposure = 0f
        if (delta > 0.25f) {
            var cap = config.maxExposureLiftStops
            if (p95 < -4f) cap = min(cap, 1.0f) // genuinely dark scene: keep the mood
            // Brightening a very noisy frame mostly amplifies noise; lift less the noisier it is.
            val noiseRatio = sigmaShadow / 0.015f
            cap /= 1f + noiseRatio * noiseRatio
            // Bright areas that are already near white limit a global lift (it would wash them
            // out); the shadow compression below lifts the dark parts locally instead.
            val headroom = max(0f, HIGHLIGHT_CEILING_STOPS - p95) + 0.5f
            exposure = min(delta * config.exposureCorrection, min(cap, headroom))
        } else if (delta < -0.4f) {
            exposure = max(delta * config.exposureCorrection * 0.6f, -0.6f)
        }
        // Keeps the tone curve monotonic (slope stays > 0 between the anchor and white).
        exposure = exposure.coerceIn(-0.6f * -anchor, 0.6f * -anchor)

        val depth = p50 - p05
        val cTarget = if (depth > 3f) (3f / depth).coerceIn(0.6f, 1f) else 1f
        val shadowC = 1f - config.shadowRecovery * (1f - cTarget)

        // Highlight recovery: only with real highlight content and a scene that has range to give.
        val brightFrac = brightN.toFloat() / n
        var highlightStops = 0f
        if (config.highlightRecovery > 0f && brightFrac > 0.03f && p95 - p05 > 4f) {
            val rangeWeight = if (scene == EnhancementScene.HIGH_CONTRAST) 1f else 0.6f
            highlightStops = MAX_HIGHLIGHT_RECOVERY_STOPS * config.highlightRecovery * rangeWeight *
                smoothstep(0.03f, 0.15f, brightFrac)
            // Keeps the combined tone curve monotonic: the exposure term's steepest negative slope
            // (1.5 · exposure / |anchor|) plus this term's (1.6 · stops) stays below 0.9.
            val exposureSlope = if (exposure > 0f) 1.5f * exposure / -anchor else 0f
            highlightStops = min(highlightStops, max(0f, 0.9f - exposureSlope) / 1.6f)
            if (highlightStops < 0.02f) highlightStops = 0f
        }

        val denoiseRadius = when {
            config.lumaNoiseReduction <= 0f || sigmaShadow < 0.0035f -> 0
            sigmaShadow < 0.010f -> 1
            else -> 2
        }
        val longEdge = max(w, h)
        val sizeFactor = ((longEdge - 800f) / 2000f).coerceIn(0.3f, 1f)
        val sceneSharpen = when (scene) {
            EnhancementScene.LOW_LIGHT -> 0.6f
            EnhancementScene.LANDSCAPE -> 1.15f
            else -> 1f
        }
        val sharpen = config.sharpening * sceneSharpen * sizeFactor / (1f + sigmaAll * 40f)
        val clarity = config.localContrast * if (scene == EnhancementScene.LANDSCAPE) 1.2f else 1f
        val denoiseStrength = if (scene == EnhancementScene.LOW_LIGHT) {
            min(1f, config.lumaNoiseReduction * 1.2f)
        } else {
            config.lumaNoiseReduction
        }

        Plan(
            f = f, lw = lw, lh = lh, grids = grids,
            gainR = gainR, gainG = gainG, gainB = gainB,
            anchor = anchor, exposure = exposure, shadowC = shadowC,
            shadowDepth = max(depth, 1f),
            clarity = clarity,
            sigmaShadow = sigmaShadow, sigmaAll = sigmaAll,
            denoiseRadius = denoiseRadius, denoiseStrength = denoiseStrength,
            chromaStrength = config.chromaNoiseReduction,
            sharpenAmount = sharpen,
            vibrance = config.vibrance,
            clippedFraction = clipped.get().toFloat() / (w.toLong() * h).toFloat(),
            scene = scene,
            airR = hazeInfo?.get(0) ?: 0f,
            airG = hazeInfo?.get(1) ?: 0f,
            airB = hazeInfo?.get(2) ?: 0f,
            dehaze = hazeInfo?.get(3) ?: 0f,
            highlightStops = highlightStops,
            faceCount = faces.size,
        )
    }

    /**
     * Dark-channel haze estimate (He et al. 2009) on the white-balanced linear low-res image.
     * Returns guided-filter coefficients of the transmission map (t = a · lPre + b) and
     * [airR, airG, airB, strength], or null when the photo is not measurably hazy.
     */
    private fun estimateHaze(
        r: FloatArray,
        g: FloatArray,
        b: FloatArray,
        lPre: FloatArray,
        lw: Int,
        lh: Int,
    ): Triple<FloatArray, FloatArray, FloatArray>? {
        val n = lw * lh
        if (n < 64) return null
        val dark = FloatArray(n) { min(r[it], min(g[it], b[it])) }
        val patch = max(2, max(lw, lh) / 48)
        val darkPatch = minFilter(dark, lw, lh, patch)

        // Airlight: mean colour of the haziest 0.2 % (by dark channel).
        val threshold = quantileSorted(darkPatch.copyOf().also { it.sort() }, 0.998f)
        var aR = 0f
        var aG = 0f
        var aB = 0f
        var count = 0
        var rowSum = 0L
        for (i in 0 until n) {
            if (darkPatch[i] < threshold) continue
            aR += r[i]; aG += g[i]; aB += b[i]; count++
            rowSum += i / lw
        }
        if (count == 0) return null
        aR /= count; aG /= count; aB /= count
        val aMin = min(aR, min(aG, aB))
        // Atmospheric light is bright and lies towards the horizon/sky. Dim or low-lying "airlight"
        // (indoor white walls, snow in the foreground, night scenes) breaks the prior, so skip.
        if (aMin < MIN_AIRLIGHT) return null
        if (rowSum.toFloat() / count > AIRLIGHT_MAX_ROW * lh) return null

        // Haze level: median dark-channel ratio over the frame (≈ 1 − t for the typical pixel).
        val ratio = FloatArray(n) { (darkPatch[it] / aMin).coerceIn(0f, 1f) }
        val median = quantileSorted(ratio.copyOf().also { it.sort() }, 0.5f)
        val strength = config.dehaze * smoothstep(HAZE_START, HAZE_FULL, median)
        if (strength < 0.05f) return null

        val airY = KR * aR + KG * aG + KB * aB
        val t = FloatArray(n)
        for (i in 0 until n) {
            var ti = 1f - strength * 0.95f * ratio[i]
            // Sky / light sources close to the airlight: the prior overestimates haze there.
            val y = KR * r[i] + KG * g[i] + KB * b[i]
            ti += (1f - ti) * smoothstep(0.75f, 0.95f, y / max(airY, 1e-4f))
            t[i] = ti.coerceIn(MIN_TRANSMISSION, 1f)
        }
        val rad = max(2, max(lw, lh) / 40)
        val (a, bb) = guidedCoefficients(lPre, t, lw, lh, rad, TRANSMISSION_EPS)
        return Triple(a, bb, floatArrayOf(aR, aG, aB, strength))
    }

    /** Robust noise estimate (Immerkær Laplacian, 30th percentile) on sampled full-res rows. */
    private fun estimateNoise(source: ArgbRowSource): Pair<Float, Float> {
        val w = source.width
        val h = source.height
        if (w < 3 || h < 3) return 0f to 0f
        val rowsToSample = min(48, max(1, (h - 2) / 2))
        val stepX = max(1, (w - 2) / 1500)
        val buf = IntArray(w * 3)
        val all = FloatArray(rowsToSample * ((w - 2) / stepX + 1))
        val shadows = FloatArray(all.size)
        var nAll = 0
        var nShadow = 0
        for (k in 0 until rowsToSample) {
            val y = (1 + ((k + 0.5f) * (h - 2) / rowsToSample).toInt()).coerceIn(1, h - 2)
            source.readRows(y - 1, 3, buf)
            var x = 1
            while (x < w - 1) {
                var conv = 0f
                for (dy in 0..2) {
                    val row = dy * w
                    val wy = if (dy == 1) -2f else 1f
                    conv += wy * (gray(buf[row + x - 1]) + gray(buf[row + x + 1])) +
                        wy * -2f * gray(buf[row + x])
                }
                val a = abs(conv)
                all[nAll++] = a
                if (gray(buf[w + x]) < 0.3f) shadows[nShadow++] = a
                x += stepX
            }
        }
        val sAll = sigmaFromAbsLaplacian(all, nAll)
        val sShadow = if (nShadow >= 200) sigmaFromAbsLaplacian(shadows, nShadow) else sAll
        return sShadow.coerceIn(0f, 0.05f) to sAll.coerceIn(0f, 0.05f)
    }

    private fun sigmaFromAbsLaplacian(values: FloatArray, count: Int): Float {
        if (count == 0) return 0f
        val sorted = values.copyOf(count)
        sorted.sort()
        // For |N(0, 6σ)| the 30th percentile is 0.385 · 6σ.
        return quantileSorted(sorted, 0.30f) / (0.385f * 6f)
    }

    // ── Rendering ───────────────────────────────────────────────────────────

    private class Workspace(width: Int, lw: Int) {
        val capacity = width * (STRIP_ROWS + 2 * MARGIN)
        val argb = IntArray(capacity)
        val r = FloatArray(capacity)
        val g = FloatArray(capacity)
        val b = FloatArray(capacity)
        val yl = FloatArray(capacity)
        val p = FloatArray(capacity)
        val sig = FloatArray(capacity)
        val pd = FloatArray(capacity)
        val hs = FloatArray(capacity)
        val hs2 = FloatArray(capacity)
        val fm = FloatArray(capacity)
        val out = IntArray(width * STRIP_ROWS)
        val rowGrid = FloatArray(lw * GRID_CHANNELS)
    }

    private suspend fun render(
        source: ArgbRowSource,
        sink: ArgbRowSink,
        plan: Plan,
        workers: Int,
        onProgress: ((Float) -> Unit)?,
    ) = coroutineScope {
        val w = source.width
        val h = source.height
        val colIdx = IntArray(w)
        val colFrac = FloatArray(w)
        for (x in 0 until w) {
            val gx = (x + 0.5f) / plan.f - 0.5f
            var i0 = floor(gx).toInt()
            var fx = gx - i0
            if (i0 < 0) { i0 = 0; fx = 0f }
            if (i0 >= plan.lw - 1) { i0 = plan.lw - 1; fx = 0f }
            colIdx[x] = i0
            colFrac[x] = fx
        }
        val strips = (h + STRIP_ROWS - 1) / STRIP_ROWS
        val next = AtomicInteger(0)
        val done = AtomicInteger(0)
        (0 until min(workers, strips)).map {
            async {
                val ws = Workspace(w, plan.lw)
                while (true) {
                    val s = next.getAndIncrement()
                    if (s >= strips) break
                    ensureActive()
                    val y0 = s * STRIP_ROWS
                    renderStrip(source, sink, plan, y0, min(h, y0 + STRIP_ROWS), colIdx, colFrac, ws)
                    val d = done.incrementAndGet()
                    onProgress?.invoke(ANALYSIS_PROGRESS + (1f - ANALYSIS_PROGRESS) * d / strips)
                }
            }
        }.awaitAll()
    }

    private fun renderStrip(
        source: ArgbRowSource,
        sink: ArgbRowSink,
        plan: Plan,
        y0: Int,
        y1: Int,
        colIdx: IntArray,
        colFrac: FloatArray,
        ws: Workspace,
    ) {
        val w = source.width
        val h = source.height
        val top = max(0, y0 - MARGIN)
        val bottom = min(h, y1 + MARGIN)
        source.readRows(top, bottom - top, ws.argb)

        // Pass 1: colour, chroma denoise, tone mapping, vibrance.
        for (y in top until bottom) {
            interpolateGridRow(plan, y, ws.rowGrid)
            val rowOff = (y - top) * w
            for (x in 0 until w) {
                val i = rowOff + x
                tonePixel(plan, ws, i, ws.argb[i], colIdx[x], colFrac[x])
            }
        }

        // Pass 2: luminance denoise on rows y0-1 .. y1 (sharpening needs one extra row).
        val dTop = max(top, y0 - 1)
        val dBottom = min(bottom, y1 + 1)
        val pd = if (plan.denoiseRadius > 0) {
            denoiseRows(plan, ws, w, top, bottom, dTop, dBottom)
            ws.pd
        } else {
            ws.p
        }

        // Pass 3: sharpening, gamut handling, encode.
        for (y in y0 until y1) {
            val ry = y - top
            val up = (max(dTop, y - 1) - top) * w
            val mid = ry * w
            val dn = (min(dBottom - 1, y + 1) - top) * w
            val outOff = (y - y0) * w
            for (x in 0 until w) {
                val xl = if (x > 0) x - 1 else 0
                val xr = if (x < w - 1) x + 1 else w - 1
                val i = mid + x
                val c = pd[i]
                var ps = c
                if (plan.sharpenAmount > 0f) {
                    val n = pd[up + x]; val s = pd[dn + x]; val e = pd[mid + xr]; val wv = pd[mid + xl]
                    val nw = pd[up + xl]; val ne = pd[up + xr]; val sw = pd[dn + xl]; val se = pd[dn + xr]
                    val blur = (4f * c + 2f * (n + s + e + wv) + nw + ne + sw + se) * 0.0625f
                    val hp = c - blur
                    val excess = abs(hp) - (ws.sig[i] + 0.0015f)
                    if (excess > 0f) {
                        val mn = min(min(min(n, s), min(e, wv)), min(min(nw, ne), min(min(sw, se), c)))
                        val mx = max(max(max(n, s), max(e, wv)), max(max(nw, ne), max(max(sw, se), c)))
                        val boost = if (hp > 0f) excess else -excess
                        val amount = plan.sharpenAmount * (1f - FACE_SHARPEN_CUT * ws.fm[i])
                        ps = (c + amount * SHARPEN_GAIN * boost).coerceIn(mn - 0.003f, mx + 0.003f)
                    }
                }
                var r = ws.r[i]
                var g = ws.g[i]
                var b = ws.b[i]
                var yf = ws.yl[i]
                if (ps != ws.p[i]) {
                    val nyf = DEC.get(ps)
                    val scale = if (yf > 1e-7f) nyf / yf else 1f
                    r *= scale; g *= scale; b *= scale
                    yf = nyf
                }
                val mxc = max(r, max(g, b))
                if (mxc > 1f) {
                    if (yf >= 1f) {
                        r = 1f; g = 1f; b = 1f
                    } else {
                        // Desaturate towards the pixel's own luminance: hue and brightness kept.
                        val t = (1f - yf) / (mxc - yf)
                        r = yf + (r - yf) * t; g = yf + (g - yf) * t; b = yf + (b - yf) * t
                    }
                }
                ws.out[outOff + x] = OPAQUE or (encode8(r) shl 16) or (encode8(g) shl 8) or encode8(b)
            }
        }
        sink.writeRows(y0, y1 - y0, ws.out)
    }

    private fun interpolateGridRow(plan: Plan, y: Int, rowGrid: FloatArray) {
        val gy = (y + 0.5f) / plan.f - 0.5f
        var j0 = floor(gy).toInt()
        var fy = gy - j0
        if (j0 < 0) { j0 = 0; fy = 0f }
        if (j0 >= plan.lh - 1) { j0 = plan.lh - 1; fy = 0f }
        val j1 = min(j0 + 1, plan.lh - 1)
        val o0 = j0 * plan.lw * GRID_CHANNELS
        val o1 = j1 * plan.lw * GRID_CHANNELS
        val grids = plan.grids
        for (k in 0 until plan.lw * GRID_CHANNELS) {
            val a = grids[o0 + k]
            rowGrid[k] = a + (grids[o1 + k] - a) * fy
        }
    }

    private fun tonePixel(plan: Plan, ws: Workspace, i: Int, argb: Int, i0: Int, fx: Float) {
        var r = LIN[(argb shr 16) and 0xFF] * plan.gainR
        var g = LIN[(argb shr 8) and 0xFF] * plan.gainG
        var b = LIN[argb and 0xFF] * plan.gainB

        val rg = ws.rowGrid
        val o0 = i0 * GRID_CHANNELS
        val o1 = min(i0 + 1, plan.lw - 1) * GRID_CHANNELS

        // Haze removal with the scattering model; t comes from the edge-aware transmission map.
        var transmission = 1f
        if (plan.dehaze > 0f) {
            val lPre = log2(KR * r + KG * g + KB * b + EPS)
            transmission = (lerp(rg[o0 + 8], rg[o1 + 8], fx) * lPre + lerp(rg[o0 + 9], rg[o1 + 9], fx))
                .coerceIn(MIN_TRANSMISSION, 1f)
            if (transmission < 0.999f) {
                val inv = 1f / transmission
                r = max(0f, (r - plan.airR * (1f - transmission)) * inv)
                g = max(0f, (g - plan.airG * (1f - transmission)) * inv)
                b = max(0f, (b - plan.airB * (1f - transmission)) * inv)
            }
        }
        val faceM = lerp(rg[o0 + 10], rg[o1 + 10], fx)
        ws.fm[i] = faceM

        val y = KR * r + KG * g + KB * b
        val l = log2(y + EPS)
        val pOrig = ENC.get(y)

        val noiseHere = (plan.sigmaAll + (plan.sigmaShadow - plan.sigmaAll) * (1f - (pOrig / 0.3f).coerceIn(0f, 1f))) /
            transmission

        // Chroma denoise: move towards the guided low-res chromaticity only within the noise band.
        if (plan.chromaStrength > 0f && y > 1e-6f) {
            val s = r + g + b
            val cr = r / s
            val cg = g / s
            val crS = lerp(rg[o0 + 4], rg[o1 + 4], fx) * l + lerp(rg[o0 + 5], rg[o1 + 5], fx)
            val cgS = lerp(rg[o0 + 6], rg[o1 + 6], fx) * l + lerp(rg[o0 + 7], rg[o1 + 7], fx)
            val d = max(abs(cr - crS), abs(cg - cgS))
            // Chromaticity noise ≈ σ_gray / P (see docs/04); τ covers ~2.5σ of it.
            val tau = (2.4f * noiseHere / max(pOrig, 0.04f)).coerceIn(0.006f, 0.25f)
            val q = d / tau
            if (q < 2.5f) {
                val wgt = plan.chromaStrength * (1f - smoothstep(1f, 2.5f, q))
                val ncr = (cr + (crS - cr) * wgt).coerceAtLeast(0f)
                val ncg = (cg + (cgS - cg) * wgt).coerceAtLeast(0f)
                val ncb = (1f - ncr - ncg).coerceAtLeast(0f)
                val denom = KR * ncr + KG * ncg + KB * ncb
                if (denom > 1e-6f) {
                    val sum = y / denom
                    r = ncr * sum; g = ncg * sum; b = ncb * sum
                }
            }
        }

        // Tone mapping on the base layer; mid band boosted; fine detail kept.
        val base = lerp(rg[o0 + 0], rg[o1 + 0], fx) * l + lerp(rg[o0 + 1], rg[o1 + 1], fx)
        val mid = lerp(rg[o0 + 2], rg[o1 + 2], fx) * l + lerp(rg[o0 + 3], rg[o1 + 3], fx)
        val noiseFade = smoothstep(-9f, -6f, l)
        val highlightFade = 1f - 0.7f * smoothstep(-1f, 0f, base)
        // Faces keep their natural micro-contrast: clarity there would exaggerate skin texture.
        val k = plan.clarity * noiseFade * highlightFade * (1f - FACE_CLARITY_CUT * faceM)
        var lOut = toneCurve(plan, base) + (1f + k) * (mid - base) + (l - mid)
        if (plan.highlightStops > 0f && base > HIGHLIGHT_KNEE_STOPS && base < 0f) {
            // Bump that is 0 at the knee and at white, so bright areas darken; it also fades out
            // per pixel towards white so pixels that were clipped in the input stay exactly white.
            val t = (base - HIGHLIGHT_KNEE_STOPS) / -HIGHLIGHT_KNEE_STOPS
            lOut -= plan.highlightStops * 4f * t * (1f - t) * (1f - smoothstep(-0.08f, -0.005f, l))
        }
        val ratio = exp2(lOut - l)
        r *= ratio; g *= ratio; b *= ratio
        val yOut = y * ratio

        if (plan.vibrance > 0f && yOut > 1e-6f) {
            val mx = max(r, max(g, b))
            val mn = min(r, min(g, b))
            val sat = (mx - mn) / (mx + 1e-6f)
            var skin = 0f
            if (config.preserveSkinTones) {
                val s = r + g + b
                val dr = (r / s - 0.47f) / 0.06f
                val dg = (g / s - 0.31f) / 0.03f
                val q = dr * dr + dg * dg
                if (q < 4f) skin = 1f - q * 0.25f
            }
            val boost = 1f + plan.vibrance * (1f - sat) * (1f - 0.85f * skin)
            r = yOut + (r - yOut) * boost
            g = yOut + (g - yOut) * boost
            b = yOut + (b - yOut) * boost
            if (r < 0f) r = 0f
            if (g < 0f) g = 0f
            if (b < 0f) b = 0f
        }

        ws.r[i] = r
        ws.g[i] = g
        ws.b[i] = b
        ws.yl[i] = yOut
        val pOut = ENC.get(yOut)
        ws.p[i] = pOut
        val gainP = (pOut / max(pOrig, 1e-3f)).coerceIn(0.5f, 6f)
        ws.sig[i] = noiseHere * gainP
    }

    /** Base-layer tone curve in log2 stops. White (0) maps to white; monotonic by construction. */
    private fun toneCurve(plan: Plan, s: Float): Float {
        val u = (s / plan.anchor).coerceIn(0f, 1f)
        var out = s + plan.exposure * u * u * (3f - 2f * u)
        val lDist = plan.anchor - s
        if (lDist > 0f && plan.shadowC < 1f) {
            val d = plan.shadowDepth
            val q = if (lDist <= d) {
                lDist * lDist / (lDist + 1f)
            } else {
                val qd = d * d / (d + 1f)
                val slope = (d * d + 2f * d) / ((d + 1f) * (d + 1f))
                qd + slope * (1f - exp(-(lDist - d)))
            }
            out += (1f - plan.shadowC) * q
        }
        return out
    }

    /**
     * Local Wiener (Lee) filter on perceptual luminance: out = mean + k·(p − mean) with
     * k = max(0, var − σ²) / var over a (2r+1)² window, where σ is this pixel's noise after tone
     * mapping. Flat areas (variance ≈ noise) are smoothed; edges and texture (variance ≫ noise)
     * are kept. Cost is two short separable sums per pixel, independent of range weighting.
     */
    private fun denoiseRows(plan: Plan, ws: Workspace, w: Int, top: Int, bottom: Int, dTop: Int, dBottom: Int) {
        val rad = plan.denoiseRadius
        val p = ws.p
        val hs = ws.hs
        val hs2 = ws.hs2
        val rowFrom = max(top, dTop - rad)
        val rowTo = min(bottom, dBottom + rad)
        // Horizontal sums (edge pixels replicated).
        for (y in rowFrom until rowTo) {
            val o = (y - top) * w
            for (x in 0 until w) {
                var s1 = 0f
                var s2 = 0f
                for (dx in -rad..rad) {
                    val v = p[o + (x + dx).coerceIn(0, w - 1)]
                    s1 += v
                    s2 += v * v
                }
                hs[o + x] = s1
                hs2[o + x] = s2
            }
        }
        val invN = 1f / ((2 * rad + 1) * (2 * rad + 1))
        for (y in dTop until dBottom) {
            val o = (y - top) * w
            for (x in 0 until w) {
                var s1 = 0f
                var s2 = 0f
                for (dy in -rad..rad) {
                    val r = ((y + dy).coerceIn(rowFrom, rowTo - 1) - top) * w + x
                    s1 += hs[r]
                    s2 += hs2[r]
                }
                val i = o + x
                val c = p[i]
                val mean = s1 * invN
                val variance = max(0f, s2 * invN - mean * mean)
                val sigma = ws.sig[i]
                val noiseVar = sigma * sigma
                val k = if (variance > 1e-12f) max(0f, variance - noiseVar) / variance else 0f
                val filtered = mean + k * (c - mean)
                // Less smoothing on faces: noise reduction must not read as skin retouching.
                ws.pd[i] = c + plan.denoiseStrength * (1f - FACE_DENOISE_CUT * ws.fm[i]) * (filtered - c)
            }
        }
    }

    internal companion object {
        const val ANALYSIS_LONG_EDGE = 512
        const val STRIP_ROWS = 64
        const val MARGIN = 3
        const val GRID_CHANNELS = 11
        const val ANALYSIS_PROGRESS = 0.15f
        const val CLIP_LEVEL = 254
        const val TARGET_MEDIAN_STOPS = -2.6f
        const val HIGHLIGHT_CEILING_STOPS = -0.6f
        const val EPS = 1f / 4096f
        const val BASE_EPS = 0.25f
        const val MID_EPS = 0.09f
        const val CHROMA_RADIUS = 2
        const val CHROMA_EPS = 0.05f
        const val SHARPEN_GAIN = 1.6f
        const val MIN_FACE_AREA = 0.002f
        const val PORTRAIT_FACE_AREA = 0.015f
        const val TARGET_FACE_STOPS = -2.0f
        const val LOW_LIGHT_MEDIAN_STOPS = -5.0f
        const val HIGH_CONTRAST_RANGE_STOPS = 6.5f
        const val MAX_HIGHLIGHT_RECOVERY_STOPS = 0.45f
        const val HIGHLIGHT_KNEE_STOPS = -2.5f
        const val MIN_TRANSMISSION = 0.6f
        const val MIN_AIRLIGHT = 0.35f
        const val AIRLIGHT_MAX_ROW = 0.55f
        const val HAZE_START = 0.08f
        const val HAZE_FULL = 0.25f
        const val TRANSMISSION_EPS = 0.01f
        const val FACE_CLARITY_CUT = 0.65f
        const val FACE_SHARPEN_CUT = 0.6f
        const val FACE_DENOISE_CUT = 0.4f
        const val KR = 0.2126f
        const val KG = 0.7152f
        const val KB = 0.0722f
        const val THIRD = 1f / 3f
        const val OPAQUE = -0x1000000
        private const val INV_LN2 = 1.4426950408889634f
        private const val LN2 = 0.6931471805599453f

        val LIN = FloatArray(256) { srgbDecode(it / 255f) }
        val ENC = TableCurve(4096) { srgbEncode(it) }
        val DEC = TableCurve(4096) { srgbDecode(it) }
        private const val ENC8_SIZE = 8192
        private val ENC8 = IntArray(ENC8_SIZE + 1) { (srgbEncode(it / ENC8_SIZE.toFloat()) * 255f + 0.5f).toInt() }


        fun srgbDecode(v: Float): Float =
            if (v <= 0.04045f) v / 12.92f else ((v + 0.055f) / 1.055f).pow(2.4f)

        fun srgbEncode(v: Float): Float =
            if (v <= 0.0031308f) v * 12.92f else 1.055f * v.pow(1f / 2.4f) - 0.055f

        fun encode8(v: Float): Int = when {
            v <= 0f -> 0
            v >= 1f -> 255
            else -> ENC8[(v * ENC8_SIZE + 0.5f).toInt()]
        }

        /** log2 for v > 0 via exponent bits + atanh series (|error| < 2e-5); ln() is slow on ART. */
        fun log2(v: Float): Float {
            val bits = java.lang.Float.floatToRawIntBits(v)
            val e = ((bits shr 23) and 0xFF) - 127
            val m = java.lang.Float.intBitsToFloat((bits and 0x7FFFFF) or 0x3F800000)
            val t = (m - 1f) / (m + 1f)
            val t2 = t * t
            val lnM = 2f * t * (1f + t2 * (1f / 3f + t2 * (0.2f + t2 * (1f / 7f))))
            return e + lnM * INV_LN2
        }

        /** 2^v via exponent bits + degree-6 polynomial (relative error < 3e-6). */
        fun exp2(v: Float): Float {
            val x = v.coerceIn(-60f, 60f)
            val i = floor(x).toInt()
            val f = (x - i) * LN2
            val p = 1f + f * (1f + f * (0.5f + f * (1f / 6f + f * (1f / 24f + f * (1f / 120f + f * (1f / 720f))))))
            return java.lang.Float.intBitsToFloat((i + 127) shl 23) * p
        }

        private fun pow6(v: Float): Double {
            val d = v.toDouble()
            val d2 = d * d
            return d2 * d2 * d2
        }

        private fun gray(p: Int): Float =
            (((p shr 16) and 0xFF) + 2 * ((p shr 8) and 0xFF) + (p and 0xFF)) / 1020f

        @Suppress("NOTHING_TO_INLINE")
        inline fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

        fun smoothstep(e0: Float, e1: Float, x: Float): Float {
            val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
            return t * t * (3f - 2f * t)
        }

        fun quantileSorted(sorted: FloatArray, q: Float): Float {
            if (sorted.isEmpty()) return 0f
            val idx = (q * (sorted.size - 1)).roundToInt().coerceIn(0, sorted.size - 1)
            return sorted[idx]
        }

        /** Separable min filter over a (2r+1)² window (edges clamped). */
        fun minFilter(src: FloatArray, w: Int, h: Int, r: Int): FloatArray {
            val tmp = FloatArray(w * h)
            val out = FloatArray(w * h)
            for (y in 0 until h) {
                val o = y * w
                for (x in 0 until w) {
                    var m = Float.MAX_VALUE
                    for (k in max(0, x - r)..min(w - 1, x + r)) m = min(m, src[o + k])
                    tmp[o + x] = m
                }
            }
            for (x in 0 until w) {
                for (y in 0 until h) {
                    var m = Float.MAX_VALUE
                    for (k in max(0, y - r)..min(h - 1, y + r)) m = min(m, tmp[k * w + x])
                    out[y * w + x] = m
                }
            }
            return out
        }

        /** Mean box filter with edge-normalised windows (separable prefix sums). */
        fun boxMean(src: FloatArray, w: Int, h: Int, r: Int): FloatArray {
            val tmp = FloatArray(w * h)
            val out = FloatArray(w * h)
            val prefix = DoubleArray(max(w, h) + 1)
            for (y in 0 until h) {
                val o = y * w
                for (x in 0 until w) prefix[x + 1] = prefix[x] + src[o + x]
                for (x in 0 until w) {
                    val a = max(0, x - r)
                    val b = min(w - 1, x + r)
                    tmp[o + x] = ((prefix[b + 1] - prefix[a]) / (b - a + 1)).toFloat()
                }
            }
            for (x in 0 until w) {
                for (y in 0 until h) prefix[y + 1] = prefix[y] + tmp[y * w + x]
                for (y in 0 until h) {
                    val a = max(0, y - r)
                    val b = min(h - 1, y + r)
                    out[y * w + x] = ((prefix[b + 1] - prefix[a]) / (b - a + 1)).toFloat()
                }
            }
            return out
        }

        /** Guided filter (He et al.) returning the smoothed linear coefficients (mean a, mean b). */
        fun guidedCoefficients(
            guide: FloatArray,
            input: FloatArray,
            w: Int,
            h: Int,
            r: Int,
            eps: Float,
        ): Pair<FloatArray, FloatArray> {
            val n = w * h
            val meanI = boxMean(guide, w, h, r)
            val meanP = boxMean(input, w, h, r)
            val corrIp = boxMean(FloatArray(n) { guide[it] * input[it] }, w, h, r)
            val corrII = boxMean(FloatArray(n) { guide[it] * guide[it] }, w, h, r)
            val a = FloatArray(n)
            val b = FloatArray(n)
            for (i in 0 until n) {
                val varI = max(0f, corrII[i] - meanI[i] * meanI[i])
                val cov = corrIp[i] - meanI[i] * meanP[i]
                a[i] = cov / (varI + eps)
                b[i] = meanP[i] - a[i] * meanI[i]
            }
            return boxMean(a, w, h, r) to boxMean(b, w, h, r)
        }
    }
}

/** Monotonic function sampled on [0, 1] with linear interpolation. */
class TableCurve(private val size: Int, fn: (Float) -> Float) {
    private val table = FloatArray(size + 1) { fn(it / size.toFloat()) }

    fun get(x: Float): Float {
        if (x <= 0f) return table[0]
        if (x >= 1f) return table[size]
        val f = x * size
        val i = f.toInt()
        val a = table[i]
        return a + (table[i + 1] - a) * (f - i)
    }
}
