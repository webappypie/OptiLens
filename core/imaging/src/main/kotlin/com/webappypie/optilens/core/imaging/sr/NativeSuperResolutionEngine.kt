package com.webappypie.optilens.core.imaging.sr

import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * High-performance orchestrator for Super Resolution and AI Zoom.
 *
 * Provides:
 * - Native C++ execution on Android arm64/x86_64 devices via [NativeSuperResolutionBridge].
 * - Full mathematical JVM fallback for headless unit testing without native `.so` binaries.
 * - Tiled image processing with Hann window raised-cosine blending for bounded memory (< 25 MB).
 * - Multi-frame sub-pixel shifted accumulation and motion-artifact rejection.
 * - Single-frame edge-directed directional interpolation (EDI).
 */
@Singleton
class NativeSuperResolutionEngine @Inject constructor() {

    private val forceJvmFallback = AtomicBoolean(false)

    fun setForceJvmFallback(force: Boolean) {
        forceJvmFallback.set(force)
    }

    /**
     * Executes Multi-Frame Super Resolution on candidate frames aligned with sub-pixel precision.
     */
    fun processMultiFrameSr(
        refYPlane: ByteArray,
        refUPlane: ByteArray?,
        refVPlane: ByteArray?,
        candYPlanes: List<ByteArray>,
        candUPlanes: List<ByteArray>? = null,
        candVPlanes: List<ByteArray>? = null,
        ghostMasks: List<ByteArray>? = null,
        subPixelShifts: List<Pair<Float, Float>> = emptyList(),
        inWidth: Int,
        inHeight: Int,
        inYStride: Int = inWidth,
        inUvStride: Int = inWidth / 2,
        config: SuperResolutionConfig = SuperResolutionConfig(),
        outYPlane: ByteArray,
        outUPlane: ByteArray? = null,
        outVPlane: ByteArray? = null,
        outWidth: Int = (inWidth * config.scaleFactor).toInt(),
        outHeight: Int = (inHeight * config.scaleFactor).toInt(),
    ): Boolean {
        if (!forceJvmFallback.get() && NativeSuperResolutionBridge.isAvailable) {
            val flatShifts = FloatArray(subPixelShifts.size * 2)
            for (i in subPixelShifts.indices) {
                flatShifts[i * 2] = subPixelShifts[i].first
                flatShifts[i * 2 + 1] = subPixelShifts[i].second
            }
            return NativeSuperResolutionBridge.nativeProcessMultiFrameSr(
                refYPlane = refYPlane,
                refUPlane = refUPlane,
                refVPlane = refVPlane,
                candYPlanes = candYPlanes.toTypedArray(),
                candUPlanes = candUPlanes?.toTypedArray(),
                candVPlanes = candVPlanes?.toTypedArray(),
                ghostMasks = ghostMasks?.toTypedArray(),
                subPixelShifts = flatShifts,
                inWidth = inWidth,
                inHeight = inHeight,
                inYStride = inYStride,
                inUvStride = inUvStride,
                scaleFactor = config.scaleFactor,
                confidenceThreshold = config.confidenceThreshold,
                coringThreshold = config.coringThreshold,
                enableHaloSuppression = config.enableHaloSuppression,
                residualRejectionThreshold = config.residualRejectionThreshold,
                sharpnessBoost = config.sharpnessBoost,
                outYPlane = outYPlane,
                outUPlane = outUPlane,
                outVPlane = outVPlane,
                outWidth = outWidth,
                outHeight = outHeight,
            )
        }

        return processMultiFrameSrJvm(
            refYPlane = refYPlane,
            refUPlane = refUPlane,
            refVPlane = refVPlane,
            candYPlanes = candYPlanes,
            ghostMasks = ghostMasks,
            subPixelShifts = subPixelShifts,
            inWidth = inWidth,
            inHeight = inHeight,
            inYStride = inYStride,
            config = config,
            outYPlane = outYPlane,
            outUPlane = outUPlane,
            outVPlane = outVPlane,
            outWidth = outWidth,
            outHeight = outHeight,
        )
    }

