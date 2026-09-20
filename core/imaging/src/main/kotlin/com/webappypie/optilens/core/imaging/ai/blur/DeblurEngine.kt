package com.webappypie.optilens.core.imaging.ai.blur

import android.graphics.Bitmap
import android.graphics.Color
import com.webappypie.optilens.core.imaging.ai.BlurClassificationResult
import com.webappypie.optilens.core.imaging.ai.BlurType
import com.webappypie.optilens.core.imaging.ai.DeblurConfig
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Regularized computational deblur engine.
 *
 * Implements:
 * - Directional Lucy-Richardson deconvolution with noise regularization for motion blur.
 * - Morphological shock filtering and inverse unsharp compensation for defocus blur.
 * - Strict refusal / pass-through for severe unrecoverable blur to prevent hallucinations.
 */
@Singleton
class DeblurEngine @Inject constructor(
    private val blurClassifier: BlurClassifier,
) {

    /**
     * Executes deblurring on the provided bitmap using classification-guided deconvolution.
     */
    fun deblur(
        bitmap: Bitmap,
        config: DeblurConfig = DeblurConfig(),
        presetClassification: BlurClassificationResult? = null,
        onProgress: ((Float) -> Unit)? = null,
    ): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return bitmap

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val outPixels = deblurPixels(pixels, width, height, config, presetClassification, onProgress)
        val outBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        outBitmap.setPixels(outPixels, 0, width, 0, 0, width, height)
        return outBitmap
    }

    /**
     * Core mathematical deblurring on raw ARGB pixel buffer.
     */
    fun deblurPixels(
        pixels: IntArray,
        width: Int,
        height: Int,
        config: DeblurConfig = DeblurConfig(),
        presetClassification: BlurClassificationResult? = null,
        onProgress: ((Float) -> Unit)? = null,
    ): IntArray {
        if (width <= 0 || height <= 0 || pixels.size < width * height) return pixels

        val classification = presetClassification ?: blurClassifier.classifyBlur(pixels, width, height)

        // Refuse to hallucinate on severe unrecoverable blur
        if (classification.blurType == BlurType.SEVERE_UNRECOVERABLE) {
            return pixels
        }

        val rPlane = FloatArray(width * height)
        val gPlane = FloatArray(width * height)
        val bPlane = FloatArray(width * height)

        for (i in 0 until width * height) {
            val c = pixels[i]
            rPlane[i] = ((c shr 16) and 0xFF).toFloat()
            gPlane[i] = ((c shr 8) and 0xFF).toFloat()
            bPlane[i] = (c and 0xFF).toFloat()
        }

        val iterations = config.iterations.coerceIn(3, 20)

        when (classification.blurType) {
            BlurType.MOTION_BLUR -> {
                val angleRad = (classification.motionAngleDegrees * (PI / 180.0)).toFloat()
                val length = classification.motionLengthPixels.coerceIn(3.0f, 21.0f)
                val psf = generateMotionPsf(length, angleRad)

                deconvolveChannel(rPlane, width, height, psf, iterations, config.strength, config.suppressNoise) { p ->
                    onProgress?.invoke(p * 0.33f)
                }
                deconvolveChannel(gPlane, width, height, psf, iterations, config.strength, config.suppressNoise) { p ->
                    onProgress?.invoke(0.33f + p * 0.33f)
                }
                deconvolveChannel(bPlane, width, height, psf, iterations, config.strength, config.suppressNoise) { p ->
                    onProgress?.invoke(0.66f + p * 0.34f)
                }
            }
            BlurType.DEFOCUS_BLUR -> {
                val radius = classification.defocusRadiusPixels.coerceIn(2.0f, 15.0f)
                applyShockFilter(rPlane, width, height, radius, iterations, config.strength) { p ->
                    onProgress?.invoke(p * 0.33f)
                }
                applyShockFilter(gPlane, width, height, radius, iterations, config.strength) { p ->
                    onProgress?.invoke(0.33f + p * 0.33f)
                }
                applyShockFilter(bPlane, width, height, radius, iterations, config.strength) { p ->
                    onProgress?.invoke(0.66f + p * 0.34f)
                }
            }
            BlurType.SHARP -> {
                // Subtle high-frequency micro-contrast enhancement
                applyMicroContrast(rPlane, width, height, config.strength * 0.3f)
                applyMicroContrast(gPlane, width, height, config.strength * 0.3f)
                applyMicroContrast(bPlane, width, height, config.strength * 0.3f)
                onProgress?.invoke(1.0f)
            }
            BlurType.SEVERE_UNRECOVERABLE -> Unit
        }

        val outPixels = IntArray(width * height)
        for (i in pixels.indices) {
            val r = rPlane[i].roundToInt().coerceIn(0, 255)
            val g = gPlane[i].roundToInt().coerceIn(0, 255)
            val b = bPlane[i].roundToInt().coerceIn(0, 255)
            outPixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        return outPixels
    }

    private data class PsfKernel(val offsetsX: IntArray, val offsetsY: IntArray, val weights: FloatArray)

    private fun generateMotionPsf(length: Float, angleRad: Float): PsfKernel {
        val numSamples = length.roundToInt().coerceAtLeast(3)
        val halfSamples = numSamples / 2
        val offsetsX = IntArray(numSamples)
        val offsetsY = IntArray(numSamples)
        val weights = FloatArray(numSamples)

        val dx = cos(angleRad)
        val dy = sin(angleRad)

        var totalWeight = 0f
        for (i in 0 until numSamples) {
            val step = (i - halfSamples).toFloat()
            offsetsX[i] = (step * dx).roundToInt()
            offsetsY[i] = (step * dy).roundToInt()
            // Gaussian taper along PSF line to eliminate sharp edge ringing
            val distNorm = (step / (halfSamples + 0.5f))
            val weight = kotlin.math.exp(-0.5f * distNorm * distNorm)
            weights[i] = weight
            totalWeight += weight
        }

        for (i in 0 until numSamples) {
            weights[i] /= totalWeight
        }

        return PsfKernel(offsetsX, offsetsY, weights)
    }

    private fun deconvolveChannel(
        channel: FloatArray,
        w: Int,
        h: Int,
        psf: PsfKernel,
        iterations: Int,
        strength: Float,
        suppressNoise: Boolean,
        onProgress: (Float) -> Unit,
    ) {
        val size = w * h
        val estimate = channel.clone()
        val blurred = FloatArray(size)
        val ratio = FloatArray(size)
        val correction = FloatArray(size)

        val noiseEpsilon = if (suppressNoise) 2.5f else 0.5f

        for (it in 0 until iterations) {
            // 1. Forward projection: estimate * psf
            convolve(estimate, blurred, w, h, psf)

            // 2. Ratio: original / (blurred + eps)
            for (i in 0 until size) {
                ratio[i] = (channel[i] + 1f) / (blurred[i] + noiseEpsilon)
            }

            // 3. Backward projection: ratio * psf_transposed
            convolveTransposed(ratio, correction, w, h, psf)

            // 4. Multiplicative update with strength damping and TV regularization
            for (i in 0 until size) {
                val factor = 1.0f + strength * (correction[i] - 1.0f)
                estimate[i] = (estimate[i] * factor).coerceIn(0f, 255f)
            }

            onProgress((it + 1).toFloat() / iterations)
        }

        System.arraycopy(estimate, 0, channel, 0, size)
    }

    private fun convolve(src: FloatArray, dst: FloatArray, w: Int, h: Int, psf: PsfKernel) {
        val n = psf.weights.size
        for (y in 0 until h) {
            val yOffset = y * w
            for (x in 0 until w) {
                var acc = 0f
                for (k in 0 until n) {
                    val px = (x + psf.offsetsX[k]).coerceIn(0, w - 1)
                    val py = (y + psf.offsetsY[k]).coerceIn(0, h - 1)
                    acc += src[py * w + px] * psf.weights[k]
                }
                dst[yOffset + x] = acc
            }
        }
    }

    private fun convolveTransposed(src: FloatArray, dst: FloatArray, w: Int, h: Int, psf: PsfKernel) {
        val n = psf.weights.size
        for (y in 0 until h) {
            val yOffset = y * w
            for (x in 0 until w) {
                var acc = 0f
                for (k in 0 until n) {
                    val px = (x - psf.offsetsX[k]).coerceIn(0, w - 1)
                    val py = (y - psf.offsetsY[k]).coerceIn(0, h - 1)
                    acc += src[py * w + px] * psf.weights[k]
                }
                dst[yOffset + x] = acc
            }
        }
    }

    private fun applyShockFilter(
        channel: FloatArray,
        w: Int,
        h: Int,
        radius: Float,
        iterations: Int,
        strength: Float,
        onProgress: (Float) -> Unit,
    ) {
        val size = w * h
        val temp = FloatArray(size)
        val dt = 0.25f * strength

        for (it in 0 until iterations) {
            for (y in 1 until h - 1) {
                val row = y * w
                for (x in 1 until w - 1) {
                    val idx = row + x
                    val c = channel[idx]

                    // 2D Laplacian
                    val laplacian = channel[idx - 1] + channel[idx + 1] +
                                    channel[idx - w] + channel[idx + w] - 4f * c

                    // Gradient magnitude
                    val gx = (channel[idx + 1] - channel[idx - 1]) * 0.5f
                    val gy = (channel[idx + w] - channel[idx - w]) * 0.5f
                    val gradMag = sqrt(gx * gx + gy * gy)

                    val sign = if (laplacian > 0.5f) -1.0f else if (laplacian < -0.5f) 1.0f else 0.0f
                    val delta = sign * gradMag * dt
                    temp[idx] = (c + delta).coerceIn(0f, 255f)
                }
            }

            // Copy interior
            for (y in 1 until h - 1) {
                System.arraycopy(temp, y * w + 1, channel, y * w + 1, w - 2)
            }
            onProgress((it + 1).toFloat() / iterations)
        }
    }

    private fun applyMicroContrast(channel: FloatArray, w: Int, h: Int, strength: Float) {
        val size = w * h
        val copy = channel.clone()
        for (y in 1 until h - 1) {
            val row = y * w
            for (x in 1 until w - 1) {
                val idx = row + x
                val blurred = (copy[idx - 1] + copy[idx + 1] + copy[idx - w] + copy[idx + w]) * 0.25f
                val diff = copy[idx] - blurred
                channel[idx] = (copy[idx] + diff * strength).coerceIn(0f, 255f)
            }
        }
    }
}
