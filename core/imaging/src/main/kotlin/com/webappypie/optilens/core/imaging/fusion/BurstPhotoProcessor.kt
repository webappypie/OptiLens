package com.webappypie.optilens.core.imaging.fusion

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.ImageFormat
import android.graphics.Rect
import com.webappypie.optilens.core.camera.burst.model.FramePacket
import com.webappypie.optilens.core.common.coroutines.AppDispatchers
import com.webappypie.optilens.core.imaging.enhance.ArgbImage
import com.webappypie.optilens.core.imaging.enhance.EnhancementConfig
import com.webappypie.optilens.core.imaging.enhance.EnhancementReport
import com.webappypie.optilens.core.imaging.enhance.LocalEnhancementPipeline
import com.webappypie.optilens.core.logging.AppLogger
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/** A merged burst encoded as JPEG, in the frames' sensor orientation (rotation is applied via EXIF). */
class ProcessedBurstPhoto(
    val jpeg: ByteArray,
    val width: Int,
    val height: Int,
    val orientationDegrees: Int,
    val merge: BurstMergeReport,
    val tone: EnhancementReport?,
)

/** A burst JPEG frame: ¼/½-scale decodes for alignment, region decodes for full-resolution bands. */
internal class JpegBurstFrame(
    private val bytes: ByteArray,
    private val length: Int,
    override val exposureProduct: Double,
) : BurstFrameSource, AutoCloseable {

    override val width: Int
    override val height: Int
    private val regionDecoder: BitmapRegionDecoder

    init {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, length, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Undecodable burst frame" }
        width = bounds.outWidth
        height = bounds.outHeight
        @Suppress("DEPRECATION")
        regionDecoder = BitmapRegionDecoder.newInstance(bytes, 0, length, false)
            ?: throw IllegalArgumentException("Burst frame is not region-decodable")
    }

    override fun readDownsampled(factor: Int): ArgbImage {
        val opts = BitmapFactory.Options().apply {
            inSampleSize = factor
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, length, opts)
            ?: throw IllegalStateException("Burst frame decode failed")
        try {
            val px = IntArray(bmp.width * bmp.height)
            bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
            return ArgbImage(bmp.width, bmp.height, px)
        } finally {
            bmp.recycle()
        }
    }

    override fun readRows(y: Int, rows: Int, dst: IntArray) {
        val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val bmp = regionDecoder.decodeRegion(Rect(0, y, width, y + rows), opts)
            ?: throw IllegalStateException("Burst region decode failed")
        try {
            bmp.getPixels(dst, 0, width, 0, 0, width, min(rows, bmp.height))
        } finally {
            bmp.recycle()
        }
    }

    override fun close() = regionDecoder.recycle()
}

/**
 * Turns a captured JPEG burst into one photo: [BurstMerger] (align + robust merge) followed,
 * optionally, by [LocalEnhancementPipeline] tone mapping. The merged frames' noise is lower, so
 * the tone mapper's noise-aware limits allow more shadow recovery than on a single frame.
 *
 * Packets are only read; the caller still owns and closes them.
 */
@Singleton
class BurstPhotoProcessor @Inject constructor(
    private val dispatchers: AppDispatchers,
    private val logger: AppLogger,
) {
    suspend fun process(
        packets: List<FramePacket>,
        toneMap: Boolean,
        config: EnhancementConfig = EnhancementConfig.BALANCED,
    ): ProcessedBurstPhoto = withContext(dispatchers.default) {
        val usable = packets.filter { !it.isClosed && it.format == ImageFormat.JPEG && it.buffer.length > 0 }
        require(usable.isNotEmpty()) { "No usable burst frames" }
        val sources = usable.map {
            JpegBurstFrame(
                bytes = it.buffer.data,
                length = it.buffer.length,
                exposureProduct = it.metadata.exposureTimeNs.toDouble() * it.metadata.iso,
            )
        }
        try {
            val w = sources[0].width
            val h = sources[0].height
            val workers = min(3, Runtime.getRuntime().availableProcessors()).coerceAtLeast(1)

            var image = ArgbImage(w, h)
            val merge = BurstMerger().merge(sources, image, workers)

            var tone: EnhancementReport? = null
            if (toneMap) {
                val toned = ArgbImage(w, h)
                tone = LocalEnhancementPipeline(config).process(image, toned, workers)
                image = toned
            }

            val bitmap = Bitmap.createBitmap(image.pixels, w, h, Bitmap.Config.ARGB_8888)
            val jpeg = try {
                ByteArrayOutputStream(w * h / 3).use { out ->
                    check(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)) { "JPEG encode failed" }
                    out.toByteArray()
                }
            } finally {
                bitmap.recycle()
            }
            logger.i(
                TAG,
                "Burst ${w}x$h: ref ${merge.referenceIndex}, merged ${merge.framesMerged}/${merge.frameCount}, " +
                    "rejected ${merge.rejectedFrames}, acceptance ${"%.2f".format(merge.meanAcceptance)}, " +
                    "align ${merge.alignMs} ms, merge ${merge.mergeMs} ms" +
                    (tone?.let { ", tone ${it.analysisMs + it.renderMs} ms" } ?: ""),
            )
            ProcessedBurstPhoto(
                jpeg = jpeg,
                width = w,
                height = h,
                orientationDegrees = usable[merge.referenceIndex].metadata.orientationDegrees,
                merge = merge,
                tone = tone,
            )
        } finally {
            sources.forEach { runCatching { it.close() } }
        }
    }

    private companion object {
        const val TAG = "BurstPhotoProcessor"
    }
}