    /**
     * Executes Single-Frame Super Resolution using Edge-Directed Interpolation (EDI).
     */
    fun processSingleFrameSr(
        inYPlane: ByteArray,
        inUPlane: ByteArray?,
        inVPlane: ByteArray?,
        inWidth: Int,
        inHeight: Int,
        inYStride: Int = inWidth,
        inUvStride: Int = inWidth / 2,
        config: SuperResolutionConfig = SuperResolutionConfig(),
        outYPlane: ByteArray,
        outUPlane: ByteArray? = null,
        outVPlane: ByteArray? = null,
        outWidth: Int = (inWidth * config.scaleFactor).toInt(),
        outHeight: Int = (inHeight * config.scaleFactor).toInt(),
    ): Boolean {
        if (!forceJvmFallback.get() && NativeSuperResolutionBridge.isAvailable) {
            return NativeSuperResolutionBridge.nativeProcessSingleFrameSr(
                inYPlane = inYPlane,
                inUPlane = inUPlane,
                inVPlane = inVPlane,
                inWidth = inWidth,
                inHeight = inHeight,
                inYStride = inYStride,
                inUvStride = inUvStride,
                scaleFactor = config.scaleFactor,
                confidenceThreshold = config.confidenceThreshold,
                coringThreshold = config.coringThreshold,
                enableHaloSuppression = config.enableHaloSuppression,
                sharpnessBoost = config.sharpnessBoost,
                outYPlane = outYPlane,
                outUPlane = outUPlane,
                outVPlane = outVPlane,
                outWidth = outWidth,
                outHeight = outHeight,
            )
        }

        return processSingleFrameSrJvm(
            inYPlane = inYPlane,
            inUPlane = inUPlane,
            inVPlane = inVPlane,
            inWidth = inWidth,
            inHeight = inHeight,
            inYStride = inYStride,
            config = config,
            outYPlane = outYPlane,
            outUPlane = outUPlane,
            outVPlane = outVPlane,
            outWidth = outWidth,
            outHeight = outHeight,
        )
    }

    /**
     * Decomposes an image into overlapping tiles for bounded memory processing.
     */
    fun decomposeTiles(
        imageWidth: Int,
        imageHeight: Int,
        tileSize: Int = 256,
        overlap: Int = 32,
        scaleFactor: Float = 2.0f,
    ): List<SrTile> {
        val tiles = mutableListOf<SrTile>()
        var y = 0
        while (y < imageHeight) {
            val h = minOf(tileSize, imageHeight - y)
            val padTop = minOf(overlap, y)
            val padBottom = minOf(overlap, imageHeight - (y + h))
            val paddedY = y - padTop
            val paddedH = h + padTop + padBottom

            var x = 0
            while (x < imageWidth) {
                val w = minOf(tileSize, imageWidth - x)
                val padLeft = minOf(overlap, x)
                val padRight = minOf(overlap, imageWidth - (x + w))
                val paddedX = x - padLeft
                val paddedW = w + padLeft + padRight

                val outX = (x * scaleFactor).toInt()
                val outY = (y * scaleFactor).toInt()
                val outW = (w * scaleFactor).toInt()
                val outH = (h * scaleFactor).toInt()

                tiles.add(
                    SrTile(
                        tileX = x,
                        tileY = y,
                        tileWidth = w,
                        tileHeight = h,
                        paddedX = paddedX,
                        paddedY = paddedY,
                        paddedWidth = paddedW,
                        paddedHeight = paddedH,
                        outX = outX,
                        outY = outY,
                        outWidth = outW,
                        outHeight = outH,
                    )
                )
                x += tileSize
            }
            y += tileSize
        }
        return tiles
    }

    // ── JVM Mathematical Fallbacks for Continuous Integration & Unit Tests ──

