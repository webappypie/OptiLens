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

/** One reusable decode-target bitmap per thread, shared by all frames of a burst. */
internal class BandBitmaps : AutoCloseable {
    private val all = mutableListOf<Bitmap>()
    private val local = ThreadLocal<Bitmap?>()

    fun target(width: Int, rows: Int): Bitmap {
        val current = local.get()
        if (current != null && !current.isRecycled && current.width == width && current.height >= rows) return current
        current?.let { old -> synchronized(all) { all.remove(old) }; old.recycle() }
        // A little headroom so small variations in band height don't reallocate.
        val bmp = Bitmap.createBitmap(width, rows + 32, Bitmap.Config.ARGB_8888)
        synchronized(all) { all += bmp }
        local.set(bmp)
        return bmp
    }

    override fun close() = synchronized(all) {
        all.forEach { it.recycle() }
        all.clear()
    }
}

/**
 * A burst JPEG frame: ¼/½-scale decodes for alignment, region decodes for full-resolution bands.
 * Each worker thread gets its own region decoder (a decoder serialises its calls); decoders
 * share the JPEG bytes instead of copying them.
 */
internal class JpegBurstFrame(
    private val bytes: ByteArray,
    private val length: Int,
    override val exposureProduct: Double,
    private val bandBitmaps: BandBitmaps,
) : BurstFrameSource, AutoCloseable {

    override val width: Int
    override val height: Int
    private val decoders = mutableListOf<BitmapRegionDecoder>()
    private val threadDecoder = object : ThreadLocal<BitmapRegionDecoder>() {
        override fun initialValue(): BitmapRegionDecoder {
            @Suppress("DEPRECATION")
            val d = BitmapRegionDecoder.newInstance(bytes, 0, length, true)
                ?: throw IllegalArgumentException("Burst frame is not region-decodable")
            synchronized(decoders) { decoders += d }
            return d
        }
    }

    init {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, length, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Undecodable burst frame" }
        width = bounds.outWidth
        height = bounds.outHeight
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
        val target = bandBitmaps.target(width, rows)
        val opts = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inMutable = true
            inBitmap = target
        }
        val bmp = threadDecoder.get()!!.decodeRegion(Rect(0, y, width, y + rows), opts)
            ?: throw IllegalStateException("Burst region decode failed")
        bmp.getPixels(dst, 0, width, 0, 0, width, min(rows, bmp.height))
        if (bmp !== target) bmp.recycle() // decoder could not reuse the target: drop the copy
    }

    override fun close() {
        synchronized(decoders) {
            decoders.forEach { it.recycle() }
            decoders.clear()
        }
    }
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
        val bandBitmaps = BandBitmaps()
        val sources = usable.map {
            JpegBurstFrame(
                bytes = it.buffer.data,
                length = it.buffer.length,
                exposureProduct = it.metadata.exposureTimeNs.toDouble() * it.metadata.iso,
                bandBitmaps = bandBitmaps,
            )
        }
        try {
            val w = sources[0].width
            val h = sources[0].height
            val cores = Runtime.getRuntime().availableProcessors()
            val mergeWorkers = mergeWorkerCount(w, h, cores)
            val toneWorkers = min(MAX_WORKERS, cores).coerceAtLeast(1)

            val tStart = System.nanoTime()
            val merged = ArgbImage(w, h)
            val merge = BurstMerger().merge(sources, merged, mergeWorkers)
            bandBitmaps.close()

            // Pixels of a Bitmap live in native memory (API 26+): writing the tone-mapped result
            // straight into it avoids a second full-size Java array and a copy.
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            var tone: EnhancementReport? = null
            val tEncode: Long
            val jpeg = try {
                if (toneMap) {
                    val lock = Any()
                    tone = LocalEnhancementPipeline(config).process(
                        source = merged,
                        sink = { y, rows, src -> synchronized(lock) { bitmap.setPixels(src, 0, w, 0, y, w, rows) } },
                        parallelism = toneWorkers,
                    )
                } else {
                    bitmap.setPixels(merged.pixels, 0, w, 0, 0, w, h)
                }
                tEncode = System.nanoTime()
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
                    (tone?.let { ", tone ${it.analysisMs + it.renderMs} ms" } ?: "") +
                    ", encode ${(System.nanoTime() - tEncode) / 1_000_000} ms" +
                    ", total ${(System.nanoTime() - tStart) / 1_000_000} ms, workers $mergeWorkers/$toneWorkers",
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
            bandBitmaps.close()
        }
    }

    /**
     * Merge workers: up to 6 cores, fewer when the Java heap is tight. Each worker holds a
     * [BurstMerger] band workspace (≈ 44 B × width × band rows plus the displaced source rows);
     * the merged full-size ARGB image (4 B × pixels) is reserved first.
     */
    private fun mergeWorkerCount(w: Int, h: Int, cores: Int): Int {
        val rt = Runtime.getRuntime()
        val free = rt.maxMemory() - (rt.totalMemory() - rt.freeMemory()) - 4L * w * h
        val perWorker = w.toLong() * (44L * BurstMerger.BAND + 4L * (BurstMerger.BAND + 2 * BurstMerger.MAX_SHIFT_ROWS + 4))
        val byMemory = (free * 2 / 3 / perWorker.coerceAtLeast(1)).toInt()
        return min(MAX_WORKERS, min(cores, byMemory)).coerceAtLeast(1)
    }

    private companion object {
        const val TAG = "BurstPhotoProcessor"
        const val MAX_WORKERS = 6
    }
}
