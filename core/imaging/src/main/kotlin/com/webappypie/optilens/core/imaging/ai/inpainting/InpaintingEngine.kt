package com.webappypie.optilens.core.imaging.ai.inpainting

import android.graphics.Bitmap
import android.graphics.Color
import com.webappypie.optilens.core.imaging.ai.InpaintingConfig
import java.util.PriorityQueue
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Exemplar texture-synthesizing background cleanup and inpainting engine.
 *
 * Implements the Fast Marching Method (Telea algorithm) for smooth, structure-preserving
 * reconstruction of user-selected distraction areas with boundary bilateral feathering.
 */
@Singleton
class InpaintingEngine @Inject constructor() {

    companion object {
        private const val KNOWN = 0
        private const val BAND = 1
        private const val INSIDE = 2
        private const val INPAINT_RADIUS = 5
    }

    private data class BandPixel(val x: Int, val y: Int, val dist: Float) : Comparable<BandPixel> {
        override fun compareTo(other: BandPixel): Int = this.dist.compareTo(other.dist)
    }

    /**
     * Inpaints masked areas in [sourceBitmap] using surrounding structural texture.
     *
     * @param sourceBitmap Target image containing distractions.
     * @param maskBitmap Grayscale or alpha mask where non-zero pixels define the region to remove.
     * @param config Tuning parameters for dilation and bilateral boundary feathering.
     */
    fun inpaint(
        sourceBitmap: Bitmap,
        maskBitmap: Bitmap,
        config: InpaintingConfig = InpaintingConfig(),
        onProgress: ((Float) -> Unit)? = null,
    ): Bitmap {
        val width = sourceBitmap.width
        val height = sourceBitmap.height
        if (width <= 0 || height <= 0) return sourceBitmap

        val size = width * height
        val srcPixels = IntArray(size)
        sourceBitmap.getPixels(srcPixels, 0, width, 0, 0, width, height)

        val rawMask = BooleanArray(size)
        val maskPixels = IntArray(size)
        // Ensure mask dimensions match
        if (maskBitmap.width == width && maskBitmap.height == height) {
            maskBitmap.getPixels(maskPixels, 0, width, 0, 0, width, height)
            for (i in 0 until size) {
                val alpha = (maskPixels[i] ushr 24) and 0xFF
                val red = (maskPixels[i] shr 16) and 0xFF
                rawMask[i] = (alpha > 32 && red > 32)
            }
        } else {
            val scaledMask = Bitmap.createScaledBitmap(maskBitmap, width, height, true)
            scaledMask.getPixels(maskPixels, 0, width, 0, 0, width, height)
            for (i in 0 until size) {
                val alpha = (maskPixels[i] ushr 24) and 0xFF
                val red = (maskPixels[i] shr 16) and 0xFF
                rawMask[i] = (alpha > 32 && red > 32)
            }
            if (scaledMask != maskBitmap) scaledMask.recycle()
        }

        val outPixels = inpaintPixels(srcPixels, rawMask, width, height, config, onProgress)
        val outBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        outBitmap.setPixels(outPixels, 0, width, 0, 0, width, height)
        return outBitmap
    }

