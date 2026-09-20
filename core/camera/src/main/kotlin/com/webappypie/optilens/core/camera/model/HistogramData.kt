package com.webappypie.optilens.core.camera.model

/**
 * 64-bin normalized luminance and RGB distributions for live viewfinder exposure analysis.
 */
data class HistogramData(
    val lumaBins: FloatArray = FloatArray(64),
    val redBins: FloatArray = FloatArray(64),
    val greenBins: FloatArray = FloatArray(64),
    val blueBins: FloatArray = FloatArray(64),
    val maxCount: Float = 1.0f,
) {
    /** Secondary constructor for luminance-only initializers (backward-compatibility). */
    constructor(bins: FloatArray, maxCount: Float = 1.0f) : this(
        lumaBins = bins,
        redBins = bins,
        greenBins = bins,
        blueBins = bins,
        maxCount = maxCount,
    )

    /** Backward-compatible alias for [lumaBins]. */
    val bins: FloatArray get() = lumaBins

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as HistogramData

        if (!lumaBins.contentEquals(other.lumaBins)) return false
        if (!redBins.contentEquals(other.redBins)) return false
        if (!greenBins.contentEquals(other.greenBins)) return false
        if (!blueBins.contentEquals(other.blueBins)) return false
        if (maxCount != other.maxCount) return false

        return true
    }

    override fun hashCode(): Int {
        var result = lumaBins.contentHashCode()
        result = 31 * result + redBins.contentHashCode()
        result = 31 * result + greenBins.contentHashCode()
        result = 31 * result + blueBins.contentHashCode()
        result = 31 * result + maxCount.hashCode()
        return result
    }

    companion object {
        val EMPTY = HistogramData()
    }
}
