package com.webappypie.optilens.core.ui.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.webappypie.optilens.core.common.monetization.BillingConnectionState
import com.webappypie.optilens.core.common.monetization.EntitlementRepository
import com.webappypie.optilens.core.common.monetization.OptiProductDetails
import com.webappypie.optilens.core.common.monetization.OptiProductIds
import com.webappypie.optilens.core.common.monetization.ProductBillingType
import com.webappypie.optilens.core.common.monetization.ProductCategory
import com.webappypie.optilens.core.common.monetization.PurchaseStatus
import com.webappypie.optilens.core.common.monetization.UserEntitlements
import com.webappypie.optilens.core.logging.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Unit-test fake implementation of [BillingManager].
 */
class FakeBillingManager(
    private val entitlementRepository: EntitlementRepository,
) : BillingManager {

    /** Test-only catalogue; prices are fixture values, never shown in the app. */
    val testProducts = listOf(
        OptiProductDetails(
            id = OptiProductIds.PRO_LIFETIME,
            category = ProductCategory.PRO_TIER,
            billingType = ProductBillingType.ONE_TIME,
            title = "Pro Lifetime (test)",
            description = "test fixture",
            formattedPrice = "TEST-1",
        ),
        OptiProductDetails(
            id = OptiProductIds.PRO_ANNUAL,
            category = ProductCategory.PRO_TIER,
            billingType = ProductBillingType.SUBSCRIPTION,
            title = "Pro Annual (test)",
            description = "test fixture",
            formattedPrice = "TEST-2",
            hasFreeTrial = true,
        ),
    )

    private val _connectionState = MutableStateFlow(BillingConnectionState.CONNECTED)
    override val connectionState: StateFlow<BillingConnectionState> = _connectionState.asStateFlow()

    override fun startConnection(onConnected: () -> Unit) {
        _connectionState.value = BillingConnectionState.CONNECTED
        onConnected()
    }

    override fun queryProductDetails(onDetailsReceived: (List<OptiProductDetails>) -> Unit) {
        entitlementRepository.updateAvailableProducts(testProducts)
        onDetailsReceived(testProducts)
    }

    override fun launchBillingFlow(activity: Activity, productId: String) {
        entitlementRepository.setPurchaseStatus(PurchaseStatus.Pending(productId))
        val current = entitlementRepository.entitlements.value
        val updated = when (productId) {
            OptiProductIds.PRO_LIFETIME -> current.copy(isPro = true, isLifetime = true)
            OptiProductIds.PRO_ANNUAL, OptiProductIds.PRO_MONTHLY -> current.copy(isPro = true, activeSubscriptionId = productId)
            OptiProductIds.PACK_AI_TOOLS, OptiProductIds.PACK_LOOKS -> current.copy(unlockedPacks = current.unlockedPacks + productId)
            else -> current
        }
        kotlinx.coroutines.runBlocking {
            entitlementRepository.updateEntitlements(updated)
            entitlementRepository.setPurchaseStatus(PurchaseStatus.Success(productId))
        }
    }

    override fun restorePurchases(onComplete: (UserEntitlements) -> Unit) {
        onComplete(entitlementRepository.entitlements.value)
    }
}
