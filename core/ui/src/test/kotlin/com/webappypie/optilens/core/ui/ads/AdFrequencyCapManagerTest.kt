package com.webappypie.optilens.core.ui.ads

import com.webappypie.optilens.core.common.config.LocalRemoteConfigRepository
import com.webappypie.optilens.core.common.feature.CustomFeatureFlags
import com.webappypie.optilens.core.common.feature.LocalFeatureFlags
import com.webappypie.optilens.core.common.monetization.BillingConnectionState
import com.webappypie.optilens.core.common.monetization.EntitlementRepository
import com.webappypie.optilens.core.common.monetization.OptiProductDetails
import com.webappypie.optilens.core.common.monetization.PurchaseStatus
import com.webappypie.optilens.core.common.monetization.UserEntitlements
import com.webappypie.optilens.core.common.result.OptiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AdFrequencyCapManagerTest {

    private lateinit var remoteConfigRepository: LocalRemoteConfigRepository
    private lateinit var entitlementRepository: FakeTestEntitlementRepository
    private lateinit var frequencyCapManager: AdFrequencyCapManager

    @Before
    fun setUp() {
        remoteConfigRepository = LocalRemoteConfigRepository()
        entitlementRepository = FakeTestEntitlementRepository()
        frequencyCapManager = AdFrequencyCapManager(remoteConfigRepository, entitlementRepository)
    }

    @Test
    fun `canShowInterstitial returns false until minimum captures met`() {
        // Defaults: adMinCapturesBetweenInterstitials = 3
        assertFalse(frequencyCapManager.canShowInterstitial())

        frequencyCapManager.recordCapture()
        assertFalse(frequencyCapManager.canShowInterstitial())

        frequencyCapManager.recordCapture()
        assertFalse(frequencyCapManager.canShowInterstitial())

        frequencyCapManager.recordCapture()
        // Now 3 captures recorded and initial elapsed time > 300s
        assertTrue(frequencyCapManager.canShowInterstitial())
    }

    @Test
    fun `recordAdShown resets capture count and time throttle`() {
        // Record 3 captures to become eligible
        repeat(3) { frequencyCapManager.recordCapture() }
        assertTrue(frequencyCapManager.canShowInterstitial())

        // Record ad display
        frequencyCapManager.recordAdShown()

        // Should now be ineligible immediately because captures = 0 and elapsed time = 0s
        assertFalse(frequencyCapManager.canShowInterstitial())

        // Even with 3 new captures immediately, time condition (< 300s) must prevent it
        repeat(3) { frequencyCapManager.recordCapture() }
        assertFalse(frequencyCapManager.canShowInterstitial())
    }

    @Test
    fun `Pro users are never shown interstitial ads`() = runTest {
        // Make user Pro
        entitlementRepository.updateEntitlements(UserEntitlements.LIFETIME_PRO)
        assertTrue(entitlementRepository.isPro.value)

        // Even with 10 captures, Pro user sees no ads
        repeat(10) { frequencyCapManager.recordCapture() }
        assertFalse(frequencyCapManager.canShowInterstitial())
    }

    @Test
    fun `Global kill switch disables all interstitial ads`() {
        // Disable ads via remote config
        remoteConfigRepository.updateFlags(CustomFeatureFlags(adsEnabled = false))

        repeat(5) { frequencyCapManager.recordCapture() }
        assertFalse(frequencyCapManager.canShowInterstitial())
    }

    @Test
    fun `AdPlacement validatePlacement allows safe screens and forbids viewfinder or shutter`() {
        // Safe placements must not throw
        AdPlacement.validatePlacement(AdPlacement.GALLERY_BOTTOM_BANNER)
        AdPlacement.validatePlacement(AdPlacement.SETTINGS_UPGRADE_BANNER)
        AdPlacement.validatePlacement(AdPlacement.REVIEW_POST_SAVE_INTERSTITIAL)
        AdPlacement.validatePlacement(AdPlacement.AI_TOOLS_PREVIEW_REWARDED)
    }

    @Test
    fun `NoOpAdProvider initializes and dismisses immediately`() {
        val noOp = NoOpAdProvider()
        assertTrue(noOp.isInitialized)
        assertFalse(noOp.isInterstitialReady())

        var dismissed = false
        // Mock activity is not needed as NoOp immediately invokes onDismissed
        noOp.showInterstitialAd(android.app.Activity()) {
            dismissed = true
        }
        assertTrue(dismissed)
    }

    private class FakeTestEntitlementRepository(
        initialEntitlements: UserEntitlements = UserEntitlements.FREE,
    ) : EntitlementRepository {
        private val _entitlements = MutableStateFlow(initialEntitlements)
        override val entitlements: StateFlow<UserEntitlements> = _entitlements.asStateFlow()

        private val _isPro = MutableStateFlow(initialEntitlements.hasFullAccess)
        override val isPro: StateFlow<Boolean> = _isPro.asStateFlow()

        private val _unlockedPacks = MutableStateFlow(initialEntitlements.unlockedPacks)
        override val unlockedPacks: StateFlow<Set<String>> = _unlockedPacks.asStateFlow()

        override val availableProducts: StateFlow<List<OptiProductDetails>> =
            MutableStateFlow(OptiProductDetails.DEFAULT_PRODUCTS)

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
