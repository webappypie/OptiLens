package com.webappypie.optilens.core.imaging.enhance

import android.graphics.Bitmap
import com.webappypie.optilens.core.common.coroutines.AppDispatchers
import com.webappypie.optilens.core.logging.AppLogger
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

/** Enhanced bitmap plus what the pipeline measured and applied. */
data class EnhancementResult(
    val bitmap: Bitmap,
    val report: EnhancementReport,
)

/**
 * On-device photo enhancement (no network, no model download, no ads): runs
 * [LocalEnhancementPipeline] on a Bitmap. See that class for the algorithm.
 *
 * Work is split into row strips processed on up to 6 cores; the worker count is lowered
 * when the heap is tight (each worker needs ≈ 32 bytes × width × 70 rows).
 */
@Singleton
class OnDeviceEnhancementEngine @Inject constructor(
    private val dispatchers: AppDispatchers,
    private val logger: AppLogger,
) {
    companion object {
        private const val TAG = "OnDeviceEnhancementEngine"
        private const val MAX_WORKERS = 6
    }

    suspend fun enhance(
        bitmap: Bitmap,
        config: EnhancementConfig = EnhancementConfig.BALANCED,
        onProgress: ((Float) -> Unit)? = null,
    ): Bitmap = enhanceWithReport(bitmap, config, onProgress).bitmap

    /**
     * @throws OutOfMemoryError when there is not enough heap for the output bitmap.
     * @throws kotlinx.coroutines.CancellationException when the calling job is cancelled.
     */
    suspend fun enhanceWithReport(
        bitmap: Bitmap,
        config: EnhancementConfig = EnhancementConfig.BALANCED,
        onProgress: ((Float) -> Unit)? = null,
    ): EnhancementResult = withContext(dispatchers.default) {
        val input = if (bitmap.config == Bitmap.Config.ARGB_8888) {
            bitmap
        } else {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
                ?: throw IllegalArgumentException("Unsupported bitmap format: ${bitmap.config}")
        }
        val width = input.width
        val height = input.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val source = object : ArgbRowSource {
            override val width = width
            override val height = height
            override fun readRows(y: Int, rows: Int, dst: IntArray) =
                input.getPixels(dst, 0, width, 0, y, width, rows)
        }
        val lock = Any()
        val sink = ArgbRowSink { y, rows, src ->
            synchronized(lock) { output.setPixels(src, 0, width, 0, y, width, rows) }
        }

        val workers = workerCount(width)
        try {
            val report = LocalEnhancementPipeline(config).process(source, sink, workers, onProgress)
            logger.i(
                TAG,
                "Enhanced ${width}x$height with $workers workers: analysis ${report.analysisMs} ms, " +
                    "render ${report.renderMs} ms, exposure ${"%.2f".format(report.exposureStops)} EV, " +
                    "noise ${"%.4f".format(report.shadowNoiseSigma)}, denoise r=${report.lumaDenoiseRadius}, " +
                    "clipped ${"%.1f".format(report.clippedHighlightFraction * 100)}%",
            )
            EnhancementResult(output, report)
        } catch (t: Throwable) {
            output.recycle()
            throw t
        } finally {
            if (input !== bitmap) input.recycle()
        }
    }

    private fun workerCount(width: Int): Int {
        val runtime = Runtime.getRuntime()
        val free = runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory())
        val perWorker = 32L * width * (LocalEnhancementPipeline.STRIP_ROWS + 2 * LocalEnhancementPipeline.MARGIN)
        val byMemory = (free / 3 / max(1L, perWorker)).toInt()
        val cores = runtime.availableProcessors()
        return min(MAX_WORKERS, min(cores, byMemory)).coerceAtLeast(1)
    }
}
