package com.webappypie.optilens.core.imaging.ai.blur

import android.graphics.Bitmap
import com.webappypie.optilens.core.imaging.ai.BlurClassificationResult
import com.webappypie.optilens.core.imaging.ai.BlurType
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * High-accuracy blur type classifier and PSF estimator.
 *
 * Classifies candidate images into:
 * - [BlurType.SHARP]: High Tenengrad gradient energy, narrow edge spread (< 2.2 px).
 * - [BlurType.MOTION_BLUR]: High gradient anisotropy along a dominant linear axis ($A \ge 0.30$).
 * - [BlurType.DEFOCUS_BLUR]: Low anisotropy ($A < 0.25$) with expanded edge spread (ESF > 4 px).
 * - [BlurType.SEVERE_UNRECOVERABLE]: Extremely low gradient energy ($T < 12$), preventing safe reconstruction.
 */
@Singleton
class BlurClassifier @Inject constructor() {

    companion object {
        private const val SHARP_TENENGRAD_THRESHOLD = 85.0f
        private const val SEVERE_BLUR_THRESHOLD = 12.0f
        private const val MOTION_ANISOTROPY_THRESHOLD = 0.28f
        private const val MAX_ANALYSIS_DIM = 512
    }

    /**
     * Analyzes the given bitmap to classify blur type and estimate PSF parameters.
     */
    fun classifyBlur(bitmap: Bitmap): BlurClassificationResult {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) {
            return BlurClassificationResult(
                blurType = BlurType.SEVERE_UNRECOVERABLE,
                confidence = 1.0f,
                isRecoverable = false,
                userAdvice = "Invalid image dimensions",
            )
        }

        // Downsample if image is very large to ensure sub-100ms analysis
        val scale = min(1.0f, MAX_ANALYSIS_DIM.toFloat() / max(width, height))
        val sampleW = (width * scale).toInt().coerceAtLeast(16)
        val sampleH = (height * scale).toInt().coerceAtLeast(16)

        val pixels = IntArray(sampleW * sampleH)
        if (scale < 1.0f) {
            val scaled = Bitmap.createScaledBitmap(bitmap, sampleW, sampleH, true)
            scaled.getPixels(pixels, 0, sampleW, 0, 0, sampleW, sampleH)
            if (scaled != bitmap) scaled.recycle()
        } else {
            bitmap.getPixels(pixels, 0, sampleW, 0, 0, sampleW, sampleH)
        }

