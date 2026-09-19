package com.webappypie.optilens.core.imaging.alignment

/**
 * Binary/probabilistic motion mask indicating regions with moving subjects or parallax
 * where multi-frame alignment residuals exceed the threshold.
 *
 * Used downstream during multi-frame fusion (Phase 09) for de-ghosting.
 *
 * @param width Mask width in pixels.
 * @param height Mask height in pixels.
 * @param maskBytes Binary mask bytes (0 = static scene, 255 = moving subject/ghost risk).
 * @param coverageFraction Fraction of total frame area classified as moving (0.0 to 1.0).
 */
data class GhostMask(
    val width: Int,
    val height: Int,
    val maskBytes: ByteArray,
    val coverageFraction: Float,
) {
    val hasSignificantMotion: Boolean
        get() = coverageFraction > 0.04f

    fun isPixelMoving(x: Int, y: Int): Boolean {
        if (x < 0 || x >= width || y < 0 || y >= height) return true
        return (maskBytes[y * width + x].toInt() and 0xFF) > 128
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GhostMask) return false
        return width == other.width && height == other.height && maskBytes.contentEquals(other.maskBytes)
    }

    override fun hashCode(): Int {
        var result = width
        result = 31 * result + height
        result = 31 * result + maskBytes.contentHashCode()
        return result
    }

    companion object {
        fun empty(width: Int, height: Int): GhostMask {
            return GhostMask(
                width = width,
                height = height,
                maskBytes = ByteArray(width * height),
                coverageFraction = 0.0f,
            )
        }
    }
}
