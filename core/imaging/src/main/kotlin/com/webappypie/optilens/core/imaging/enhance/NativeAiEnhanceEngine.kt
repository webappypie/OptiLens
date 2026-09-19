package com.webappypie.optilens.core.imaging.enhance

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Orchestrator for the One-Tap AI Enhance pipeline with native acceleration
 * and deterministic pure Kotlin JVM fallback.
 */
@Singleton
class NativeAiEnhanceEngine @Inject constructor() {
    private var useNativeIfAvailable: Boolean = true

    constructor(useNativeIfAvailable: Boolean) : this() {
        this.useNativeIfAvailable = useNativeIfAvailable
    }

    /**
     * Executes the AI Enhance processing pipeline on planar YUV data.
     *
     * @param onStageChanged Callback reporting genuine stage progress without fake percentages.
     */
    fun processEnhance(
        yPlane: ByteArray,
        uPlane: ByteArray? = null,
        vPlane: ByteArray? = null,
        width: Int,
        height: Int,
        yStride: Int = width,
        uvStride: Int = width / 2,
        config: AiEnhanceConfig = AiEnhanceConfig(),
        onStageChanged: ((AiEnhanceStage) -> Unit)? = null,
    ): Boolean {
        if (width <= 0 || height <= 0 || yPlane.isEmpty()) return false

        if (useNativeIfAvailable) {
            try {
                onStageChanged?.invoke(AiEnhanceStage.ANALYZING_SCENE)
                onStageChanged?.invoke(AiEnhanceStage.BALANCING_EXPOSURE)
                val success = NativeAiEnhanceBridge.nativeProcessAiEnhance(
                    yPlane = yPlane,
                    uPlane = uPlane,
                    vPlane = vPlane,
                    width = width,
                    height = height,
                    yStride = yStride,
                    uvStride = uvStride,
                    strength = config.strength,
                    preserveSkinTones = config.preserveSkinTones,
                    enableExposureBalancing = config.enableExposureBalancing,
                    enableNoiseReduction = config.enableNoiseReduction,
                    enableDetailEnhancement = config.enableDetailEnhancement,
                    enableColorHarmony = config.enableColorHarmony,
                )
                if (success) {
                    onStageChanged?.invoke(AiEnhanceStage.COMPLETED)
                    return true
                }
            } catch (_: Throwable) {
                // Fallback to pure Kotlin implementation below
            }
        }

        return processFallback(
            yPlane = yPlane,
            uPlane = uPlane,
            vPlane = vPlane,
            width = width,
            height = height,
            yStride = yStride,
            uvStride = uvStride,
            config = config,
            onStageChanged = onStageChanged,
        )
    }

    /**
     * Deterministic pure Kotlin implementation of AI Enhance.
     */
    fun processFallback(
        yPlane: ByteArray,
        uPlane: ByteArray?,
        vPlane: ByteArray?,
        width: Int,
        height: Int,
        yStride: Int,
        uvStride: Int,
        config: AiEnhanceConfig,
        onStageChanged: ((AiEnhanceStage) -> Unit)? = null,
    ): Boolean {
        // Stage 1: Analyze Scene
        onStageChanged?.invoke(AiEnhanceStage.ANALYZING_SCENE)
        val metrics = analyzeScene(yPlane, width, height, yStride)

        // Stage 2: Balance Exposure & Dynamic Range
        if (config.enableExposureBalancing) {
            onStageChanged?.invoke(AiEnhanceStage.BALANCING_EXPOSURE)
            balanceExposure(yPlane, width, height, yStride, metrics, config.strength)
        }

        // Stage 3: Reduce Noise
        if (config.enableNoiseReduction) {
            onStageChanged?.invoke(AiEnhanceStage.REDUCING_NOISE)
            reduceNoise(yPlane, uPlane, vPlane, width, height, yStride, uvStride, config.strength)
        }

        // Stage 4: Enhance Details & Micro-Contrast
        if (config.enableDetailEnhancement) {
            onStageChanged?.invoke(AiEnhanceStage.ENHANCING_DETAILS)
            enhanceDetails(yPlane, width, height, yStride, config.strength)
        }

        // Stage 5: Color Harmony & Vibrance
        if (config.enableColorHarmony && uPlane != null && vPlane != null) {
            onStageChanged?.invoke(AiEnhanceStage.COLOR_HARMONY)
            enhanceColorHarmony(uPlane, vPlane, width / 2, height / 2, uvStride, config.preserveSkinTones, config.strength)
        }

        onStageChanged?.invoke(AiEnhanceStage.COMPLETED)
        return true
    }

