package com.webappypie.optilens.core.imaging.alignment

/**
 * Performance and quality profiling metrics for multi-frame alignment.
 */
data class AlignmentDiagnostics(
    val scoringDurationMs: Long,
    val alignmentDurationMs: Long,
    val totalDurationMs: Long,
    val candidateCount: Int,
    val alignedCount: Int,
    val rejectedCount: Int,
    val averageConfidence: Float,
)

/**
 * Output product of Phase 08: a registered, scored, and motion-masked frame stack
 * ready for multi-frame computational fusion in Phase 09.
 *
 * Implements [AutoCloseable] to cascade closure to all constituent frames.
 *
 * @param referenceIndex Index of the anchor reference frame.
 * @param referenceFrame The chosen anchor reference frame.
 * @param alignedFrames Successfully aligned non-reference frames.
 * @param rejectedFrames Frames rejected due to low confidence, severe blur, or occlusion.
 * @param diagnostics Performance and timing metrics.
 */
data class AlignedStack(
    val referenceIndex: Int,
    val referenceFrame: AlignedFrame,
    val alignedFrames: List<AlignedFrame>,
    val rejectedFrames: List<AlignedFrame>,
    val diagnostics: AlignmentDiagnostics,
) : AutoCloseable {

    /** Total number of usable frames (reference + aligned). */
    val usableFrameCount: Int
        get() = 1 + alignedFrames.size

    val allFrames: List<AlignedFrame>
        get() = buildList {
            add(referenceFrame)
            addAll(alignedFrames)
            addAll(rejectedFrames)
        }

    override fun close() {
        referenceFrame.close()
        alignedFrames.forEach { it.close() }
        rejectedFrames.forEach { it.close() }
    }
}
