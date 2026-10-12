package com.webappypie.optilens.core.common.monetization

import kotlinx.serialization.Serializable

/**
 * Known product identifiers registered in Google Play Console.
 */
object OptiProductIds {
    /** Lifetime one-time unlock granting perpetual Pro access and removing all ads. */
    const val PRO_LIFETIME = "optilens_pro_lifetime"

    /** Annual recurring subscription with a 7-day free trial. */
    const val PRO_ANNUAL = "optilens_pro_annual"

    /** Monthly recurring subscription. */
    const val PRO_MONTHLY = "optilens_pro_monthly"

    /** Modular one-time pack unlocking advanced AI computational tools. */
    const val PACK_AI_TOOLS = "optilens_pack_ai"

    /** Modular one-time pack unlocking creative film simulation looks. */
    const val PACK_LOOKS = "optilens_pack_looks"

    val ALL_PRO_PRODUCTS = setOf(PRO_LIFETIME, PRO_ANNUAL, PRO_MONTHLY)
    val ALL_PACKS = setOf(PACK_AI_TOOLS, PACK_LOOKS)
    val ALL_PRODUCTS = ALL_PRO_PRODUCTS + ALL_PACKS
}

enum class ProductCategory {
    PRO_TIER,
    MODULAR_PACK,
}

enum class ProductBillingType {
    ONE_TIME,
    SUBSCRIPTION,
}

/**
 * Standardized presentation data for an in-app product or subscription.
 */
@Serializable
data class OptiProductDetails(
    val id: String,
    val category: ProductCategory,
    val billingType: ProductBillingType,
    val title: String,
    val description: String,
    val formattedPrice: String,
    val hasFreeTrial: Boolean = false,
)

/**
 * User's verified entitlement snapshot.
 *
 * Security: Persisted to local private storage so user retains offline access to paid features.
 * Automatically refreshed and verified when network and Play Store are reachable.
 */
@Serializable
data class UserEntitlements(
    val isPro: Boolean = false,
    val isLifetime: Boolean = false,
    val activeSubscriptionId: String? = null,
    val unlockedPacks: Set<String> = emptySet(),
    val lastVerifiedTimestampMs: Long = 0L,
) {
    val hasFullAccess: Boolean
        get() = isPro || isLifetime

    fun hasPack(packId: String): Boolean = hasFullAccess || unlockedPacks.contains(packId)

    companion object {
        val FREE = UserEntitlements()
        val LIFETIME_PRO = UserEntitlements(
            isPro = true,
            isLifetime = true,
            unlockedPacks = OptiProductIds.ALL_PACKS,
        )
    }
}

/**
 * Dynamic state of the billing connection to Google Play.
 */
enum class BillingConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    UNAVAILABLE,
}

/**
 * Result state of a user-initiated purchase or restore flow.
 */
sealed interface PurchaseStatus {
    data object Idle : PurchaseStatus
    data class Pending(val productId: String) : PurchaseStatus
    data class Success(val productId: String) : PurchaseStatus
    data class Error(val errorCode: String, val displayMessage: String) : PurchaseStatus
}
