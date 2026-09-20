package com.webappypie.optilens.core.camera.specialized

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Capture and color tuning parameters produced by [FoodModeEngine].
 */
data class FoodModeConfig(
    val targetKelvin: Int = 5200,
    val redGain: Float = 1.06f,
    val blueGain: Float = 0.94f,
    val saturationBoostFactor: Float = 1.12f,
    val maxChromaClampingKnee: Float = 85.0f,
    val localContrastLimit: Int = 16,
)

/**
 * Specialized engine for Food photography.
 *
 * Enforces:
 * 1. Stable White Balance (WB): Stabilized warm-neutral anchor (~5200K) resisting volatile indoor lighting shifts.
 * 2. Restrained Saturation: Selective mid-chroma boost for appetizing tones with soft-knee roll-off preventing neon clipping.
 * 3. Controlled Local Contrast: Subtle midtone micro-contrast enhancement with strict clipping bounds to eliminate dark haloing.
 */
@Singleton
class FoodModeEngine @Inject constructor() {

    private var smoothedKelvin = 5200f

    /**
     * Stabilizes white balance color temperature across frames using temporal smoothing.
     *
     * Anchors the color temperature in the appetizing warm-neutral zone (5000K–5400K),
     * preventing sudden cold or yellowish oscillations under indoor dining lights.
     */
    fun getStabilizedWhiteBalance(currentEstimatedKelvin: Int): FoodModeConfig {
        // Smooth towards warm-neutral target
        val target = currentEstimatedKelvin.coerceIn(4800, 5600).toFloat()
        smoothedKelvin = (0.85f * smoothedKelvin) + (0.15f * target)

        val finalKelvin = smoothedKelvin.toInt()
        val rGain = if (finalKelvin < 5000) 1.04f else 1.08f
        val bGain = if (finalKelvin > 5400) 0.92f else 0.96f

        return FoodModeConfig(
            targetKelvin = finalKelvin,
            redGain = rGain,
            blueGain = bGain,
            saturationBoostFactor = 1.12f,
            maxChromaClampingKnee = 85.0f,
            localContrastLimit = 16,
        )
    }

    /**
     * Applies restrained chrominance saturation curve on U and V planes.
     *
     * Selectively enriches muted and mid-tone chromas (fresh greens, golden crusts, warm meats)
     * while compressing saturated peaks to prevent radioactive neon clipping.
     */
    fun processRestrainedSaturation(
        uPlane: ByteArray,
        vPlane: ByteArray,
        width: Int,
        height: Int,
        config: FoodModeConfig = FoodModeConfig(),
        outU: ByteArray = ByteArray(uPlane.size),
        outV: ByteArray = ByteArray(vPlane.size),
    ): Pair<ByteArray, ByteArray> {
        val total = uPlane.size.coerceAtMost(vPlane.size)
        val boost = config.saturationBoostFactor
        val knee = config.maxChromaClampingKnee

        for (i in 0 until total) {
            val u = (uPlane[i].toInt() and 0xFF) - 128
            val v = (vPlane[i].toInt() and 0xFF) - 128

            val chromaMag = sqrt((u * u + v * v).toDouble()).toFloat()

            if (chromaMag < 2.0f) {
                outU[i] = uPlane[i]
                outV[i] = vPlane[i]
                continue
            }

            // Non-linear restrained saturation mapping
            val scaledChroma = when {
                chromaMag <= knee -> chromaMag * boost
                else -> {
                    // Soft knee compression above threshold
                    knee * boost + (chromaMag - knee) * 0.30f
                }
            }.coerceIn(0.0f, 115.0f) // Clamped strictly within valid 8-bit YUV gamut

            val scale = scaledChroma / chromaMag
            val newU = (128 + (u * scale)).toInt().coerceIn(0, 255)
            val newV = (128 + (v * scale)).toInt().coerceIn(0, 255)

            outU[i] = newU.toByte()
            outV[i] = newV.toByte()
        }

        return outU to outV
    }

    /**
     * Enhances local contrast on the luminance plane with strict clamp limits to prevent halos.
     */
    fun processControlledLocalContrast(
        inputY: ByteArray,
        width: Int,
        height: Int,
        stride: Int = width,
        outY: ByteArray = ByteArray(inputY.size),
        maxBoost: Int = 16,
    ): ByteArray {
        if (inputY.isEmpty() || width < 5 || height < 5) return inputY

        // Direct border copy
        for (x in 0 until width) {
            outY[x] = inputY[x]
            outY[(height - 1) * stride + x] = inputY[(height - 1) * stride + x]
        }
        for (y in 0 until height) {
            outY[y * stride] = inputY[y * stride]
            outY[y * stride + width - 1] = inputY[y * stride + width - 1]
        }

        // Subsampled local mean filter for micro-contrast
        for (y in 2 until height - 2) {
            val currRow = y * stride
            for (x in 2 until width - 2) {
                val center = inputY[currRow + x].toInt() and 0xFF

                // 5-point cross sample
                val top = inputY[(y - 2) * stride + x].toInt() and 0xFF
                val bottom = inputY[(y + 2) * stride + x].toInt() and 0xFF
                val left = inputY[currRow + x - 2].toInt() and 0xFF
                val right = inputY[currRow + x + 2].toInt() and 0xFF

                val localMean = (center * 2 + top + bottom + left + right) / 6
                val diff = center - localMean

                // Controlled unsharp boost strictly clamped to maxBoost
                val clampedBoost = (diff * 0.35f).toInt().coerceIn(-maxBoost, maxBoost)
                val newLuma = (center + clampedBoost).coerceIn(0, 255)

                outY[currRow + x] = newLuma.toByte()
            }
        }

        return outY
    }
}
