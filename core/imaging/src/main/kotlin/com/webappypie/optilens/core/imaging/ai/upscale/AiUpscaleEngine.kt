package com.webappypie.optilens.core.imaging.ai.upscale

import android.graphics.Bitmap
import com.webappypie.optilens.core.imaging.ai.UpscaleConfig
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * High-performance edge-directed super-resolution upscaler for 2x and 4x magnifications.
 *
 * Implements directional sub-pixel edge interpolation (EDI) combined with high-frequency
 * residual detail sharpening to prevent bilinear blurring and pixelated staircase artifacts.
 */
@Singleton
class AiUpscaleEngine @Inject constructor() {

    /**
     * Upscales [bitmap] by the factor specified in [config] (2x or 4x).
     */
    fun upscale(
        bitmap: Bitmap,
        config: UpscaleConfig = UpscaleConfig(),
        onProgress: ((Float) -> Unit)? = null,
    ): Bitmap {
        val inWidth = bitmap.width
        val inHeight = bitmap.height
        if (inWidth <= 0 || inHeight <= 0) return bitmap

        val inPixels = IntArray(inWidth * inHeight)
        bitmap.getPixels(inPixels, 0, inWidth, 0, 0, inWidth, inHeight)

        val (outPixels, dims) = upscalePixels(inPixels, inWidth, inHeight, config, onProgress)
        val outBitmap = Bitmap.createBitmap(dims.first, dims.second, Bitmap.Config.ARGB_8888)
        outBitmap.setPixels(outPixels, 0, dims.first, 0, 0, dims.first, dims.second)
        return outBitmap
    }

    /**
     * Core mathematical upscaler on raw ARGB pixel buffer.
     * Returns Pair(outPixels, Pair(outWidth, outHeight)).
     */
    fun upscalePixels(
        inPixels: IntArray,
        inWidth: Int,
        inHeight: Int,
        config: UpscaleConfig = UpscaleConfig(),
        onProgress: ((Float) -> Unit)? = null,
    ): Pair<IntArray, Pair<Int, Int>> {
        val scaleFactor = config.scaleFactor.coerceIn(2, 4)

        return if (scaleFactor == 4) {
            onProgress?.invoke(0.10f)
            val intermediate = upscalePixels2x(inPixels, inWidth, inHeight, config.enhanceMicroContrast) { p ->
                onProgress?.invoke(0.10f + p * 0.40f)
            }
            val result = upscalePixels2x(intermediate, inWidth * 2, inHeight * 2, config.enhanceMicroContrast) { p ->
                onProgress?.invoke(0.50f + p * 0.45f)
            }
            onProgress?.invoke(1.0f)
            Pair(result, Pair(inWidth * 4, inHeight * 4))
        } else {
            val result = upscalePixels2x(inPixels, inWidth, inHeight, config.enhanceMicroContrast) { p ->
                onProgress?.invoke(p)
            }
            Pair(result, Pair(inWidth * 2, inHeight * 2))
        }
    }