    private fun processMultiFrameSrJvm(
        refYPlane: ByteArray,
        refUPlane: ByteArray?,
        refVPlane: ByteArray?,
        candYPlanes: List<ByteArray>,
        ghostMasks: List<ByteArray>?,
        subPixelShifts: List<Pair<Float, Float>>,
        inWidth: Int,
        inHeight: Int,
        inYStride: Int,
        config: SuperResolutionConfig,
        outYPlane: ByteArray,
        outUPlane: ByteArray?,
        outVPlane: ByteArray?,
        outWidth: Int,
        outHeight: Int,
    ): Boolean {
        val scale = config.scaleFactor
        val totalOut = outWidth * outHeight
        val accumY = FloatArray(totalOut)
        val weightY = FloatArray(totalOut)

        // 1. Splat reference frame
        for (y in 0 until inHeight) {
            val yOffset = y * inYStride
            for (x in 0 until inWidth) {
                val v = (refYPlane[yOffset + x].toInt() and 0xFF).toFloat()
                val hrX = (x + 0.5f) * scale - 0.5f
                val hrY = (y + 0.5f) * scale - 0.5f

                val x0 = floor(hrX).toInt()
                val y0 = floor(hrY).toInt()
                val fx = hrX - x0
                val fy = hrY - y0

                for (dy in 0..1) {
                    val py = (y0 + dy).coerceIn(0, outHeight - 1)
                    val wy = if (dy == 0) (1.0f - fy) else fy
                    for (dx in 0..1) {
                        val px = (x0 + dx).coerceIn(0, outWidth - 1)
                        val wx = if (dx == 0) (1.0f - fx) else fx
                        val w = wx * wy * 1.5f

                        val idx = py * outWidth + px
                        accumY[idx] += v * w
                        weightY[idx] += w
                    }
                }
            }
        }

        // 2. Splat candidate frames with sub-pixel shifts
        for (c in candYPlanes.indices) {
            val candY = candYPlanes[c]
            val mask = ghostMasks?.getOrNull(c)
            val shift = subPixelShifts.getOrNull(c) ?: (0.0f to 0.0f)
            val shiftX = shift.first
            val shiftY = shift.second

            for (y in 0 until inHeight) {
                val yOffset = y * inYStride
                val maskOffset = y * inWidth
                for (x in 0 until inWidth) {
                    val rVal = (refYPlane[yOffset + x].toInt() and 0xFF).toFloat()
                    val cVal = (candY[yOffset + x].toInt() and 0xFF).toFloat()
                    val residual = abs(cVal - rVal)

                    // Artifact rejection
                    if (residual > config.residualRejectionThreshold) continue
                    if (mask != null && (mask[maskOffset + x].toInt() and 0xFF) > 48) continue

                    val confidence = 1.0f - (residual / (config.residualRejectionThreshold * 1.5f))
                    if (confidence < config.confidenceThreshold) continue

                    val hrX = (x + shiftX + 0.5f) * scale - 0.5f
                    val hrY = (y + shiftY + 0.5f) * scale - 0.5f

                    val x0 = floor(hrX).toInt()
                    val y0 = floor(hrY).toInt()
                    val fx = hrX - x0
                    val fy = hrY - y0

                    for (dy in 0..1) {
                        val py = (y0 + dy).coerceIn(0, outHeight - 1)
                        val wy = if (dy == 0) (1.0f - fy) else fy
                        for (dx in 0..1) {
                            val px = (x0 + dx).coerceIn(0, outWidth - 1)
                            val wx = if (dx == 0) (1.0f - fx) else fx
                            val w = wx * wy * confidence

                            val idx = py * outWidth + px
                            accumY[idx] += cVal * w
                            weightY[idx] += w
                        }
                    }
                }
            }
        }

        // 3. Normalize HR Luminance
        val normY = FloatArray(totalOut)
        for (i in 0 until totalOut) {
            val w = weightY[i]
            normY[i] = if (w > 1e-4f) accumY[i] / w else 128.0f
        }

        // 4. Deconvolution with coring and halo suppression
        for (y in 0 until outHeight) {
            val yPrev = (y - 1).coerceIn(0, outHeight - 1)
            val yNext = (y + 1).coerceIn(0, outHeight - 1)
            for (x in 0 until outWidth) {
                val xPrev = (x - 1).coerceIn(0, outWidth - 1)
                val xNext = (x + 1).coerceIn(0, outWidth - 1)

                val center = normY[y * outWidth + x]
                val smooth = (
                    normY[yPrev * outWidth + xPrev] * 0.0625f + normY[yPrev * outWidth + x] * 0.125f + normY[yPrev * outWidth + xNext] * 0.0625f +
                    normY[y * outWidth + xPrev] * 0.125f     + center * 0.25f                       + normY[y * outWidth + xNext] * 0.125f +
                    normY[yNext * outWidth + xPrev] * 0.0625f + normY[yNext * outWidth + x] * 0.125f + normY[yNext * outWidth + xNext] * 0.0625f
                )

                var highPass = center - smooth
                if (abs(highPass) < config.coringThreshold) {
                    highPass = 0.0f
                }

                var res = center + highPass * config.sharpnessBoost
                if (config.enableHaloSuppression) {
                    val lMin = minOf(normY[yPrev * outWidth + x], normY[yNext * outWidth + x], normY[y * outWidth + xPrev], normY[y * outWidth + xNext])
                    val lMax = maxOf(normY[yPrev * outWidth + x], normY[yNext * outWidth + x], normY[y * outWidth + xPrev], normY[y * outWidth + xNext])
                    res = res.coerceIn(lMin - 4.0f, lMax + 4.0f)
                }

                outYPlane[y * outWidth + x] = res.coerceIn(0.0f, 255.0f).toInt().toByte()
            }
        }

        // 5. Fill chroma channels if provided
        if (outUPlane != null && refUPlane != null) {
            val uvW = outWidth / 2
            val uvH = outHeight / 2
            for (i in 0 until (uvW * uvH)) {
                outUPlane[i] = refUPlane[i % refUPlane.size]
            }
        }
        if (outVPlane != null && refVPlane != null) {
            val uvW = outWidth / 2
            val uvH = outHeight / 2
            for (i in 0 until (uvW * uvH)) {
                outVPlane[i] = refVPlane[i % refVPlane.size]
            }
        }

        return true
    }