    data class SceneMetrics(
        val meanLuminance: Float,
        val shadowFraction: Float,
        val highlightFraction: Float,
    )

    fun analyzeScene(
        yPlane: ByteArray,
        width: Int,
        height: Int,
        yStride: Int,
    ): SceneMetrics {
        var sumLuma = 0L
        var shadowCount = 0
        var highlightCount = 0
        val totalPixels = width * height
        val step = max(1, sqrt(totalPixels / 10000.0).toInt())
        var sampled = 0

        var y = 0
        while (y < height) {
            val row = y * yStride
            var x = 0
            while (x < width) {
                val yVal = yPlane[row + x].toInt() and 0xFF
                sumLuma += yVal
                if (yVal < 50) shadowCount++
                if (yVal > 200) highlightCount++
                sampled++
                x += step
            }
            y += step
        }

        return if (sampled > 0) {
            SceneMetrics(
                meanLuminance = sumLuma.toFloat() / sampled,
                shadowFraction = shadowCount.toFloat() / sampled,
                highlightFraction = highlightCount.toFloat() / sampled,
            )
        } else {
            SceneMetrics(128f, 0f, 0f)
        }
    }

    private fun balanceExposure(
        yPlane: ByteArray,
        width: Int,
        height: Int,
        yStride: Int,
        metrics: SceneMetrics,
        strength: Float,
    ) {
        val baseLift = if (metrics.shadowFraction > 0.12f || metrics.meanLuminance < 115.0f) {
            min(1.8f, 0.5f + (115.0f - metrics.meanLuminance) / 75.0f)
        } else {
            0.35f
        }
        val effectiveLift = baseLift * strength

        for (y in 0 until height) {
            val row = y * yStride
            for (x in 0 until width) {
                val yVal = (yPlane[row + x].toInt() and 0xFF).toFloat()
                val normY = yVal / 255.0f
                val shadowWeight = (1.0f - normY) * sqrt(normY) * 2.0f
                var newY = yVal + effectiveLift * 35.0f * shadowWeight

                if (newY > 210.0f) {
                    val excess = newY - 210.0f
                    newY = 210.0f + excess * 0.70f
                }
                yPlane[row + x] = newY.roundToInt().coerceIn(0, 255).toByte()
            }
        }
    }

    private fun reduceNoise(
        yPlane: ByteArray,
        uPlane: ByteArray?,
        vPlane: ByteArray?,
        width: Int,
        height: Int,
        yStride: Int,
        uvStride: Int,
        strength: Float,
    ) {
        if (width < 4 || height < 4) return
        val filteredRow = ByteArray(width)

        for (y in 2 until height - 2) {
            val row = y * yStride
            val uvY = y / 2
            val uvRow = uvY * uvStride

            for (x in 2 until width - 2) {
                val centerVal = (yPlane[row + x].toInt() and 0xFF).toFloat()

                var shouldFilter = centerVal < 75.0f
                if (!shouldFilter && uPlane != null && vPlane != null) {
                    val uvX = x / 2
                    val u = (uPlane[uvRow + uvX].toInt() and 0xFF).toFloat()
                    val v = (vPlane[uvRow + uvX].toInt() and 0xFF).toFloat()
                    if (computeSkinProbability(u, v) > 0.20f) {
                        shouldFilter = true
                    }
                }

                if (!shouldFilter) {
                    filteredRow[x] = yPlane[row + x]
                    continue
                }

                var sumW = 0.0f
                var sumV = 0.0f

                for (dy in -2..2) {
                    val nRow = (y + dy) * yStride
                    for (dx in -2..2) {
                        val neighborVal = (yPlane[nRow + x + dx].toInt() and 0xFF).toFloat()
                        val sDist2 = (dx * dx + dy * dy).toFloat()
                        val diff = centerVal - neighborVal
                        val rDist2 = diff * diff
                        val w = exp(-sDist2 / (2f * 2.0f * 2.0f) - rDist2 / (2f * 25f * 25f))
                        sumW += w
                        sumV += w * neighborVal
                    }
                }

                if (sumW > 0.001f) {
                    val smoothed = sumV / sumW
                    val finalVal = ((1.0f - strength * 0.60f) * centerVal + (strength * 0.60f) * smoothed)
                    filteredRow[x] = finalVal.roundToInt().coerceIn(0, 255).toByte()
                } else {
                    filteredRow[x] = yPlane[row + x]
                }
            }

            for (x in 2 until width - 2) {
                yPlane[row + x] = filteredRow[x]
            }
        }
    }

