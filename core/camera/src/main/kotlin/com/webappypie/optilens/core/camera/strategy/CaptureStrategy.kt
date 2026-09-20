package com.webappypie.optilens.core.camera.strategy

/**
 * Recommended capture execution pipeline mode decided by [CaptureStrategyEngine].
 */
enum class CaptureStrategyMode {
    SINGLE_FRAME,
    MULTI_FRAME_HDR,
    NIGHT_STACK,
    PORTRAIT_DEPTH,
    DOCUMENT_ENHANCE,
    ACTION_FREEZE,
    SUPER_RES_ZOOM,
    BEST_SHOT_BURST,
    PET_FREEZE,
    FOOD_OPTIMIZED,
    MOON_ASSIST,
    WILDLIFE_BURST;
}

/**
 * Non-intrusive UI hint recommendation for the viewfinder.
 */
enum class CaptureUiHint(val message: String) {
    HOLD_STEADY("Hold steady"),
    NIGHT_SUGGESTED("Night mode suggested"),
    HDR_SUGGESTED("HDR recommended"),
    BACKLIGHT_DETECTED("Backlight detected"),
    DOCUMENT_DETECTED("Document detected"),
    AI_ZOOM_ACTIVE("AI Zoom Active"),
    LENS_DIRTY_WARNING("Clean camera lens for clearer photos"),
    MOON_DETECTED("Moon detected — telephoto recommended"),
    WILDLIFE_DETECTED("Wildlife detected — fast shutter active"),
    STABILIZE_CAMERA("Hold steady for Moon capture"),
    SCENE_CHIP("");
}

/**
 * Shutter timing bias for computational acquisition.
 */
enum class ShutterPriority {
    AUTO,
    FAST_ACTION,
    LONG_STABILIZED;
}

/**
 * Diagnostic metrics evaluating the performance and overhead of the analysis pipeline.
 */
data class AnalysisDiagnostics(
    val analysisFps: Float = 0.0f,
    val inferenceLatencyMs: Long = 0L,
    val frameProcessingTimeMs: Long = 0L,
) {
    companion object {
        val EMPTY = AnalysisDiagnostics()
    }
}

/**
 * Strategy decision bundle instructing the capture pipeline and informing the viewfinder UI.
 *
 * @param mode The optimal computational imaging mode for current conditions.
 * @param recommendedFrameCount Number of frames to acquire in multi-frame bursts (Phase 07).
 * @param exposureEvOffsets EV exposure offsets for bracketed acquisition (e.g. [-2, 0, +2]).
 * @param uiHint Optional subtle UI hint to present to the user.
 * @param shutterPriority Recommended shutter bias.
 * @param diagnostics Performance metrics for real-time analysis profiling.
 * @param timestampMs Generation timestamp.
 */
data class CaptureStrategy(
    val mode: CaptureStrategyMode = CaptureStrategyMode.SINGLE_FRAME,
    val recommendedFrameCount: Int = 1,
    val exposureEvOffsets: List<Int> = listOf(0),
    val uiHint: CaptureUiHint? = null,
    val shutterPriority: ShutterPriority = ShutterPriority.AUTO,
    val diagnostics: AnalysisDiagnostics = AnalysisDiagnostics.EMPTY,
    val timestampMs: Long = System.currentTimeMillis(),
) {
    companion object {
        val DEFAULT = CaptureStrategy()
    }
}
