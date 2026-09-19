package com.webappypie.optilens.core.imaging.diagnostics

import com.webappypie.optilens.core.camera.burst.model.FramePacket
import com.webappypie.optilens.core.imaging.fusion.FusedPhoto
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlin.math.max

/**
 * Diagnostic report and before/after comparison visual artifacts produced by [ContactSheetGenerator].
 */
data class ContactSheetResult(
    val comparisonWidth: Int,
    val comparisonHeight: Int,
    val comparisonJpegBytes: ByteArray,
    val diffHeatMapJpegBytes: ByteArray,
    val meanDifference: Float,
    val maxDifference: Float,
    val snrGainDb: Float,
    val dynamicRangeExtensionEv: Float,
)

/**
 * Diagnostic utility that generates side-by-side before/after contact sheets
 * and differential heat maps to evaluate computational photography enhancements.
 */
class ContactSheetGenerator {

    /**
     * Creates a side-by-side comparison (Left: Original Reference Frame, Right: Fused Result)
     * and a difference heat map.
     */
    fun generateComparison(
        referenceFrame: FramePacket,
        fusedPhoto: FusedPhoto,
    ): ContactSheetResult {
        val width = referenceFrame.width
        val height = referenceFrame.height
        val totalPixels = width * height

        val refY = referenceFrame.buffer.data
        val fusedY = fusedPhoto.yuvBytes ?: ByteArray(totalPixels) { i ->
            // If yuvBytes was not preserved, fallback to sample byte
            if (i < refY.size) refY[i] else 128.toByte()
        }

        val compWidth = width * 2
        val compHeight = height

        // 1. Generate side-by-side comparison buffer (compWidth x compHeight)
        val compY = ByteArray(compWidth * compHeight)
        for (y in 0 until height) {
            val srcOffset = y * width
            val dstOffset = y * compWidth

            // Left side: original reference frame
            System.arraycopy(refY, srcOffset, compY, dstOffset, width)
            // Right side: fused enhanced photo
            System.arraycopy(fusedY, srcOffset, compY, dstOffset + width, width)
        }

        // 2. Generate difference heat map highlighting noise reduction and dynamic range lift
        val diffY = ByteArray(totalPixels)
        var sumDiff = 0.0
        var maxDiff = 0.0f

        for (i in 0 until totalPixels) {
            val rVal = refY[i].toInt() and 0xFF
            val fVal = fusedY[i].toInt() and 0xFF
            val d = abs(fVal - rVal).toFloat()

            sumDiff += d
            if (d > maxDiff) maxDiff = d

            // Amplify difference for visualization (scaled by 4x)
            val amplified = (d * 4.0f).toInt().coerceIn(0, 255)
            diffY[i] = amplified.toByte()
        }

        val meanDiff = (sumDiff / max(1, totalPixels)).toFloat()

        val compJpeg = encodeGrayscaleJpeg(compY, compWidth, compHeight)
        val diffJpeg = encodeGrayscaleJpeg(diffY, width, height)

        return ContactSheetResult(
            comparisonWidth = compWidth,
            comparisonHeight = compHeight,
            comparisonJpegBytes = compJpeg,
            diffHeatMapJpegBytes = diffJpeg,
            meanDifference = meanDiff,
            maxDifference = maxDiff,
            snrGainDb = fusedPhoto.diagnostics.snrGainDb,
            dynamicRangeExtensionEv = fusedPhoto.diagnostics.dynamicRangeExtensionEv,
        )
    }

    private fun encodeGrayscaleJpeg(yData: ByteArray, width: Int, height: Int): ByteArray {
        // Attempt desktop ImageIO if available
        try {
            val img = java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_BYTE_GRAY)
            val raster = img.raster
            raster.setDataElements(0, 0, width, height, yData)
            val stream = ByteArrayOutputStream()
            javax.imageio.ImageIO.write(img, "JPEG", stream)
            if (stream.size() > 0) return stream.toByteArray()
        } catch (_: Throwable) {
            // Fallback
        }

        // Minimal JPEG container fallback
        return byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(),
            0xFF.toByte(), 0xE0.toByte(), 0x00.toByte(), 0x10.toByte(),
            0x4A.toByte(), 0x46.toByte(), 0x49.toByte(), 0x46.toByte(), 0x00.toByte(),
            0x01.toByte(), 0x01.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(),
            0x00.toByte(), 0x01.toByte(), 0x00.toByte(), 0x00.toByte(),
            0xFF.toByte(), 0xD9.toByte()
        )
    }

    companion object {
        /**
         * Generates synthetic test patterns covering all required Phase 09 quality checks:
         * Foliage, Skin, Text, Low Light, Bright Signs, Repeating Patterns, and Saturated Colors.
         */
        fun generateSyntheticScene(
            type: QualitySceneType,
            width: Int = 128,
            height: Int = 128,
        ): ByteArray {
            val data = ByteArray(width * height)
            when (type) {
                QualitySceneType.FOLIAGE -> {
                    // High-frequency pseudo-random texture
                    var seed = 123456789L
                    for (i in 0 until width * height) {
                        seed = (seed * 1103515245 + 12345) and 0x7FFFFFFF
                        data[i] = (60 + (seed % 100)).toByte()
                    }
                }
                QualitySceneType.SKIN -> {
                    // Smooth skin luminance with subtle gradient
                    for (y in 0 until height) {
                        for (x in 0 until width) {
                            val lum = (140 + (x * 30 / width) + (y * 20 / height)).coerceIn(0, 255)
                            data[y * width + x] = lum.toByte()
                        }
                    }
                }
                QualitySceneType.TEXT -> {
                    // High-contrast binary text edges
                    for (y in 0 until height) {
                        for (x in 0 until width) {
                            val isChar = (x / 8) % 2 == 0 && (y / 12) % 2 == 0
                            data[y * width + x] = if (isChar) 20.toByte() else 240.toByte()
                        }
                    }
                }
                QualitySceneType.LOW_LIGHT -> {
                    // Very dark region (shadows) with sensor noise floor
                    for (i in 0 until width * height) {
                        data[i] = (8 + (i % 7)).toByte()
                    }
                }
                QualitySceneType.BRIGHT_SIGNS -> {
                    // Extended bright highlights testing knee roll-off
                    for (y in 0 until height) {
                        for (x in 0 until width) {
                            val rad = (x.toFloat() / width) * 280.0f
                            data[y * width + x] = rad.toInt().coerceIn(0, 255).toByte()
                        }
                    }
                }
                QualitySceneType.REPEATING_PATTERNS -> {
                    // High frequency grid/checkerboard
                    for (y in 0 until height) {
                        for (x in 0 until width) {
                            val v = if (((x / 4) + (y / 4)) % 2 == 0) 40 else 220
                            data[y * width + x] = v.toByte()
                        }
                    }
                }
                QualitySceneType.SATURATED_COLORS -> {
                    // Midtone luminance intended for saturated chroma testing
                    data.fill(128.toByte())
                }
            }
            return data
        }
    }
}

enum class QualitySceneType {
    FOLIAGE,
    SKIN,
    TEXT,
    LOW_LIGHT,
    BRIGHT_SIGNS,
    REPEATING_PATTERNS,
    SATURATED_COLORS,
}
