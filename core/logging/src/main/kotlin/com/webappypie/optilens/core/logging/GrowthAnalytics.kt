package com.webappypie.optilens.core.logging

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stages of the in-app purchase and monetization conversion funnel.
 */
enum class PurchaseFunnelStage {
    PAYWALL_VIEWED,
    TRIAL_OFFER_CLICKED,
    PURCHASE_INITIATED,
    PURCHASE_COMPLETED,
    PURCHASE_CANCELLED,
    PURCHASE_FAILED,
    RESTORE_CLICKED,
}

/**
 * Privacy-preserving event analytics tracker for activation, retention, capture quality,
 * mode usage, AI Keep Rate, and conversion funnels.
 *
 * Privacy Invariants:
 * - Strictly zero personal identifying information (PII).
 * - Strictly zero photo bytes, thumbnails, or image metadata (location/faces/names).
 * - All tracking respects user opt-in/opt-out status configured in Settings.
 */
interface GrowthAnalytics {
    /** Track initial application install activation and performance cohort. */
    fun trackAppActivated(isFirstLaunch: Boolean, cohort: DeviceCohortMetadata)

    /** Track camera viewfinder readiness latency. */
    fun trackCameraReady(warmupMs: Long, mode: String)

    /** Track successful still image capture and computational pipeline duration. */
    fun trackCaptureSuccess(mode: String, durationMs: Long, burstCount: Int)

    /** Track camera mode selection. */
    fun trackModeUsage(mode: String, source: String)

    /**
     * Track AI post-capture review decision (keep or discard) to measure AI Keep Rate.
     * AI Keep Rate = total_kept / (total_kept + total_discarded).
     */
    fun trackAiEnhanceDecision(kept: Boolean, sliderPosition: Float, processingMs: Long)

    /** Track pipeline processing failures for regression monitoring. */
    fun trackProcessingFailure(stage: String, errorType: String, recoverable: Boolean)

    /** Track purchase conversion funnel progress. */
    fun trackPurchaseFunnel(stage: PurchaseFunnelStage, productId: String? = null, error: String? = null)

    /** Track retention milestone (day 1, day 7, day 30 active session). */
    fun trackRetentionSession(sessionIndex: Int, daysSinceInstall: Int)
}

/**
 * Default implementation of [GrowthAnalytics] using sanitized redaction logging.
 */
@Singleton
class DefaultGrowthAnalytics @Inject constructor(
    private val logger: AppLogger,
) : GrowthAnalytics {

    companion object {
        private const val TAG = "GrowthAnalytics"
    }

    override fun trackAppActivated(isFirstLaunch: Boolean, cohort: DeviceCohortMetadata) {
        logger.i(
            TAG,
            "Event: app_activated [firstLaunch=$isFirstLaunch, soc=${cohort.socFamily}, ram=${cohort.ramBucket}, api=${cohort.apiLevel}, hal=${cohort.hardwareLevel}, quirks=${cohort.quirkCount}]"
        )
    }

    override fun trackCameraReady(warmupMs: Long, mode: String) {
        logger.d(TAG, "Event: camera_ready [warmupMs=$warmupMs, mode=$mode]")
    }

    override fun trackCaptureSuccess(mode: String, durationMs: Long, burstCount: Int) {
        logger.i(TAG, "Event: capture_success [mode=$mode, durationMs=$durationMs, burstCount=$burstCount]")
    }

    override fun trackModeUsage(mode: String, source: String) {
        logger.d(TAG, "Event: mode_usage [mode=$mode, source=$source]")
    }

    override fun trackAiEnhanceDecision(kept: Boolean, sliderPosition: Float, processingMs: Long) {
        logger.i(
            TAG,
            "Event: ai_enhance_decision [decision=${if (kept) "KEEP" else "DISCARD"}, sliderPos=$sliderPosition, processingMs=$processingMs]"
        )
    }

    override fun trackProcessingFailure(stage: String, errorType: String, recoverable: Boolean) {
        logger.w(
            TAG,
            "Event: processing_failure [stage=$stage, errorType=$errorType, recoverable=$recoverable]"
        )
    }

    override fun trackPurchaseFunnel(stage: PurchaseFunnelStage, productId: String?, error: String?) {
        logger.i(
            TAG,
            "Event: purchase_funnel [stage=${stage.name}, product=${productId ?: "none"}, error=${error ?: "none"}]"
        )
    }

    override fun trackRetentionSession(sessionIndex: Int, daysSinceInstall: Int) {
        logger.i(
            TAG,
            "Event: retention_session [sessionIndex=$sessionIndex, daysSinceInstall=$daysSinceInstall]"
        )
    }
}
