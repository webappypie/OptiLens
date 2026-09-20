package com.webappypie.optilens.core.common.performance

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * Statistics describing UI frame render performance and dropped frames.
 */
data class FrameMetricsSnapshot(
    val totalFrames: Long = 0L,
    val jankFrames: Long = 0L,
    val severeJankFrames: Long = 0L,
    val averageFrameDurationMs: Float = 0f,
    val p50FrameDurationMs: Float = 0f,
    val p90FrameDurationMs: Float = 0f,
    val p99FrameDurationMs: Float = 0f,
) {
    /** Percentage of rendered frames exceeding 16.6ms threshold. */
    val jankRatePercent: Float
        get() = if (totalFrames > 0L) (jankFrames.toFloat() / totalFrames) * 100f else 0f
}

/**
 * Frame timing and jank tracking engine.
 *
 * Implements Phase 19 Task 5:
 * - Frame/jank metric logging and percentile computation.
 * - Detects frame drops during high-speed viewfinder display and review split-screen sliding.
 */
@Singleton
class FrameMetricsCollector @Inject constructor() {

    companion object {
        const val JANK_THRESHOLD_MS = 16.67f      // 60 fps boundary
        const val SEVERE_JANK_THRESHOLD_MS = 33.33f // 30 fps boundary
        private const val MAX_WINDOW_SIZE = 500
    }

    private val frameDurations = mutableListOf<Float>()
    private var totalFrames = 0L
    private var jankFrames = 0L
    private var severeJankFrames = 0L
    private var sumDuration = 0.0

    private val _metrics = MutableStateFlow(FrameMetricsSnapshot())
    val metrics: StateFlow<FrameMetricsSnapshot> = _metrics.asStateFlow()

    fun getSnapshot(): FrameMetricsSnapshot = _metrics.value

    fun recordFrameDurationNs(durationNs: Long) {
        recordFrameDuration(durationNs.toFloat() / 1_000_000f)
    }

    @Synchronized
    fun recordFrameDuration(durationMs: Float) {
        val safeDuration = max(0f, durationMs)
        totalFrames++
        sumDuration += safeDuration

        if (safeDuration > JANK_THRESHOLD_MS) jankFrames++
        if (safeDuration > SEVERE_JANK_THRESHOLD_MS) severeJankFrames++

        frameDurations.add(safeDuration)
        if (frameDurations.size > MAX_WINDOW_SIZE) {
            val removed = frameDurations.removeAt(0)
            sumDuration = max(0.0, sumDuration - removed)
        }

        recomputeSnapshot()
    }

    @Synchronized
    fun reset() {
        frameDurations.clear()
        totalFrames = 0L
        jankFrames = 0L
        severeJankFrames = 0L
        sumDuration = 0.0
        _metrics.value = FrameMetricsSnapshot()
    }

    private fun recomputeSnapshot() {
        if (frameDurations.isEmpty()) {
            _metrics.value = FrameMetricsSnapshot()
            return
        }

        val sorted = frameDurations.sorted()
        val p50 = getPercentile(sorted, 0.50f)
        val p90 = getPercentile(sorted, 0.90f)
        val p99 = getPercentile(sorted, 0.99f)
        val avg = (sumDuration / frameDurations.size).toFloat()

        _metrics.value = FrameMetricsSnapshot(
            totalFrames = totalFrames,
            jankFrames = jankFrames,
            severeJankFrames = severeJankFrames,
            averageFrameDurationMs = avg,
            p50FrameDurationMs = p50,
            p90FrameDurationMs = p90,
            p99FrameDurationMs = p99,
        )
    }

    private fun getPercentile(sorted: List<Float>, rank: Float): Float {
        if (sorted.isEmpty()) return 0f
        val index = ((sorted.size - 1) * rank).toInt().coerceIn(0, sorted.size - 1)
        return sorted[index]
    }
}