    private fun enhanceDetails(
        yPlane: ByteArray,
        width: Int,
        height: Int,
        yStride: Int,
        strength: Float,
    ) {
        if (width < 3 || height < 3) return
        val coring = 3.0f
        val maxBoost = 20.0f
        val sharpenedRow = ByteArray(width)

        for (y in 1 until height - 1) {
            val prev = (y - 1) * yStride
            val curr = y * yStride
            val next = (y + 1) * yStride

            for (x in 1 until width - 1) {
                val center = (yPlane[curr + x].toInt() and 0xFF).toFloat()
                val blurred = (
                    ((yPlane[prev + x - 1].toInt() and 0xFF) + (yPlane[prev + x + 1].toInt() and 0xFF) +
                     (yPlane[next + x - 1].toInt() and 0xFF) + (yPlane[next + x + 1].toInt() and 0xFF)) * 1.0f +
                    ((yPlane[prev + x].toInt() and 0xFF) + (yPlane[curr + x - 1].toInt() and 0xFF) +
                     (yPlane[curr + x + 1].toInt() and 0xFF) + (yPlane[next + x].toInt() and 0xFF)) * 2.0f +
                    center * 4.0f
                ) / 16.0f

                val highPass = center - blurred
                var detailDelta = 0.0f
                if (abs(highPass) > coring) {
                    val shadowGain = if (center < 40.0f) center / 40.0f else 1.0f
                    detailDelta = highPass * strength * 0.45f * shadowGain
                    detailDelta = detailDelta.coerceIn(-maxBoost, maxBoost)
                }

                sharpenedRow[x] = (center + detailDelta).roundToInt().coerceIn(0, 255).toByte()
            }

            for (x in 1 until width - 1) {
                yPlane[curr + x] = sharpenedRow[x]
            }
        }
    }

    private fun enhanceColorHarmony(
        uPlane: ByteArray,
        vPlane: ByteArray,
        uvWidth: Int,
        uvHeight: Int,
        uvStride: Int,
        preserveSkin: Boolean,
        strength: Float,
    ) {
        for (y in 0 until uvHeight) {
            val uvRow = y * uvStride
            for (x in 0 until uvWidth) {
                val uVal = (uPlane[uvRow + x].toInt() and 0xFF).toFloat()
                val vVal = (vPlane[uvRow + x].toInt() and 0xFF).toFloat()

                val du = uVal - 128.0f
                val dv = vVal - 128.0f
                val chroma = sqrt(du * du + dv * dv)

                val pSkin = if (preserveSkin) computeSkinProbability(uVal, vVal) else 0.0f
                val vibranceFactor = max(0.0f, 1.0f - (chroma / 90.0f))
                val effectiveBoost = 1.0f + (strength * 0.22f * vibranceFactor * (1.0f - pSkin))

                uPlane[uvRow + x] = (128.0f + du * effectiveBoost).roundToInt().coerceIn(0, 255).toByte()
                vPlane[uvRow + x] = (128.0f + dv * effectiveBoost).roundToInt().coerceIn(0, 255).toByte()
            }
        }
    }

    fun computeSkinProbability(u: Float, v: Float): Float {
        val u0 = 112.0f
        val v0 = 152.0f
        val du = u - u0
        val dv = v - v0

        val cosT = 0.81915f
        val sinT = -0.57357f

        val xr = cosT * du - sinT * dv
        val yr = sinT * du + cosT * dv

        val sigmaX = 22.0f
        val sigmaY = 14.0f

        val d2 = (xr * xr) / (sigmaX * sigmaX) + (yr * yr) / (sigmaY * sigmaY)
        if (d2 > 8.0f) return 0.0f

        val p = exp(-0.5f * d2)
        return if (p > 0.05f) p else 0.0f
    }
}
