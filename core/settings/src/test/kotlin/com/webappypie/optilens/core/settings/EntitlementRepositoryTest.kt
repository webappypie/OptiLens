package com.webappypie.optilens.core.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.webappypie.optilens.core.common.coroutines.AppDispatchers
import com.webappypie.optilens.core.common.monetization.BillingConnectionState
import com.webappypie.optilens.core.common.monetization.OptiProductDetails
import com.webappypie.optilens.core.common.monetization.OptiProductIds
import com.webappypie.optilens.core.common.monetization.ProductBillingType
import com.webappypie.optilens.core.common.monetization.ProductCategory
import com.webappypie.optilens.core.common.monetization.PurchaseStatus
import com.webappypie.optilens.core.common.monetization.UserEntitlements
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class EntitlementRepositoryTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testDispatchers = object : AppDispatchers {
        override val default: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val main: CoroutineDispatcher = testDispatcher
        override val mainImmediate: CoroutineDispatcher = testDispatcher
    }

    private fun createRepository(
        scope: CoroutineScope,
        dataFile: File? = null,
    ): Pair<EntitlementRepositoryImpl, File> {
        val file = dataFile ?: File(tmpFolder.newFolder(UUID.randomUUID().toString()), "entitlements.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { file },
        )
        return Pair(EntitlementRepositoryImpl(dataStore, testDispatchers, scope), file)
    }

    @Test
    fun `initial state defaults to free tier without pro privileges`() = runTest {
        val (repo, _) = createRepository(backgroundScope)

        val entitlements = repo.entitlements.first()
        assertFalse(entitlements.isPro)
        assertFalse(entitlements.isLifetime)
        assertFalse(entitlements.hasFullAccess)
        assertTrue(entitlements.unlockedPacks.isEmpty())
        assertFalse(repo.isPro.first())
        assertEquals(BillingConnectionState.DISCONNECTED, repo.billingConnectionState.value)
        assertEquals(PurchaseStatus.Idle, repo.purchaseStatus.value)
    }

    @Test
    fun `updateEntitlements with Lifetime Pro grants full access and packs`() = runTest {
        val (repo, _) = createRepository(backgroundScope)

        repo.updateEntitlements(UserEntitlements.LIFETIME_PRO)

        val updated = repo.entitlements.first()
        assertTrue(updated.isPro)
        assertTrue(updated.isLifetime)
        assertTrue(updated.hasFullAccess)
        assertTrue(repo.isPro.first())
        assertTrue(repo.unlockedPacks.first().contains(OptiProductIds.PACK_AI_TOOLS))
        assertTrue(repo.unlockedPacks.first().contains(OptiProductIds.PACK_LOOKS))
    }

    @Test
    fun `updateEntitlements with modular pack unlocks specific feature pack only`() = runTest {
        val (repo, _) = createRepository(backgroundScope)

        val aiPackOnly = UserEntitlements(
            isPro = false,
            isLifetime = false,
            unlockedPacks = setOf(OptiProductIds.PACK_AI_TOOLS),
        )
        repo.updateEntitlements(aiPackOnly)

        val updated = repo.entitlements.first()
        assertFalse(updated.hasFullAccess)
        assertFalse(repo.isPro.first())
        assertTrue(repo.unlockedPacks.first().contains(OptiProductIds.PACK_AI_TOOLS))
        assertFalse(repo.unlockedPacks.first().contains(OptiProductIds.PACK_LOOKS))
    }

    @Test
    fun `entitlements persist across repository instances and survive offline restart`() = runTest {
        val firstScope = CoroutineScope(testDispatchers.io + Job())
        val (firstRepo, storeFile) = createRepository(firstScope)

        val annualPro = UserEntitlements(
            isPro = true,
            isLifetime = false,
            activeSubscriptionId = OptiProductIds.PRO_ANNUAL,
            lastVerifiedTimestampMs = System.currentTimeMillis(),
        )
        firstRepo.updateEntitlements(annualPro)

        // Verify write
        assertTrue(firstRepo.isPro.first())

        // Cancel first repository scope to release DataStore lock
        firstScope.cancel()

        // Recreate repository from same backing file
        val (secondRepo, _) = createRepository(backgroundScope, dataFile = storeFile)

        // Wait for DataStore read to populate
        val loaded = secondRepo.entitlements.first { it.isPro }
        assertTrue(loaded.isPro)
        assertEquals(OptiProductIds.PRO_ANNUAL, loaded.activeSubscriptionId)
        assertTrue(secondRepo.isPro.first { it })
    }

    @Test
    fun `status and connection state updates reflect immediately`() = runTest {
        val (repo, _) = createRepository(backgroundScope)

        repo.setPurchaseStatus(PurchaseStatus.Pending(OptiProductIds.PRO_LIFETIME))
        assertTrue(repo.purchaseStatus.value is PurchaseStatus.Pending)

        repo.setPurchaseStatus(PurchaseStatus.Error("user_cancelled", "Payment cancelled by user"))
        val errorStatus = repo.purchaseStatus.value as PurchaseStatus.Error
        assertEquals("Payment cancelled by user", errorStatus.displayMessage)

        repo.updateBillingConnectionState(BillingConnectionState.CONNECTED)
        assertEquals(BillingConnectionState.CONNECTED, repo.billingConnectionState.value)

        val customProducts = listOf(
            OptiProductDetails(
                id = OptiProductIds.PRO_LIFETIME,
                category = ProductCategory.PRO_TIER,
                billingType = ProductBillingType.ONE_TIME,
                title = "Lifetime Special",
                description = "Lifetime access",
                formattedPrice = "$49.99",
                hasFreeTrial = false,
            )
        )
        repo.updateAvailableProducts(customProducts)
        assertEquals(1, repo.availableProducts.value.size)
        assertEquals("Lifetime Special", repo.availableProducts.value[0].title)
    }
}
