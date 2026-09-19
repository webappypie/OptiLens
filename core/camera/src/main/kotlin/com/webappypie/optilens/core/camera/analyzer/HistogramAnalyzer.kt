package com.webappypie.optilens.core.camera.analyzer

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.webappypie.optilens.core.camera.model.HistogramData
import java.nio.ByteBuffer

/**
 * High-performance, low-overhead CameraX [ImageAnalysis.Analyzer] that extracts
 * a 64-bin normalized luminance distribution from incoming viewfinder frames.
 *
 * Guarantees deterministic [ImageProxy.close] cleanup on every invocation.
 */
class HistogramAnalyzer(
    private val onHistogramComputed: (HistogramData) -> Unit,
) : ImageAnalysis.Analyzer {

    @Volatile
    var isEnabled: Boolean = true

    private var lastAnalyzedTimestampMs = 0L
    private val throttleIntervalMs = 66L // ~15 fps max analysis rate to conserve CPU/battery

    override fun analyze(image: ImageProxy) {
        try {
            val now = System.currentTimeMillis()
            if (!isEnabled || (now - lastAnalyzedTimestampMs) < throttleIntervalMs) {
                return
            }
            lastAnalyzedTimestampMs = now

            val planes = image.planes
            if (planes.isEmpty()) return

            val yPlane = planes[0]
            val buffer = yPlane.buffer
            val pixelStride = yPlane.pixelStride
            val rowStride = yPlane.rowStride
            val width = image.width
            val height = image.height

            val rawBins = IntArray(64)
            var maxBin = 1

            // Sub-sample grid with step of 4 to achieve < 1ms execution on mobile SoC
            val step = 4
            val rowBuffer = ByteArray(rowStride)

            for (row in 0 until height step step) {
                val rowOffset = row * rowStride
                buffer.position(rowOffset)
                val bytesToRead = minOf(rowStride, buffer.remaining())
                buffer.get(rowBuffer, 0, bytesToRead)

                for (col in 0 until width step step) {
                    val pixelIndex = col * pixelStride
                    if (pixelIndex < bytesToRead) {
                        val luminance = rowBuffer[pixelIndex].toInt() and 0xFF
                        val binIndex = (luminance shr 2).coerceIn(0, 63)
                        rawBins[binIndex]++
                        if (rawBins[binIndex] > maxBin) {
                            maxBin = rawBins[binIndex]
                        }
                    }
                }
            }

            val normalizedBins = FloatArray(64) { i ->
                rawBins[i].toFloat() / maxBin.toFloat()
            }

            onHistogramComputed(HistogramData(bins = normalizedBins, maxCount = maxBin.toFloat()))
        } catch (_: Exception) {
            // Drop frame gracefully on format/lifecycle transition
        } finally {
            image.close()
        }
    }
}
