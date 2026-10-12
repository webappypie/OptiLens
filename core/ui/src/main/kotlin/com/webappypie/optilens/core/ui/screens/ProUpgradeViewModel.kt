package com.webappypie.optilens.core.ui.screens

import com.webappypie.optilens.core.ui.R
import com.webappypie.optilens.core.ui.text.UiText
import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.webappypie.optilens.core.common.config.RemoteConfigRepository
import com.webappypie.optilens.core.common.monetization.EntitlementRepository
import com.webappypie.optilens.core.common.monetization.OptiProductDetails
import com.webappypie.optilens.core.common.monetization.OptiProductIds
import com.webappypie.optilens.core.common.monetization.PurchaseStatus
import com.webappypie.optilens.core.logging.AppLogger
import com.webappypie.optilens.core.ui.billing.BillingManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProUpgradeUiState(
    /** Products returned by Google Play. Empty means purchases are unavailable right now. */
    val products: List<OptiProductDetails> = emptyList(),
    val selectedPlanIndex: Int = 0, // 0 = Annual, 1 = Lifetime
    val isPro: Boolean = false,
    val isLifetime: Boolean = false,
    val activeSubscriptionId: String? = null,
    val isPurchaseInProgress: Boolean = false,
    val isRestoring: Boolean = false,
    val promotionalCopy: String? = null,
    val errorMessage: UiText? = null,
    val successMessage: UiText? = null,
) {
    val annualProduct: OptiProductDetails?
        get() = products.firstOrNull { it.id == OptiProductIds.PRO_ANNUAL }

    val lifetimeProduct: OptiProductDetails?
        get() = products.firstOrNull { it.id == OptiProductIds.PRO_LIFETIME }

    val hasPurchasableProducts: Boolean get() = annualProduct != null || lifetimeProduct != null

    /** The chosen plan, or the only available one when the chosen plan is not offered by Play. */
    val selectedProduct: OptiProductDetails?
        get() = if (selectedPlanIndex == 0) annualProduct ?: lifetimeProduct else lifetimeProduct ?: annualProduct
}

@HiltViewModel
class ProUpgradeViewModel @Inject constructor(
    private val billingManager: BillingManager,
    private val entitlementRepository: EntitlementRepository,
    private val remoteConfigRepository: RemoteConfigRepository,
    private val logger: AppLogger,
) : ViewModel() {

    companion object {
        private const val TAG = "ProUpgradeViewModel"
    }

    private val _internalState = MutableStateFlow(ProUpgradeInternalState())

    val uiState: StateFlow<ProUpgradeUiState> = combine(
        _internalState,
        entitlementRepository.entitlements,
        entitlementRepository.availableProducts,
        entitlementRepository.purchaseStatus,
        remoteConfigRepository.featureFlags,
    ) { internal, entitlements, products, purchaseStatus, flags ->
        val isPending = purchaseStatus is PurchaseStatus.Pending
        val error = if (purchaseStatus is PurchaseStatus.Error) UiText(R.string.pro_purchase_failed) else internal.errorMessage
        val success = if (purchaseStatus is PurchaseStatus.Success) UiText(R.string.pro_welcome) else internal.successMessage

        ProUpgradeUiState(
            products = products,
            selectedPlanIndex = internal.selectedPlanIndex,
            isPro = entitlements.hasFullAccess,
            isLifetime = entitlements.isLifetime,
            activeSubscriptionId = entitlements.activeSubscriptionId,
            isPurchaseInProgress = isPending || internal.isPurchaseInProgress,
            isRestoring = internal.isRestoring,
            promotionalCopy = flags.promotionalCopy,
            errorMessage = error,
            successMessage = success,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = ProUpgradeUiState(),
    )

    init {
        billingManager.startConnection {
            billingManager.queryProductDetails()
        }
    }

    fun selectPlan(index: Int) {
        _internalState.update { it.copy(selectedPlanIndex = index) }
    }

    fun startPurchase(activity: Activity) {
        val selected = uiState.value.selectedProduct ?: return
        logger.d(TAG, "Initiating purchase flow for: ${selected.id}")
        _internalState.update { it.copy(isPurchaseInProgress = true, errorMessage = null, successMessage = null) }
        billingManager.launchBillingFlow(activity, selected.id)
    }

    fun restorePurchases() {
        if (_internalState.value.isRestoring) return
        _internalState.update { it.copy(isRestoring = true, errorMessage = null, successMessage = null) }
        logger.d(TAG, "Restoring purchases")

        viewModelScope.launch {
            billingManager.restorePurchases { entitlements ->
                _internalState.update {
                    it.copy(
                        isRestoring = false,
                        successMessage = if (entitlements.hasFullAccess) UiText(R.string.pro_restored) else null,
                        errorMessage = if (!entitlements.hasFullAccess) UiText(R.string.pro_nothing_to_restore) else null,
                    )
                }
            }
        }
    }

    fun clearMessages() {
        _internalState.update { it.copy(errorMessage = null, successMessage = null) }
        entitlementRepository.setPurchaseStatus(PurchaseStatus.Idle)
    }

    private data class ProUpgradeInternalState(
        val selectedPlanIndex: Int = 0,
        val isPurchaseInProgress: Boolean = false,
        val isRestoring: Boolean = false,
        val errorMessage: UiText? = null,
        val successMessage: UiText? = null,
    )
}