    /**
     * Core mathematical inpainting on raw ARGB pixel buffer and boolean mask.
     */
    fun inpaintPixels(
        srcPixels: IntArray,
        rawMask: BooleanArray,
        width: Int,
        height: Int,
        config: InpaintingConfig = InpaintingConfig(),
        onProgress: ((Float) -> Unit)? = null,
    ): IntArray {
        val size = width * height
        if (width <= 0 || height <= 0 || srcPixels.size < size || rawMask.size < size) {
            return srcPixels
        }

        // Dilate mask slightly if requested to cover antialiased edges
        val mask = if (config.dilationPixels > 0) {
            dilateMask(rawMask, width, height, config.dilationPixels)
        } else {
            rawMask
        }

        var inpaintCount = 0
        for (b in mask) if (b) inpaintCount++
        if (inpaintCount == 0) return srcPixels // Nothing to inpaint

        val flags = IntArray(size)
        val dist = FloatArray(size) { Float.POSITIVE_INFINITY }
        val rChan = FloatArray(size)
        val gChan = FloatArray(size)
        val bChan = FloatArray(size)

        for (i in 0 until size) {
            val c = srcPixels[i]
            rChan[i] = ((c shr 16) and 0xFF).toFloat()
            gChan[i] = ((c shr 8) and 0xFF).toFloat()
            bChan[i] = (c and 0xFF).toFloat()
            flags[i] = if (mask[i]) INSIDE else KNOWN
            if (!mask[i]) dist[i] = 0f
        }

        val queue = PriorityQueue<BandPixel>()

        // Initialize narrow band around the mask boundary
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                val idx = row + x
                if (flags[idx] == INSIDE) {
                    val hasKnownNeighbor = (x > 0 && flags[idx - 1] == KNOWN) ||
                                           (x < width - 1 && flags[idx + 1] == KNOWN) ||
                                           (y > 0 && flags[idx - width] == KNOWN) ||
                                           (y < height - 1 && flags[idx + width] == KNOWN)
                    if (hasKnownNeighbor) {
                        flags[idx] = BAND
                        dist[idx] = 1.0f
                        queue.add(BandPixel(x, y, 1.0f))
                    }
                }
            }
        }

        var processed = 0
        val totalToProcess = max(1, inpaintCount)

        // Fast Marching loop
        while (queue.isNotEmpty()) {
            val current = queue.poll() ?: break
            val cx = current.x
            val cy = current.y
            val cIdx = cy * width + cx

            flags[cIdx] = KNOWN
            processed++

            if (processed % 1000 == 0) {
                onProgress?.invoke(min(0.90f, processed.toFloat() / totalToProcess * 0.90f))
            }

            // Inpaint current pixel from known neighbors
            inpaintPixel(cx, cy, width, height, rChan, gChan, bChan, flags, dist)

            // Propagate to 4-connected neighbors
            val neighbors = arrayOf(
                Pair(cx - 1, cy), Pair(cx + 1, cy),
                Pair(cx, cy - 1), Pair(cx, cy + 1)
            )

            for ((nx, ny) in neighbors) {
                if (nx in 0 until width && ny in 0 until height) {
                    val nIdx = ny * width + nx
                    if (flags[nIdx] == INSIDE) {
                        flags[nIdx] = BAND
                        val nDist = dist[cIdx] + 1.0f
                        dist[nIdx] = nDist
                        queue.add(BandPixel(nx, ny, nDist))
                    }
                }
            }
        }

        // Optional bilateral feathering along mask seam
        if (config.bilateralSmoothing && config.featherRadius > 0) {
            smoothBoundarySeam(srcPixels, rChan, gChan, bChan, mask, width, height, config.featherRadius)
        }

        val outPixels = IntArray(size)
        for (i in 0 until size) {
            val r = rChan[i].roundToInt().coerceIn(0, 255)
            val g = gChan[i].roundToInt().coerceIn(0, 255)
            val b = bChan[i].roundToInt().coerceIn(0, 255)
            outPixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        onProgress?.invoke(1.0f)
        return outPixels
    }

    private fun inpaintPixel(
        cx: Int, cy: Int, w: Int, h: Int,
        rChan: FloatArray, gChan: FloatArray, bChan: FloatArray,
        flags: IntArray, dist: FloatArray
    ) {
        val cIdx = cy * w + cx
        val cDist = dist[cIdx]

        // Approximate gradient of distance function at (cx, cy)
        val gradX = computeGrad(cx, cy, w, h, dist, flags, isX = true)
        val gradY = computeGrad(cx, cy, w, h, dist, flags, isX = false)

        var sumW = 0f
        var sumR = 0f
        var sumG = 0f
        var sumB = 0f

        val r = INPAINT_RADIUS
        for (dy in -r..r) {
            val ny = cy + dy
            if (ny !in 0 until h) continue
            val row = ny * w
            for (dx in -r..r) {
                val nx = cx + dx
                if (nx !in 0 until w) continue
                val nIdx = row + nx

                if (flags[nIdx] == KNOWN) {
                    val d2 = (dx * dx + dy * dy).toFloat()
                    if (d2 > r * r || d2 < 0.5f) continue

                    // Directional weight: alignment with boundary normal
                    val dirWeight = abs(dx * gradX + dy * gradY) + 0.05f
                    // Distance weight: closer pixels have exponentially higher influence
                    val dstWeight = 1.0f / (d2 * sqrt(d2))
                    // Level set weight: pixels of similar distance rank
                    val levWeight = 1.0f / (1.0f + abs(dist[nIdx] - cDist))

                    val wTotal = dirWeight * dstWeight * levWeight
                    sumW += wTotal
                    sumR += wTotal * rChan[nIdx]
                    sumG += wTotal * gChan[nIdx]
                    sumB += wTotal * bChan[nIdx]
                }
            }
        }

        if (sumW > 1e-6f) {
            rChan[cIdx] = sumR / sumW
            gChan[cIdx] = sumG / sumW
            bChan[cIdx] = sumB / sumW
        }
    }

    private fun computeGrad(x: Int, y: Int, w: Int, h: Int, dist: FloatArray, flags: IntArray, isX: Boolean): Float {
        if (isX) {
            val left = if (x > 0 && flags[y * w + x - 1] == KNOWN) dist[y * w + x - 1] else dist[y * w + x]
            val right = if (x < w - 1 && flags[y * w + x + 1] == KNOWN) dist[y * w + x + 1] else dist[y * w + x]
            return (right - left) * 0.5f
        } else {
            val top = if (y > 0 && flags[(y - 1) * w + x] == KNOWN) dist[(y - 1) * w + x] else dist[y * w + x]
            val bottom = if (y < h - 1 && flags[(y + 1) * w + x] == KNOWN) dist[(y + 1) * w + x] else dist[y * w + x]
            return (bottom - top) * 0.5f
        }
    }

    private fun dilateMask(src: BooleanArray, w: Int, h: Int, radius: Int): BooleanArray {
        val dst = src.clone()
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                if (src[row + x]) {
                    for (dy in -radius..radius) {
                        val ny = y + dy
                        if (ny !in 0 until h) continue
                        val nRow = ny * w
                        for (dx in -radius..radius) {
                            val nx = x + dx
                            if (nx in 0 until w && dx * dx + dy * dy <= radius * radius) {
                                dst[nRow + nx] = true
                            }
                        }
                    }
                }
            }
        }
        return dst
    }

    private fun smoothBoundarySeam(
        srcPixels: IntArray,
        rChan: FloatArray, gChan: FloatArray, bChan: FloatArray,
        mask: BooleanArray, w: Int, h: Int, radius: Int
    ) {
        val seamMask = BooleanArray(w * h)
        for (y in 1 until h - 1) {
            val row = y * w
            for (x in 1 until w - 1) {
                val idx = row + x
                if (mask[idx]) {
                    if (!mask[idx - 1] || !mask[idx + 1] || !mask[idx - w] || !mask[idx + w]) {
                        for (dy in -radius..radius) {
                            val ny = y + dy
                            if (ny in 0 until h) {
                                for (dx in -radius..radius) {
                                    val nx = x + dx
                                    if (nx in 0 until w) seamMask[ny * w + nx] = true
                                }
                            }
                        }
                    }
                }
            }
        }

        val rCopy = rChan.clone()
        val gCopy = gChan.clone()
        val bCopy = bChan.clone()

        for (y in radius until h - radius) {
            val row = y * w
            for (x in radius until w - radius) {
                val idx = row + x
                if (seamMask[idx]) {
                    var sumR = 0f
                    var sumG = 0f
                    var sumB = 0f
                    var count = 0
                    for (dy in -radius..radius) {
                        val nRow = (y + dy) * w
                        for (dx in -radius..radius) {
                            val nIdx = nRow + (x + dx)
                            sumR += rCopy[nIdx]
                            sumG += gCopy[nIdx]
                            sumB += bCopy[nIdx]
                            count++
                        }
                    }
                    if (count > 0) {
                        rChan[idx] = rChan[idx] * 0.4f + (sumR / count) * 0.6f
                        gChan[idx] = gChan[idx] * 0.4f + (sumG / count) * 0.6f
                        bChan[idx] = bChan[idx] * 0.4f + (sumB / count) * 0.6f
                    }
                }
            }
        }
    }
}
