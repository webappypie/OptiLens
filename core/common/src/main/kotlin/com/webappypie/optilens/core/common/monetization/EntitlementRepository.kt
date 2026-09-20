package com.webappypie.optilens.core.common.monetization

import com.webappypie.optilens.core.common.result.OptiResult
import kotlinx.coroutines.flow.StateFlow

/**
 * Central repository governing application entitlements, purchase states, and product catalogs.
 *
 * Responsibilities:
 * 1. Single source of truth for Pro and pack entitlements across all camera and imaging features.
 * 2. Secure offline caching in DataStore ensuring paid features work without active connectivity.
 * 3. Reactive flows for UI binding (Pro badge, paywall screens, ad suppression).
 * 4. Reconciliation with Google Play Billing queries.
 */
interface EntitlementRepository {
    /** Reactive stream of the user's active verified entitlements. */
    val entitlements: StateFlow<UserEntitlements>

    /** Convenience reactive flow indicating whether user has any active Pro access (Annual, Monthly, Lifetime). */
    val isPro: StateFlow<Boolean>

    /** Set of modular pack identifiers unlocked by the user. */
    val unlockedPacks: StateFlow<Set<String>>

    /** Available products retrieved from Google Play Billing (with local fallback). */
    val availableProducts: StateFlow<List<OptiProductDetails>>

    /** State of the active or most recent purchase/restore transaction. */
    val purchaseStatus: StateFlow<PurchaseStatus>

    /** Real-time status of the connection to Google Play Billing service. */
    val billingConnectionState: StateFlow<BillingConnectionState>

    /** Re-queries Google Play Billing for owned in-app items and subscriptions. */
    suspend fun refreshPurchases(): OptiResult<UserEntitlements>

    /** Explicit user-triggered restore purchases flow. */
    suspend fun restorePurchases(): OptiResult<UserEntitlements>

    /** Updates the purchase status (e.g. from billing callbacks or UI dismissals). */
    fun setPurchaseStatus(status: PurchaseStatus)

    /** Atomically persists new verified entitlements. */
    suspend fun updateEntitlements(entitlements: UserEntitlements)
}