    private fun processSingleFrameSrJvm(
        inYPlane: ByteArray,
        inUPlane: ByteArray?,
        inVPlane: ByteArray?,
        inWidth: Int,
        inHeight: Int,
        inYStride: Int,
        config: SuperResolutionConfig,
        outYPlane: ByteArray,
        outUPlane: ByteArray?,
        outVPlane: ByteArray?,
        outWidth: Int,
        outHeight: Int,
    ): Boolean {
        val scaleX = inWidth.toFloat() / outWidth.toFloat()
        val scaleY = inHeight.toFloat() / outHeight.toFloat()

        // 1. Compute gradients
        val edgeMag = FloatArray(inWidth * inHeight)
        val edgeAngle = FloatArray(inWidth * inHeight)

        for (y in 0 until inHeight) {
            val yPrev = (y - 1).coerceIn(0, inHeight - 1)
            val yNext = (y + 1).coerceIn(0, inHeight - 1)
            for (x in 0 until inWidth) {
                val xPrev = (x - 1).coerceIn(0, inWidth - 1)
                val xNext = (x + 1).coerceIn(0, inWidth - 1)

                val gx = ((inYPlane[y * inYStride + xNext].toInt() and 0xFF) - (inYPlane[y * inYStride + xPrev].toInt() and 0xFF)) * 0.5f
                val gy = ((inYPlane[yNext * inYStride + x].toInt() and 0xFF) - (inYPlane[yPrev * inYStride + x].toInt() and 0xFF)) * 0.5f

                edgeMag[y * inWidth + x] = sqrt(gx * gx + gy * gy)
                edgeAngle[y * inWidth + x] = atan2(gy, gx)
            }
        }

        // 2. Edge-directed interpolation
        for (y in 0 until outHeight) {
            val srcY = (y + 0.5f) * scaleY - 0.5f
            val sy0 = floor(srcY).toInt()
            val fy = srcY - sy0

            for (x in 0 until outWidth) {
                val srcX = (x + 0.5f) * scaleX - 0.5f
                val sx0 = floor(srcX).toInt()
                val fx = srcX - sx0

                val nx = (srcX + 0.5f).toInt().coerceIn(0, inWidth - 1)
                val ny = (srcY + 0.5f).toInt().coerceIn(0, inHeight - 1)

                val mag = edgeMag[ny * inWidth + nx]
                val theta = edgeAngle[ny * inWidth + nx]

                var yVal = 0.0f
                var wSum = 0.0f

                if (mag > 16.0f) {
                    val cosDir = -sin(theta)
                    val sinDir = cos(theta)

                    for (dy in -1..2) {
                        val py = (sy0 + dy).coerceIn(0, inHeight - 1)
                        val distY = dy - fy
                        for (dx in -1..2) {
                            val px = (sx0 + dx).coerceIn(0, inWidth - 1)
                            val distX = dx - fx

                            val par = distX * cosDir + distY * sinDir
                            val perp = -distX * sinDir + distY * cosDir

                            val w = lanczosKernel(par * 0.8f) * lanczosKernel(perp * 1.4f)
                            yVal += (inYPlane[py * inYStride + px].toInt() and 0xFF).toFloat() * w
                            wSum += w
                        }
                    }
                } else {
                    for (dy in -1..2) {
                        val py = (sy0 + dy).coerceIn(0, inHeight - 1)
                        val wy = bicubicWeight(dy - fy)
                        for (dx in -1..2) {
                            val px = (sx0 + dx).coerceIn(0, inWidth - 1)
                            val wx = bicubicWeight(dx - fx)
                            val w = wx * wy

                            yVal += (inYPlane[py * inYStride + px].toInt() and 0xFF).toFloat() * w
                            wSum += w
                        }
                    }
                }

                val finalVal = if (abs(wSum) > 1e-4f) yVal / wSum else 128.0f
                outYPlane[y * outWidth + x] = finalVal.coerceIn(0.0f, 255.0f).toInt().toByte()
            }
        }

        // 3. Unsharp micro-contrast on output Y
        val tempY = outYPlane.clone()
        for (y in 0 until outHeight) {
            val yPrev = (y - 1).coerceIn(0, outHeight - 1)
            val yNext = (y + 1).coerceIn(0, outHeight - 1)
            for (x in 0 until outWidth) {
                val xPrev = (x - 1).coerceIn(0, outWidth - 1)
                val xNext = (x + 1).coerceIn(0, outWidth - 1)

                val center = (tempY[y * outWidth + x].toInt() and 0xFF).toFloat()
                val cross = (
                    (tempY[yPrev * outWidth + x].toInt() and 0xFF) +
                    (tempY[yNext * outWidth + x].toInt() and 0xFF) +
                    (tempY[y * outWidth + xPrev].toInt() and 0xFF) +
                    (tempY[y * outWidth + xNext].toInt() and 0xFF)
                ).toFloat() * 0.25f

                var diff = center - cross
                if (abs(diff) < config.coringThreshold) diff = 0.0f

                var res = center + diff * (config.sharpnessBoost * 0.8f)
                if (config.enableHaloSuppression) {
                    val lMin = minOf(
                        (tempY[yPrev * outWidth + x].toInt() and 0xFF).toFloat(),
                        (tempY[yNext * outWidth + x].toInt() and 0xFF).toFloat(),
                        (tempY[y * outWidth + xPrev].toInt() and 0xFF).toFloat(),
                        (tempY[y * outWidth + xNext].toInt() and 0xFF).toFloat(),
                    )
                    val lMax = maxOf(
                        (tempY[yPrev * outWidth + x].toInt() and 0xFF).toFloat(),
                        (tempY[yNext * outWidth + x].toInt() and 0xFF).toFloat(),
                        (tempY[y * outWidth + xPrev].toInt() and 0xFF).toFloat(),
                        (tempY[y * outWidth + xNext].toInt() and 0xFF).toFloat(),
                    )
                    res = res.coerceIn(lMin - 3.0f, lMax + 3.0f)
                }

                outYPlane[y * outWidth + x] = res.coerceIn(0.0f, 255.0f).toInt().toByte()
            }
        }

        // Chroma copy
        if (outUPlane != null && inUPlane != null) {
            val uvW = outWidth / 2
            val uvH = outHeight / 2
            for (i in 0 until (uvW * uvH)) {
                outUPlane[i] = inUPlane[i % inUPlane.size]
            }
        }
        if (outVPlane != null && inVPlane != null) {
            val uvW = outWidth / 2
            val uvH = outHeight / 2
            for (i in 0 until (uvW * uvH)) {
                outVPlane[i] = inVPlane[i % inVPlane.size]
            }
        }

        return true
    }

    private fun lanczosKernel(x: Float): Float {
        val ax = abs(x)
        if (ax >= 3.0f) return 0.0f
        if (ax < 1e-4f) return 1.0f
        val pix = Math.PI.toFloat() * ax
        return (sin(pix) / pix) * (sin(pix / 3.0f) / (pix / 3.0f))
    }

    private fun bicubicWeight(x: Float): Float {
        val ax = abs(x)
        val a = -0.5f
        return when {
            ax <= 1.0f -> (a + 2.0f) * ax * ax * ax - (a + 3.0f) * ax * ax + 1.0f
            ax < 2.0f -> a * ax * ax * ax - 5.0f * a * ax * ax + 8.0f * a * ax - 4.0f * a
            else -> 0.0f
        }
    }
}