    private fun upscalePixels2x(
        inPixels: IntArray,
        w: Int,
        h: Int,
        enhanceDetail: Boolean,
        onProgress: (Float) -> Unit,
    ): IntArray {
        val outW = w * 2
        val outH = h * 2

        val inR = FloatArray(w * h)
        val inG = FloatArray(w * h)
        val inB = FloatArray(w * h)
        val inLuma = FloatArray(w * h)

        for (i in inPixels.indices) {
            val c = inPixels[i]
            val r = ((c shr 16) and 0xFF).toFloat()
            val g = ((c shr 8) and 0xFF).toFloat()
            val b = (c and 0xFF).toFloat()
            inR[i] = r
            inG[i] = g
            inB[i] = b
            inLuma[i] = 0.299f * r + 0.587f * g + 0.114f * b
        }

        val outR = FloatArray(outW * outH)
        val outG = FloatArray(outW * outH)
        val outB = FloatArray(outW * outH)

        // 1. Map existing original samples to even grid coordinates (2x, 2y)
        for (y in 0 until h) {
            val srcRow = y * w
            val dstRow = (y * 2) * outW
            for (x in 0 until w) {
                val srcIdx = srcRow + x
                val dstIdx = dstRow + (x * 2)
                outR[dstIdx] = inR[srcIdx]
                outG[dstIdx] = inG[srcIdx]
                outB[dstIdx] = inB[srcIdx]
            }
        }

        onProgress(0.30f)

        // 2. Interpolate center diagonal points (2x + 1, 2y + 1) using directional gradients
        for (y in 0 until h - 1) {
            val dstRow = (y * 2 + 1) * outW
            for (x in 0 until w - 1) {
                val dstIdx = dstRow + (x * 2 + 1)

                // 4 diagonal corners from input grid
                val p00 = y * w + x
                val p01 = y * w + (x + 1)
                val p10 = (y + 1) * w + x
                val p11 = (y + 1) * w + (x + 1)

                // Diagonal gradient comparison: |p00 - p11| vs |p01 - p10|
                val diffDiag1 = abs(inLuma[p00] - inLuma[p11])
                val diffDiag2 = abs(inLuma[p01] - inLuma[p10])

                if (diffDiag1 < diffDiag2 * 0.75f) {
                    // Edge follows diagonal 1 (top-left to bottom-right)
                    outR[dstIdx] = (inR[p00] + inR[p11]) * 0.5f
                    outG[dstIdx] = (inG[p00] + inG[p11]) * 0.5f
                    outB[dstIdx] = (inB[p00] + inB[p11]) * 0.5f
                } else if (diffDiag2 < diffDiag1 * 0.75f) {
                    // Edge follows diagonal 2 (top-right to bottom-left)
                    outR[dstIdx] = (inR[p01] + inR[p10]) * 0.5f
                    outG[dstIdx] = (inG[p01] + inG[p10]) * 0.5f
                    outB[dstIdx] = (inB[p01] + inB[p10]) * 0.5f
                } else {
                    // Flat / non-directional region: 4-tap average
                    outR[dstIdx] = (inR[p00] + inR[p01] + inR[p10] + inR[p11]) * 0.25f
                    outG[dstIdx] = (inG[p00] + inG[p01] + inG[p10] + inG[p11]) * 0.25f
                    outB[dstIdx] = (inB[p00] + inB[p01] + inB[p10] + inB[p11]) * 0.25f
                }
            }
        }

        onProgress(0.60f)

        // 3. Interpolate remaining cross points (horizontal & vertical midpoints)
        for (y in 0 until outH) {
            val row = y * outW
            for (x in 0 until outW) {
                val idx = row + x
                if ((x % 2 == 1 && y % 2 == 0) || (x % 2 == 0 && y % 2 == 1)) {
                    val left = if (x > 0) idx - 1 else idx + 1
                    val right = if (x < outW - 1) idx + 1 else idx - 1
                    val top = if (y > 0) idx - outW else idx + outW
                    val bottom = if (y < outH - 1) idx + outW else idx - outW

                    val diffH = abs(outG[left] - outG[right])
                    val diffV = abs(outG[top] - outG[bottom])

                    if (diffH < diffV * 0.75f) {
                        outR[idx] = (outR[left] + outR[right]) * 0.5f
                        outG[idx] = (outG[left] + outG[right]) * 0.5f
                        outB[idx] = (outB[left] + outB[right]) * 0.5f
                    } else if (diffV < diffH * 0.75f) {
                        outR[idx] = (outR[top] + outR[bottom]) * 0.5f
                        outG[idx] = (outG[top] + outG[bottom]) * 0.5f
                        outB[idx] = (outB[top] + outB[bottom]) * 0.5f
                    } else {
                        outR[idx] = (outR[left] + outR[right] + outR[top] + outR[bottom]) * 0.25f
                        outG[idx] = (outG[left] + outG[right] + outG[top] + outG[bottom]) * 0.25f
                        outB[idx] = (outB[left] + outB[right] + outB[top] + outB[bottom]) * 0.25f
                    }
                }
            }
        }

        // Fill rightmost column and bottom row edge duplicates
        for (y in 0 until outH) {
            val lastIdx = y * outW + (outW - 1)
            outR[lastIdx] = outR[lastIdx - 1]
            outG[lastIdx] = outG[lastIdx - 1]
            outB[lastIdx] = outB[lastIdx - 1]
        }
        val lastRow = (outH - 1) * outW
        val prevRow = (outH - 2) * outW
        for (x in 0 until outW) {
            outR[lastRow + x] = outR[prevRow + x]
            outG[lastRow + x] = outG[prevRow + x]
            outB[lastRow + x] = outB[prevRow + x]
        }

        onProgress(0.85f)

        // 4. High-frequency unsharp contrast restoration
        if (enhanceDetail) {
            val outCopyR = outR.clone()
            val outCopyG = outG.clone()
            val outCopyB = outB.clone()

            for (y in 1 until outH - 1) {
                val row = y * outW
                for (x in 1 until outW - 1) {
                    val idx = row + x
                    val blurR = (outCopyR[idx - 1] + outCopyR[idx + 1] + outCopyR[idx - outW] + outCopyR[idx + outW]) * 0.25f
                    val blurG = (outCopyG[idx - 1] + outCopyG[idx + 1] + outCopyG[idx - outW] + outCopyG[idx + outW]) * 0.25f
                    val blurB = (outCopyB[idx - 1] + outCopyB[idx + 1] + outCopyB[idx - outW] + outCopyB[idx + outW]) * 0.25f

                    val diffR = (outCopyR[idx] - blurR).coerceIn(-24f, 24f)
                    val diffG = (outCopyG[idx] - blurG).coerceIn(-24f, 24f)
                    val diffB = (outCopyB[idx] - blurB).coerceIn(-24f, 24f)

                    outR[idx] = (outCopyR[idx] + diffR * 0.45f).coerceIn(0f, 255f)
                    outG[idx] = (outCopyG[idx] + diffG * 0.45f).coerceIn(0f, 255f)
                    outB[idx] = (outCopyB[idx] + diffB * 0.45f).coerceIn(0f, 255f)
                }
            }
        }

        val outPixels = IntArray(outW * outH)
        for (i in 0 until outW * outH) {
            val r = outR[i].roundToInt().coerceIn(0, 255)
            val g = outG[i].roundToInt().coerceIn(0, 255)
            val b = outB[i].roundToInt().coerceIn(0, 255)
            outPixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }

        onProgress(1.0f)
        return outPixels
    }
}
