package com.webappypie.optilens.core.logging

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Privacy-preserving event analytics tracker for business, monetization, and ad events.
 *
 * Privacy Invariants:
 * - Strictly zero personal identifying information (PII) logged.
 * - Zero user account, payment instrument, or photo contents logged.
 * - Ad events record coarse placement names only.
 */
interface MonetizationAnalytics {
    fun trackProScreenViewed(source: String)
    fun trackPurchaseStarted(productId: String)
    fun trackPurchaseSuccess(productId: String)
    fun trackPurchaseFailed(productId: String, error: String)
    fun trackPurchaseCancelled(productId: String)
    fun trackRestorePurchases(isSuccess: Boolean)
    fun trackAdImpression(placement: String)
    fun trackAdClicked(placement: String)
}

@Singleton
class DefaultMonetizationAnalytics @Inject constructor(
    private val logger: AppLogger,
) : MonetizationAnalytics {

    companion object {
        private const val TAG = "MonetizationAnalytics"
    }

    override fun trackProScreenViewed(source: String) {
        logger.d(TAG, "Event: pro_screen_viewed [source=$source]")
    }

    override fun trackPurchaseStarted(productId: String) {
        logger.d(TAG, "Event: purchase_started [product=$productId]")
    }

    override fun trackPurchaseSuccess(productId: String) {
        logger.i(TAG, "Event: purchase_success [product=$productId]")
    }

    override fun trackPurchaseFailed(productId: String, error: String) {
        logger.w(TAG, "Event: purchase_failed [product=$productId, error=$error]")
    }

    override fun trackPurchaseCancelled(productId: String) {
        logger.d(TAG, "Event: purchase_cancelled [product=$productId]")
    }

    override fun trackRestorePurchases(isSuccess: Boolean) {
        logger.d(TAG, "Event: restore_purchases [success=$isSuccess]")
    }

    override fun trackAdImpression(placement: String) {
        logger.d(TAG, "Event: ad_impression [placement=$placement]")
    }

    override fun trackAdClicked(placement: String) {
        logger.d(TAG, "Event: ad_clicked [placement=$placement]")
    }
}
