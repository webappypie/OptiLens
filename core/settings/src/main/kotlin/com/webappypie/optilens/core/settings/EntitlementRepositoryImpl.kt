package com.webappypie.optilens.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.webappypie.optilens.core.common.coroutines.AppDispatchers
import com.webappypie.optilens.core.common.monetization.BillingConnectionState
import com.webappypie.optilens.core.common.monetization.EntitlementRepository
import com.webappypie.optilens.core.common.monetization.OptiProductDetails
import com.webappypie.optilens.core.common.monetization.PurchaseStatus
import com.webappypie.optilens.core.common.monetization.UserEntitlements
import com.webappypie.optilens.core.common.result.OptiResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production implementation of [EntitlementRepository] backed by encrypted / private DataStore.
 *
 * Ensures offline resilience: once verified, user entitlements survive network drops,
 * plane rides, and process death.
 */
@Singleton
class EntitlementRepositoryImpl(
    private val dataStore: DataStore<Preferences>,
    private val dispatchers: AppDispatchers,
    private val scope: CoroutineScope,
) : EntitlementRepository {

    @Inject
    constructor(
        dataStore: DataStore<Preferences>,
        dispatchers: AppDispatchers,
    ) : this(
        dataStore = dataStore,
        dispatchers = dispatchers,
        scope = CoroutineScope(dispatchers.io + SupervisorJob()),
    )

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private object Keys {
        val ENTITLEMENTS = stringPreferencesKey("user_entitlements_json")
    }

    private val _entitlements = MutableStateFlow(UserEntitlements.FREE)
    override val entitlements: StateFlow<UserEntitlements> = _entitlements.asStateFlow()

    override val isPro: StateFlow<Boolean> = _entitlements.map { it.hasFullAccess }
        .stateIn(scope, SharingStarted.Eagerly, false)

    override val unlockedPacks: StateFlow<Set<String>> = _entitlements.map { it.unlockedPacks }
        .stateIn(scope, SharingStarted.Eagerly, emptySet())

    private val _availableProducts = MutableStateFlow(OptiProductDetails.DEFAULT_PRODUCTS)
    override val availableProducts: StateFlow<List<OptiProductDetails>> = _availableProducts.asStateFlow()

    private val _purchaseStatus = MutableStateFlow<PurchaseStatus>(PurchaseStatus.Idle)
    override val purchaseStatus: StateFlow<PurchaseStatus> = _purchaseStatus.asStateFlow()

    private val _billingConnectionState = MutableStateFlow(BillingConnectionState.DISCONNECTED)
    override val billingConnectionState: StateFlow<BillingConnectionState> = _billingConnectionState.asStateFlow()

    init {
        scope.launch {
            dataStore.data.collect { prefs ->
                val jsonStr = prefs[Keys.ENTITLEMENTS]
                if (!jsonStr.isNullOrBlank()) {
                    try {
                        val parsed = json.decodeFromString<UserEntitlements>(jsonStr)
                        _entitlements.value = parsed
                    } catch (e: Exception) {
                        // Retain current in-memory value
                    }
                }
            }
        }
    }

    override suspend fun refreshPurchases(): OptiResult<UserEntitlements> = withContext(dispatchers.io) {
        OptiResult.Success(_entitlements.value)
    }

    override suspend fun restorePurchases(): OptiResult<UserEntitlements> = withContext(dispatchers.io) {
        OptiResult.Success(_entitlements.value)
    }

    override fun setPurchaseStatus(status: PurchaseStatus) {
        _purchaseStatus.value = status
    }

    override suspend fun updateEntitlements(entitlements: UserEntitlements): Unit = withContext(dispatchers.io) {
        _entitlements.value = entitlements
        try {
            val jsonStr = json.encodeToString(UserEntitlements.serializer(), entitlements)
            dataStore.edit { prefs ->
                prefs[Keys.ENTITLEMENTS] = jsonStr
            }
        } catch (e: Exception) {
            // Memory state updated
        }
    }

    fun updateAvailableProducts(products: List<OptiProductDetails>) {
        _availableProducts.value = products
    }

    fun updateBillingConnectionState(state: BillingConnectionState) {
        _billingConnectionState.value = state
    }
}
