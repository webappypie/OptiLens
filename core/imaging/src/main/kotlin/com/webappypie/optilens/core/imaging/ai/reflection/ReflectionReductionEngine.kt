package com.webappypie.optilens.core.imaging.ai.reflection

import android.graphics.Bitmap
import com.webappypie.optilens.core.imaging.ai.ReflectionConfig
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Optical reflection and glass glare suppression engine.
 *
 * Decomposes observed image $I = T + R$ into transmission ($T$) and reflection ($R$) components,
 * attenuating semi-transparent ghosting, surface veiling glare, and specular haze while restoring
 * the contrast and saturation of the underlying scene.
 */
@Singleton
class ReflectionReductionEngine @Inject constructor() {

    /**
     * Attenuates glass reflections and surface flare on the target bitmap.
     */
    fun reduceReflections(
        bitmap: Bitmap,
        config: ReflectionConfig = ReflectionConfig(),
        onProgress: ((Float) -> Unit)? = null,
    ): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return bitmap

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val outPixels = reduceReflectionsPixels(pixels, width, height, config, onProgress)
        val outBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        outBitmap.setPixels(outPixels, 0, width, 0, 0, width, height)
        return outBitmap
    }

    /**
     * Core mathematical reflection reduction on raw ARGB pixel buffer.
     */
    fun reduceReflectionsPixels(
        pixels: IntArray,
        width: Int,
        height: Int,
        config: ReflectionConfig = ReflectionConfig(),
        onProgress: ((Float) -> Unit)? = null,
    ): IntArray {
        val size = width * height
        if (width <= 0 || height <= 0 || pixels.size < size) return pixels

        val rChan = FloatArray(size)
        val gChan = FloatArray(size)
        val bChan = FloatArray(size)
        val luma = FloatArray(size)

        for (i in 0 until size) {
            val c = pixels[i]
            val r = ((c shr 16) and 0xFF).toFloat()
            val g = ((c shr 8) and 0xFF).toFloat()
            val b = (c and 0xFF).toFloat()
            rChan[i] = r
            gChan[i] = g
            bChan[i] = b
            luma[i] = 0.299f * r + 0.587f * g + 0.114f * b
        }

        onProgress?.invoke(0.20f)

        // 1. Extract low-frequency reflection veil using a wide box/Gaussian blur
        val lowFreqLuma = computeLowFrequencyVeil(luma, width, height, radius = 12)
        onProgress?.invoke(0.45f)

        // 2. Detect high-frequency edge map to protect transmission details
        val edgeMap = FloatArray(size)
        for (y in 1 until height - 1) {
            val row = y * width
            for (x in 1 until width - 1) {
                val idx = row + x
                val gx = (luma[idx + 1] - luma[idx - 1]) * 0.5f
                val gy = (luma[idx + width] - luma[idx - width]) * 0.5f
                edgeMap[idx] = sqrt(gx * gx + gy * gy)
            }
        }

        onProgress?.invoke(0.65f)

        // 3. Compute transmission scaling map
        val strength = config.strength.coerceIn(0.1f, 1.0f)
        val outPixels = IntArray(size)

        for (i in 0 until size) {
            val l = luma[i]
            val veil = lowFreqLuma[i]
            val edge = edgeMap[i]

            // If region has high low-frequency veil with weak edges, it is dominant reflection
            val reflectionWeight = if (edge > 25f) {
                // Strong transmission edge: protect detail
                0.15f
            } else {
                // Smooth veil / haze: suppress reflection
                val diff = (veil - luma[i]).coerceAtLeast(0f)
                (0.5f + (diff / 50f)).coerceIn(0.3f, 1.0f)
            }

            val attenuation = veil * 0.45f * strength * reflectionWeight

            // Subtract reflection haze and expand local dynamic range
            var r = (rChan[i] - attenuation)
            var g = (gChan[i] - attenuation)
            var b = (bChan[i] - attenuation)

            if (config.recoverTransmissionColor) {
                // Restore chromaticity lost to milky white reflection wash
                val meanColor = (r + g + b) / 3f
                r = meanColor + (r - meanColor) * (1.0f + 0.25f * strength)
                g = meanColor + (g - meanColor) * (1.0f + 0.25f * strength)
                b = meanColor + (b - meanColor) * (1.0f + 0.25f * strength)
            }

            val rOut = r.roundToInt().coerceIn(0, 255)
            val gOut = g.roundToInt().coerceIn(0, 255)
            val bOut = b.roundToInt().coerceIn(0, 255)
            outPixels[i] = (0xFF shl 24) or (rOut shl 16) or (gOut shl 8) or bOut
        }

        onProgress?.invoke(0.95f)

        onProgress?.invoke(1.0f)
        return outPixels
    }

    private fun computeLowFrequencyVeil(src: FloatArray, w: Int, h: Int, radius: Int): FloatArray {
        val temp = FloatArray(w * h)
        val dst = FloatArray(w * h)

        // Horizontal pass
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                var acc = 0f
                var count = 0
                for (dx in -radius..radius) {
                    val nx = (x + dx).coerceIn(0, w - 1)
                    acc += src[row + nx]
                    count++
                }
                temp[row + x] = acc / count
            }
        }

        // Vertical pass
        for (x in 0 until w) {
            for (y in 0 until h) {
                var acc = 0f
                var count = 0
                for (dy in -radius..radius) {
                    val ny = (y + dy).coerceIn(0, h - 1)
                    acc += temp[ny * w + x]
                    count++
                }
                dst[y * w + x] = acc / count
            }
        }

        return dst
    }
}
