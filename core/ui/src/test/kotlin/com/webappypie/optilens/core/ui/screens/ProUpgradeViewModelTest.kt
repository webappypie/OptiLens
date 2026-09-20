package com.webappypie.optilens.core.ui.screens

import android.app.Activity
import app.cash.turbine.test
import com.webappypie.optilens.core.common.config.LocalRemoteConfigRepository
import com.webappypie.optilens.core.common.monetization.BillingConnectionState
import com.webappypie.optilens.core.common.monetization.EntitlementRepository
import com.webappypie.optilens.core.common.monetization.OptiProductDetails
import com.webappypie.optilens.core.common.monetization.OptiProductIds
import com.webappypie.optilens.core.common.monetization.PurchaseStatus
import com.webappypie.optilens.core.common.monetization.UserEntitlements
import com.webappypie.optilens.core.common.result.OptiResult
import com.webappypie.optilens.core.logging.NoOpLogger
import com.webappypie.optilens.core.ui.billing.FakeBillingManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProUpgradeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeEntitlementRepo: FakeTestEntitlementRepo
    private lateinit var fakeBillingManager: FakeBillingManager
    private lateinit var remoteConfigRepo: LocalRemoteConfigRepository
    private lateinit var viewModel: ProUpgradeViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeEntitlementRepo = FakeTestEntitlementRepo()
        fakeBillingManager = FakeBillingManager(fakeEntitlementRepo)
        remoteConfigRepo = LocalRemoteConfigRepository()
        viewModel = ProUpgradeViewModel(
            billingManager = fakeBillingManager,
            entitlementRepository = fakeEntitlementRepo,
            remoteConfigRepository = remoteConfigRepo,
            logger = NoOpLogger(),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial uiState defaults to free tier and selects annual plan`() = runTest {
        viewModel.uiState.test {
            val initial = awaitItem()
            assertFalse(initial.isPro)
            assertFalse(initial.isLifetime)
            assertEquals(0, initial.selectedPlanIndex)
            assertNotNull(initial.annualProduct)
            assertNotNull(initial.lifetimeProduct)
            assertEquals(initial.annualProduct?.id, initial.selectedProduct?.id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `selectPlan updates selected plan and product`() = runTest {
        viewModel.uiState.test {
            awaitItem() // Initial

            viewModel.selectPlan(1) // Select Lifetime
            val lifetimeState = awaitItem()
            assertEquals(1, lifetimeState.selectedPlanIndex)
            assertEquals(OptiProductIds.PRO_LIFETIME, lifetimeState.selectedProduct?.id)

            viewModel.selectPlan(0) // Select Annual
            val annualState = awaitItem()
            assertEquals(0, annualState.selectedPlanIndex)
            assertEquals(OptiProductIds.PRO_ANNUAL, annualState.selectedProduct?.id)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `startPurchase with FakeBillingManager initiates flow and updates Pro state`() = runTest {
        viewModel.uiState.test {
            awaitItem() // Initial

            viewModel.selectPlan(1) // Lifetime
            awaitItem()

            viewModel.startPurchase(Activity())
            advanceUntilIdle()

            // State should update to Pro Lifetime
            val proState = expectMostRecentItem()
            assertTrue(proState.isPro)
            assertTrue(proState.isLifetime)
            assertEquals("Welcome to OptiLens Pro!", proState.successMessage)
        }
    }

    @Test
    fun `restorePurchases restores previous entitlements if user already holds Pro`() = runTest {
        // Preset entitlement in repository to Pro
        fakeEntitlementRepo.updateEntitlements(UserEntitlements.LIFETIME_PRO)

        viewModel.uiState.test {
            awaitItem() // Initial state

            viewModel.restorePurchases()
            advanceUntilIdle()

            val restoredState = expectMostRecentItem()
            assertTrue(restoredState.isPro)
            assertEquals("Purchases restored successfully!", restoredState.successMessage)
            assertNull(restoredState.errorMessage)
        }
    }

    @Test
    fun `clearMessages clears transient messages`() = runTest {
        viewModel.uiState.test {
            awaitItem() // Initial

            // Trigger purchase to get success message
            viewModel.startPurchase(Activity())
            advanceUntilIdle()

            val successState = expectMostRecentItem()
            assertEquals("Welcome to OptiLens Pro!", successState.successMessage)

            viewModel.clearMessages()
            advanceUntilIdle()

            val clearedState = expectMostRecentItem()
            assertNull(clearedState.successMessage)
            assertNull(clearedState.errorMessage)
        }
    }

    private class FakeTestEntitlementRepo(
        initialEntitlements: UserEntitlements = UserEntitlements.FREE,
    ) : EntitlementRepository {
        private val _entitlements = MutableStateFlow(initialEntitlements)
        override val entitlements: StateFlow<UserEntitlements> = _entitlements.asStateFlow()

        private val _isPro = MutableStateFlow(initialEntitlements.hasFullAccess)
        override val isPro: StateFlow<Boolean> = _isPro.asStateFlow()

        private val _unlockedPacks = MutableStateFlow(initialEntitlements.unlockedPacks)
        override val unlockedPacks: StateFlow<Set<String>> = _unlockedPacks.asStateFlow()

        private val _availableProducts = MutableStateFlow(OptiProductDetails.DEFAULT_PRODUCTS)
        override val availableProducts: StateFlow<List<OptiProductDetails>> = _availableProducts.asStateFlow()

        private val _purchaseStatus = MutableStateFlow<PurchaseStatus>(PurchaseStatus.Idle)
        override val purchaseStatus: StateFlow<PurchaseStatus> = _purchaseStatus.asStateFlow()

        override val billingConnectionState: StateFlow<BillingConnectionState> =
            MutableStateFlow(BillingConnectionState.CONNECTED)

        override suspend fun refreshPurchases(): OptiResult<UserEntitlements> =
            OptiResult.Success(_entitlements.value)

        override suspend fun restorePurchases(): OptiResult<UserEntitlements> =
            OptiResult.Success(_entitlements.value)

        override fun setPurchaseStatus(status: PurchaseStatus) {
            _purchaseStatus.value = status
        }

        override suspend fun updateEntitlements(entitlements: UserEntitlements) {
            _entitlements.value = entitlements
            _isPro.value = entitlements.hasFullAccess
            _unlockedPacks.value = entitlements.unlockedPacks
        }
    }
}
