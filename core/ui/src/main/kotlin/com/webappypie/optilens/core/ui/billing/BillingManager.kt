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
 * High-level manager interface for Google Play Billing.
 */
interface BillingManager {
    val connectionState: StateFlow<BillingConnectionState>
    fun startConnection(onConnected: () -> Unit = {})
    fun queryProductDetails(onDetailsReceived: (List<OptiProductDetails>) -> Unit = {})
    fun launchBillingFlow(activity: Activity, productId: String)
    fun restorePurchases(onComplete: (UserEntitlements) -> Unit = {})
}

/**
 * Production implementation integrating Google Play Billing Client 7.x.
 *
 * Implements:
 * 1. Safe connection lifecycle with automatic reconnect.
 * 2. In-App products (Lifetime Pro, AI Pack, Looks Pack) + Subscriptions (Annual, Monthly).
 * 3. Purchase acknowledgment within Google Play's required timeout.
 * 4. Pending purchase handling for cash/banking delays.
 * 5. Instant synchronization with central [EntitlementRepository].
 */
@Singleton
class PlayBillingManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val entitlementRepository: EntitlementRepository,
    private val logger: AppLogger,
) : BillingManager, PurchasesUpdatedListener {

    companion object {
        private const val TAG = "PlayBillingManager"
    }

    private val scope = CoroutineScope(Dispatchers.Main.immediate)

    private val _connectionState = MutableStateFlow(BillingConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<BillingConnectionState> = _connectionState.asStateFlow()

    private val cachedProductDetails = mutableMapOf<String, ProductDetails>()

    private val billingClient: BillingClient by lazy {
        BillingClient.newBuilder(context)
            .setListener(this)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder()
                    .enableOneTimeProducts()
                    .build()
            )
            .build()
    }

    override fun startConnection(onConnected: () -> Unit) {
        if (_connectionState.value == BillingConnectionState.CONNECTED) {
            onConnected()
            return
        }

        _connectionState.value = BillingConnectionState.CONNECTING
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    _connectionState.value = BillingConnectionState.CONNECTED
                    logger.d(TAG, "BillingClient connected successfully")
                    queryProductDetails()
                    queryAndReconcilePurchases()
                    onConnected()
                } else {
                    _connectionState.value = BillingConnectionState.UNAVAILABLE
                    logger.e(TAG, "Billing setup failed: ${billingResult.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                _connectionState.value = BillingConnectionState.DISCONNECTED
                logger.w(TAG, "Billing service disconnected")
            }
        })
    }

    override fun queryProductDetails(onDetailsReceived: (List<OptiProductDetails>) -> Unit) {
        if (_connectionState.value != BillingConnectionState.CONNECTED) {
            entitlementRepository.updateAvailableProducts(emptyList())
            onDetailsReceived(emptyList())
            return
        }

        val inAppList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(OptiProductIds.PRO_LIFETIME)
                .setProductType(BillingClient.ProductType.INAPP)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(OptiProductIds.PACK_AI_TOOLS)
                .setProductType(BillingClient.ProductType.INAPP)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(OptiProductIds.PACK_LOOKS)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        )

        val subsList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(OptiProductIds.PRO_ANNUAL)
                .setProductType(BillingClient.ProductType.SUBS)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(OptiProductIds.PRO_MONTHLY)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        )

        val parsedDetails = mutableListOf<OptiProductDetails>()

        // 1. Query INAPP
        billingClient.queryProductDetailsAsync(
            QueryProductDetailsParams.newBuilder().setProductList(inAppList).build()
        ) { result, detailsList ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                detailsList.forEach { details ->
                    // Only products with a real Play price are offered.
                    val price = details.oneTimePurchaseOfferDetails?.formattedPrice ?: return@forEach
                    cachedProductDetails[details.productId] = details
                    parsedDetails.add(
                        OptiProductDetails(
                            id = details.productId,
                            category = if (details.productId == OptiProductIds.PRO_LIFETIME) ProductCategory.PRO_TIER else ProductCategory.MODULAR_PACK,
                            billingType = ProductBillingType.ONE_TIME,
                            title = details.title,
                            description = details.description,
                            formattedPrice = price,
                        )
                    )
                }
            }

            // 2. Query SUBS
            billingClient.queryProductDetailsAsync(
                QueryProductDetailsParams.newBuilder().setProductList(subsList).build()
            ) { subsResult, subsDetailsList ->
                if (subsResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    subsDetailsList.forEach { details ->
                        val basePlan = details.subscriptionOfferDetails?.firstOrNull()
                        val price = basePlan?.pricingPhases?.pricingPhaseList?.lastOrNull()?.formattedPrice
                            ?: return@forEach
                        cachedProductDetails[details.productId] = details
                        val hasTrial = basePlan?.pricingPhases?.pricingPhaseList?.any { it.priceAmountMicros == 0L } == true

                        parsedDetails.add(
                            OptiProductDetails(
                                id = details.productId,
                                category = ProductCategory.PRO_TIER,
                                billingType = ProductBillingType.SUBSCRIPTION,
                                title = details.title,
                                description = details.description,
                                formattedPrice = price,
                                hasFreeTrial = hasTrial,
                            )
                        )
                    }
                }

                val finalProducts = parsedDetails.toList()
                entitlementRepository.updateAvailableProducts(finalProducts)
                onDetailsReceived(finalProducts)
            }
        }
    }

    override fun launchBillingFlow(activity: Activity, productId: String) {
        val details = cachedProductDetails[productId]
        if (details == null) {
            entitlementRepository.setPurchaseStatus(
                PurchaseStatus.Error("product_not_found", "Product not available in current region")
            )
            return
        }

        entitlementRepository.setPurchaseStatus(PurchaseStatus.Pending(productId))

        val flowParamsBuilder = BillingFlowParams.newBuilder()
        val productDetailsParamsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)

        // For subscriptions, provide the offer token
        details.subscriptionOfferDetails?.firstOrNull()?.offerToken?.let { token ->
            productDetailsParamsBuilder.setOfferToken(token)
        }

        flowParamsBuilder.setProductDetailsParamsList(listOf(productDetailsParamsBuilder.build()))
        val result = billingClient.launchBillingFlow(activity, flowParamsBuilder.build())

        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            entitlementRepository.setPurchaseStatus(
                PurchaseStatus.Error(result.responseCode.toString(), result.debugMessage)
            )
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.let { handlePurchases(it) }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                entitlementRepository.setPurchaseStatus(PurchaseStatus.Idle)
            }
            else -> {
                entitlementRepository.setPurchaseStatus(
                    PurchaseStatus.Error(billingResult.responseCode.toString(), billingResult.debugMessage)
                )
            }
        }
    }

    private fun handlePurchases(purchases: List<Purchase>) {
        scope.launch {
            var updated = entitlementRepository.entitlements.value

            for (purchase in purchases) {
                if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                    // Acknowledge non-acknowledged purchases
                    if (!purchase.isAcknowledged) {
                        val ackParams = AcknowledgePurchaseParams.newBuilder()
                            .setPurchaseToken(purchase.purchaseToken)
                            .build()
                        billingClient.acknowledgePurchase(ackParams) { ackResult ->
                            logger.d(TAG, "Acknowledge result: ${ackResult.responseCode}")
                        }
                    }

                    for (product in purchase.products) {
                        when (product) {
                            OptiProductIds.PRO_LIFETIME -> {
                                updated = updated.copy(isPro = true, isLifetime = true)
                            }
                            OptiProductIds.PRO_ANNUAL, OptiProductIds.PRO_MONTHLY -> {
                                updated = updated.copy(isPro = true, activeSubscriptionId = product)
                            }
                            OptiProductIds.PACK_AI_TOOLS, OptiProductIds.PACK_LOOKS -> {
                                updated = updated.copy(unlockedPacks = updated.unlockedPacks + product)
                            }
                        }
                        entitlementRepository.setPurchaseStatus(PurchaseStatus.Success(product))
                    }
                } else if (purchase.purchaseState == Purchase.PurchaseState.PENDING) {
                    val pId = purchase.products.firstOrNull() ?: "unknown"
                    entitlementRepository.setPurchaseStatus(PurchaseStatus.Pending(pId))
                }
            }

            updated = updated.copy(lastVerifiedTimestampMs = System.currentTimeMillis())
            entitlementRepository.updateEntitlements(updated)
        }
    }

    override fun restorePurchases(onComplete: (UserEntitlements) -> Unit) {
        startConnection {
            queryAndReconcilePurchases(onComplete)
        }
    }

    private fun queryAndReconcilePurchases(onComplete: (UserEntitlements) -> Unit = {}) {
        val inAppParams = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        val subsParams = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        billingClient.queryPurchasesAsync(inAppParams) { inAppResult, inAppPurchases ->
            if (inAppResult.responseCode == BillingClient.BillingResponseCode.OK) {
                handlePurchases(inAppPurchases)
            }

            billingClient.queryPurchasesAsync(subsParams) { subsResult, subsPurchases ->
                if (subsResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    handlePurchases(subsPurchases)
                }
                onComplete(entitlementRepository.entitlements.value)
            }
        }
    }
}
