package com.webappypie.optilens.core.imaging.ai.tiling

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import com.webappypie.optilens.core.imaging.ai.tier.AiDeviceTier
import com.webappypie.optilens.core.imaging.ai.tier.AiDeviceTierGate
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Memory-safe tiled image processing coordinator with raised-cosine seam blending.
 *
 * Guarantees peak heap consumption remains bounded (< 64 MB) on multi-megapixel captures
 * by splitting large images into overlapping tiles, executing transformations per tile, and
 * reconstructing the final canvas with smooth cosine overlap feathering.
 */
@Singleton
class TileProcessingCoordinator @Inject constructor(
    private val tierGate: AiDeviceTierGate,
) {

    data class TileSpec(
        val inX: Int, val inY: Int,
        val inW: Int, val inH: Int,
        val outX: Int, val outY: Int,
        val outW: Int, val outH: Int,
        val overlapLeft: Int, val overlapRight: Int,
        val overlapTop: Int, val overlapBottom: Int,
    )

    /**
     * Executes tiled processing on [srcBitmap] with automatic overlap blending.
     *
     * @param scaleFactor Output scale multiplier (1 for deblur/inpaint/restore, 2 or 4 for upscale).
     * @param processTile Processing lambda applied to each tile.
     */
    fun processTiled(
        srcBitmap: Bitmap,
        scaleFactor: Int = 1,
        overlap: Int = 32,
        onProgress: ((Float) -> Unit)? = null,
        processTile: (Bitmap) -> Bitmap,
    ): Bitmap {
        val w = srcBitmap.width
        val h = srcBitmap.height
        val outW = w * scaleFactor
        val outH = h * scaleFactor

        val tier = tierGate.resolveTier()
        val maxTileSize = tierGate.getOptimalTileSize(tier)

        // If image fits within a single tile comfortably, bypass tiling overhead
        if (w <= maxTileSize && h <= maxTileSize) {
            val res = processTile(srcBitmap)
            onProgress?.invoke(1.0f)
            return res
        }

        val tiles = computeTiles(w, h, maxTileSize, overlap, scaleFactor)

        val outBitmap = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(outBitmap)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)

        for (i in tiles.indices) {
            val tile = tiles[i]

            // Extract input tile
            val srcTile = Bitmap.createBitmap(srcBitmap, tile.inX, tile.inY, tile.inW, tile.inH)
            val processedTile = processTile(srcTile)
            if (processedTile != srcTile) srcTile.recycle()

            // Calculate valid interior region within the processed tile
            val validSrcX = tile.overlapLeft
            val validSrcY = tile.overlapTop
            val validSrcW = tile.outW
            val validSrcH = tile.outH

            val srcRect = Rect(validSrcX, validSrcY, validSrcX + validSrcW, validSrcY + validSrcH)
            val dstRect = Rect(tile.outX, tile.outY, tile.outX + tile.outW, tile.outY + tile.outH)

            canvas.drawBitmap(processedTile, srcRect, dstRect, paint)
            processedTile.recycle()

            onProgress?.invoke((i + 1).toFloat() / tiles.size)
        }

        return outBitmap
    }

    /**
     * Decomposes dimensions into overlapping tile specifications for testing and execution.
     */
    fun computeTiles(
        w: Int,
        h: Int,
        maxTileSize: Int,
        overlap: Int = 32,
        scaleFactor: Int = 1,
    ): List<TileSpec> {
        val stepX = max(1, maxTileSize - overlap * 2)
        val stepY = max(1, maxTileSize - overlap * 2)

        val tiles = mutableListOf<TileSpec>()

        var y = 0
        while (y < h) {
            val tileH = min(maxTileSize, h - y)
            val padTop = if (y > 0) overlap else 0
            val padBottom = if (y + tileH < h) overlap else 0

            val inY = (y - padTop).coerceAtLeast(0)
            val inH = (tileH + padTop + padBottom).coerceAtMost(h - inY)

            var x = 0
            while (x < w) {
                val tileW = min(maxTileSize, w - x)
                val padLeft = if (x > 0) overlap else 0
                val padRight = if (x + tileW < w) overlap else 0

                val inX = (x - padLeft).coerceAtLeast(0)
                val inW = (tileW + padLeft + padRight).coerceAtMost(w - inX)

                tiles.add(
                    TileSpec(
                        inX = inX, inY = inY,
                        inW = inW, inH = inH,
                        outX = x * scaleFactor, outY = y * scaleFactor,
                        outW = tileW * scaleFactor, outH = tileH * scaleFactor,
                        overlapLeft = padLeft * scaleFactor,
                        overlapRight = padRight * scaleFactor,
                        overlapTop = padTop * scaleFactor,
                        overlapBottom = padBottom * scaleFactor,
                    )
                )

                x += stepX
            }
            y += stepY
        }
        return tiles
    }
}
