package com.webappypie.optilens.core.camera.model

/**
 * 64-bin normalized luminance distribution for live viewfinder exposure analysis.
 */
data class HistogramData(
    val bins: FloatArray = FloatArray(64),
    val maxCount: Float = 1.0f,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as HistogramData

        if (!bins.contentEquals(other.bins)) return false
        if (maxCount != other.maxCount) return false

        return true
    }

    override fun hashCode(): Int {
        var result = bins.contentHashCode()
        result = 31 * result + maxCount.hashCode()
        return result
    }

    companion object {
        val EMPTY = HistogramData()
    }
}