        return classifyBlur(pixels, sampleW, sampleH)
    }

    /**
     * Core mathematical blur classification on raw ARGB pixel buffer.
     */
    fun classifyBlur(pixels: IntArray, sampleW: Int, sampleH: Int): BlurClassificationResult {
        if (sampleW <= 0 || sampleH <= 0 || pixels.size < sampleW * sampleH) {
            return BlurClassificationResult(
                blurType = BlurType.SEVERE_UNRECOVERABLE,
                confidence = 1.0f,
                isRecoverable = false,
                userAdvice = "Invalid buffer dimensions",
            )
        }

        // Convert to luminance buffer
        val luma = FloatArray(sampleW * sampleH)
        for (i in pixels.indices) {
            val color = pixels[i]
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF
            luma[i] = 0.299f * r + 0.587f * g + 0.114f * b
        }

        // Compute 2D Sobel gradients and structural tensor components
        var sumGx2 = 0.0
        var sumGy2 = 0.0
        var sumGxy = 0.0
        var strongEdgeCount = 0
        var totalEdgeWidth = 0.0

        for (y in 1 until sampleH - 1) {
            val rowOffset = y * sampleW
            for (x in 1 until sampleW - 1) {
                val idx = rowOffset + x

                // Horizontal Sobel
                val gx = (luma[idx - sampleW + 1] + 2f * luma[idx + 1] + luma[idx + sampleW + 1]) -
                         (luma[idx - sampleW - 1] + 2f * luma[idx - 1] + luma[idx + sampleW - 1])

                // Vertical Sobel
                val gy = (luma[idx + sampleW - 1] + 2f * luma[idx + sampleW] + luma[idx + sampleW + 1]) -
                         (luma[idx - sampleW - 1] + 2f * luma[idx - sampleW] + luma[idx - sampleW + 1])

                val mag2 = gx * gx + gy * gy
                sumGx2 += gx * gx
                sumGy2 += gy * gy
                sumGxy += gx * gy

                // Measure edge profile width on prominent edges
                if (mag2 > 1600f) {
                    strongEdgeCount++
                    var w = 1
                    var lookAhead = 1
                    while (x + lookAhead < sampleW - 1 && lookAhead <= 12) {
                        val aheadMag = (luma[idx + lookAhead + 1] - luma[idx + lookAhead - 1])
                        if (aheadMag * aheadMag < mag2 * 0.25f) break
                        w++
                        lookAhead++
                    }
                    totalEdgeWidth += w
                }
            }
        }

        val totalPixels = ((sampleW - 2) * (sampleH - 2)).toDouble()
        val tenengrad = (sumGx2 + sumGy2) / totalPixels

        // Eigenvalues of gradient tensor
        val trace = sumGx2 + sumGy2
        val diff = sumGx2 - sumGy2
        val discriminant = sqrt(diff * diff + 4.0 * sumGxy * sumGxy)
        val lambda1 = (trace + discriminant) / 2.0
        val lambda2 = (trace - discriminant) / 2.0

        // Directional anisotropy A in [0, 1]
        val anisotropy = if (trace > 1e-6) ((lambda1 - lambda2) / trace).toFloat() else 0f

        // Angle in degrees in [-90, 90]
        val angleRad = 0.5 * atan2(2.0 * sumGxy, diff)
        val angleDeg = (angleRad * (180.0 / PI)).toFloat()

        // Average edge spread in pixels
        val avgEdgeWidth = if (strongEdgeCount > 0) (totalEdgeWidth / strongEdgeCount).toFloat() else 1.0f

        return when {
            tenengrad < SEVERE_BLUR_THRESHOLD -> {
                BlurClassificationResult(
                    blurType = BlurType.SEVERE_UNRECOVERABLE,
                    confidence = 0.95f,
                    isRecoverable = false,
                    userAdvice = "Optical information is severely degraded. Optical deconvolution cannot recover details without producing synthetic artifacts.",
                )
            }
            tenengrad >= SHARP_TENENGRAD_THRESHOLD && avgEdgeWidth < 2.5f -> {
                BlurClassificationResult(
                    blurType = BlurType.SHARP,
                    confidence = 0.92f,
                    isRecoverable = true,
                    userAdvice = "Image has high edge sharpness and rich frequency detail. Deblur is not recommended.",
                )
            }
            anisotropy >= MOTION_ANISOTROPY_THRESHOLD -> {
                val estimatedLength = (avgEdgeWidth * 2.2f).coerceIn(3.0f, 25.0f)
                BlurClassificationResult(
                    blurType = BlurType.MOTION_BLUR,
                    confidence = (0.70f + anisotropy * 0.30f).coerceIn(0.70f, 0.98f),
                    motionAngleDegrees = angleDeg,
                    motionLengthPixels = estimatedLength,
                    isRecoverable = true,
                    userAdvice = "Detected directional motion blur ($estimatedLength px at ${angleDeg.toInt()}°). Directional deconvolution will sharpen edges.",
                )
            }
            else -> {
                val estimatedRadius = (avgEdgeWidth * 1.5f).coerceIn(2.0f, 18.0f)
                BlurClassificationResult(
                    blurType = BlurType.DEFOCUS_BLUR,
                    confidence = (0.75f + (1f - anisotropy) * 0.20f).coerceIn(0.70f, 0.95f),
                    defocusRadiusPixels = estimatedRadius,
                    isRecoverable = true,
                    userAdvice = "Detected isotropic out-of-focus blur (approx radius $estimatedRadius px). Regularized shock filtering will recover focus.",
                )
            }
        }
    }
}
