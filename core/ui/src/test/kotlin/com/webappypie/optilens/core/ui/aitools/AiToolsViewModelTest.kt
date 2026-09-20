package com.webappypie.optilens.core.ui.aitools

import android.graphics.Bitmap
import app.cash.turbine.test
import com.webappypie.optilens.core.common.monetization.BillingConnectionState
import com.webappypie.optilens.core.common.monetization.EntitlementRepository
import com.webappypie.optilens.core.common.monetization.OptiProductDetails
import com.webappypie.optilens.core.common.monetization.OptiProductIds
import com.webappypie.optilens.core.common.monetization.PurchaseStatus
import com.webappypie.optilens.core.common.monetization.UserEntitlements
import com.webappypie.optilens.core.common.result.OptiResult
import com.webappypie.optilens.core.imaging.ai.AiToolType
import com.webappypie.optilens.core.imaging.ai.AiToolsCoordinator
import com.webappypie.optilens.core.imaging.ai.blur.BlurClassifier
import com.webappypie.optilens.core.imaging.ai.blur.DeblurEngine
import com.webappypie.optilens.core.imaging.ai.inpainting.InpaintingEngine
import com.webappypie.optilens.core.imaging.ai.reflection.ReflectionReductionEngine
import com.webappypie.optilens.core.imaging.ai.restoration.PhotoRestorationEngine
import com.webappypie.optilens.core.imaging.ai.tier.AiDeviceTierGate
import com.webappypie.optilens.core.imaging.ai.tiling.TileProcessingCoordinator
import com.webappypie.optilens.core.imaging.ai.upscale.AiUpscaleEngine
import com.webappypie.optilens.core.logging.NoOpLogger
import com.webappypie.optilens.core.settings.AppSettings
import com.webappypie.optilens.core.settings.ColorProfile
import com.webappypie.optilens.core.settings.HistogramModeSetting
import com.webappypie.optilens.core.settings.RawCaptureFormatSetting
import com.webappypie.optilens.core.settings.ThemeMode
import com.webappypie.optilens.core.ui.review.PhotoBitmapLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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
class AiToolsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeLoader: FakePhotoBitmapLoader
    private lateinit var fakeSettings: FakeAppSettings
    private lateinit var fakeEntitlements: FakeEntitlementRepository
    private lateinit var coordinator: AiToolsCoordinator
    private lateinit var viewModel: AiToolsViewModel

    companion object {
        fun createDummyBitmap(): Bitmap {
            val field = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe")
            field.isAccessible = true
            val unsafe = field.get(null) as sun.misc.Unsafe
            return unsafe.allocateInstance(Bitmap::class.java) as Bitmap
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeLoader = FakePhotoBitmapLoader()
        fakeSettings = FakeAppSettings()
        fakeEntitlements = FakeEntitlementRepository()

        val blurClassifier = BlurClassifier()
        val deblurEngine = DeblurEngine(blurClassifier)
        val inpaintingEngine = InpaintingEngine()
        val reflectionEngine = ReflectionReductionEngine()
        val upscaleEngine = AiUpscaleEngine()
        val restorationEngine = PhotoRestorationEngine()
        val tierGate = AiDeviceTierGate()
        val tileCoordinator = TileProcessingCoordinator(tierGate)

        coordinator = AiToolsCoordinator(
            blurClassifier = blurClassifier,
            deblurEngine = deblurEngine,
            inpaintingEngine = inpaintingEngine,
            reflectionEngine = reflectionEngine,
            upscaleEngine = upscaleEngine,
            restorationEngine = restorationEngine,
            tileCoordinator = tileCoordinator,
        )

        viewModel = AiToolsViewModel(
            photoBitmapLoader = fakeLoader,
            aiToolsCoordinator = coordinator,
            appSettings = fakeSettings,
            entitlementRepository = fakeEntitlements,
            logger = NoOpLogger(),
            ioDispatcher = testDispatcher,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has default deblur tool and empty history stacks`() = runTest {
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(AiToolType.DEBLUR, state.selectedTool)
            assertFalse(state.canUndo)
            assertFalse(state.canRedo)
            assertFalse(state.isProcessing)
            assertEquals(0.50f, state.splitFraction, 0.001f)
            assertFalse(state.isHoldingToCompare)
            assertTrue(state.isGated)
        }
    }

    @Test
    fun `selecting inpainting or restoration activates mandatory ethical disclosure`() = runTest {
        viewModel.selectTool(AiToolType.INPAINTING)
        val inpaintState = viewModel.uiState.value
        assertEquals(AiToolType.INPAINTING, inpaintState.selectedTool)
        assertNotNull(inpaintState.disclosureMessage)
        assertTrue(inpaintState.disclosureMessage!!.contains("not guaranteed historical truth", ignoreCase = true))

        viewModel.selectTool(AiToolType.RESTORATION)
        val restoreState = viewModel.uiState.value
        assertEquals(AiToolType.RESTORATION, restoreState.selectedTool)
        assertNotNull(restoreState.disclosureMessage)
        assertTrue(restoreState.disclosureMessage!!.contains("not guaranteed historical truth", ignoreCase = true))

        viewModel.selectTool(AiToolType.DEBLUR)
        val deblurState = viewModel.uiState.value
        assertNull(deblurState.disclosureMessage)
    }

    @Test
    fun `pro gated tools reflect entitlement status correctly`() = runTest {
        viewModel.selectTool(AiToolType.RESTORATION)
        assertTrue("Restoration should be gated by default for non-pro", viewModel.uiState.value.isGated)

        // Grant AI pack
        fakeEntitlements.updateEntitlements(
            UserEntitlements(unlockedPacks = setOf(OptiProductIds.PACK_AI_TOOLS))
        )
        advanceUntilIdle()
        assertFalse("AI pack unlocks restoration", viewModel.uiState.value.isGated)
    }

    @Test
    fun `parameter adjustments are clamped within valid bounds`() {
        viewModel.updateDeblurStrength(1.5f)
        assertEquals(1.0f, viewModel.uiState.value.deblurStrength, 0.001f)

        viewModel.updateDeblurStrength(-0.5f)
        assertEquals(0.1f, viewModel.uiState.value.deblurStrength, 0.001f)

        viewModel.updateBrushSize(150f)
        assertEquals(100f, viewModel.uiState.value.inpaintingBrushSize, 0.001f)

        viewModel.updateBrushSize(2f)
        assertEquals(10f, viewModel.uiState.value.inpaintingBrushSize, 0.001f)

        viewModel.updateReflectionStrength(0.85f)
        assertEquals(0.85f, viewModel.uiState.value.reflectionStrength, 0.001f)

        viewModel.updateUpscaleFactor(3)
        assertEquals(2, viewModel.uiState.value.upscaleFactor)

        viewModel.updateUpscaleFactor(4)
        assertEquals(4, viewModel.uiState.value.upscaleFactor)

        viewModel.updateRestorationStrength(0.9f)
        assertEquals(0.9f, viewModel.uiState.value.restorationStrength, 0.001f)
    }

    @Test
    fun `split slider and hold-to-compare mutate preview states`() {
        viewModel.setSplitFraction(0.75f)
        assertEquals(0.75f, viewModel.uiState.value.splitFraction, 0.001f)

        viewModel.setHoldingToCompare(true)
        assertTrue(viewModel.uiState.value.isHoldingToCompare)

        viewModel.setHoldingToCompare(false)
        assertFalse(viewModel.uiState.value.isHoldingToCompare)
    }

    @Test
    fun `loadPhoto populates originalBitmap and currentBitmap`() = runTest {
        val testUri = "content://media/external/images/media/photo_456"
        val dummy = createDummyBitmap()
        fakeLoader.loadedBitmap = dummy

        viewModel.loadPhoto(testUri)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(testUri, state.photoUri)
        assertEquals(dummy, state.originalBitmap)
        assertEquals(dummy, state.currentBitmap)
    }

    @Test
    fun `saveCopy invokes photoBitmapLoader with current keepOriginal setting`() = runTest {
        val testUri = "content://media/external/images/media/photo_456"
        fakeLoader.loadedBitmap = createDummyBitmap()
        fakeSettings.setKeepOriginalEnabled(true)

        viewModel.loadPhoto(testUri)
        advanceUntilIdle()

        viewModel.saveCopy()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isSaved)
        assertEquals("content://media/external/images/media/ai_enhanced_123", state.savedUri)
        assertEquals(1, fakeLoader.saveCallCount)
    }

    @Test
    fun `revertAll restores originalBitmap and clears undo redo history`() = runTest {
        val testUri = "content://media/external/images/media/photo_456"
        val original = createDummyBitmap()
        fakeLoader.loadedBitmap = original

        viewModel.loadPhoto(testUri)
        advanceUntilIdle()

        viewModel.revertAll()

        val state = viewModel.uiState.value
        assertEquals(original, state.currentBitmap)
        assertFalse(state.canUndo)
        assertFalse(state.canRedo)
        assertFalse(state.isSaved)
    }

    // ==========================================
    // Test Fakes
    // ==========================================

    private class FakePhotoBitmapLoader : PhotoBitmapLoader {
        var loadedBitmap: Bitmap? = createDummyBitmap()
        var savedUriResult: String? = "content://media/external/images/media/ai_enhanced_123"
        var saveCallCount: Int = 0

        override suspend fun loadBitmap(uriString: String): Bitmap? = loadedBitmap

        override suspend fun saveEnhancedBitmap(
            bitmap: Bitmap,
            originalUri: String,
            keepOriginal: Boolean,
        ): String? {
            saveCallCount++
            return savedUriResult
        }
    }

    private class FakeEntitlementRepository : EntitlementRepository {
        private val _entitlements = MutableStateFlow(UserEntitlements())
        override val entitlements: StateFlow<UserEntitlements> = _entitlements.asStateFlow()

        private val _isPro = MutableStateFlow(false)
        override val isPro: StateFlow<Boolean> = _isPro.asStateFlow()

        private val _unlockedPacks = MutableStateFlow(emptySet<String>())
        override val unlockedPacks: StateFlow<Set<String>> = _unlockedPacks.asStateFlow()

        override val availableProducts: StateFlow<List<OptiProductDetails>> =
            MutableStateFlow(OptiProductDetails.DEFAULT_PRODUCTS)

        override val purchaseStatus: StateFlow<PurchaseStatus> =
            MutableStateFlow(PurchaseStatus.Idle)

        override val billingConnectionState: StateFlow<BillingConnectionState> =
            MutableStateFlow(BillingConnectionState.CONNECTED)

        override suspend fun refreshPurchases(): OptiResult<UserEntitlements> =
            OptiResult.Success(_entitlements.value)

        override suspend fun restorePurchases(): OptiResult<UserEntitlements> =
            OptiResult.Success(_entitlements.value)

        override fun setPurchaseStatus(status: PurchaseStatus) {}

        override suspend fun updateEntitlements(entitlements: UserEntitlements) {
            _entitlements.value = entitlements
            _isPro.value = entitlements.hasFullAccess
            _unlockedPacks.value = entitlements.unlockedPacks
        }
    }

    private class FakeAppSettings : AppSettings {
        override val themeMode: Flow<ThemeMode> = MutableStateFlow(ThemeMode.SYSTEM)
        override suspend fun setThemeMode(mode: ThemeMode) {}

        override val gridEnabled: Flow<Boolean> = MutableStateFlow(false)
        override suspend fun setGridEnabled(enabled: Boolean) {}

        override val levelEnabled: Flow<Boolean> = MutableStateFlow(false)
        override suspend fun setLevelEnabled(enabled: Boolean) {}

        override val shutterSoundEnabled: Flow<Boolean> = MutableStateFlow(true)
        override suspend fun setShutterSoundEnabled(enabled: Boolean) {}

        override val hapticFeedbackEnabled: Flow<Boolean> = MutableStateFlow(true)
        override suspend fun setHapticFeedbackEnabled(enabled: Boolean) {}

        override val volumeKeyShutterEnabled: Flow<Boolean> = MutableStateFlow(false)
        override suspend fun setVolumeKeyShutterEnabled(enabled: Boolean) {}

        override val colorProfile: Flow<ColorProfile> = MutableStateFlow(ColorProfile.BALANCED)
        override suspend fun setColorProfile(profile: ColorProfile) {}

        private val _keepOriginal = MutableStateFlow(true)
        override val keepOriginalEnabled: Flow<Boolean> = _keepOriginal.asStateFlow()
        override suspend fun setKeepOriginalEnabled(enabled: Boolean) { _keepOriginal.value = enabled }

        override val locationTaggingEnabled: Flow<Boolean> = MutableStateFlow(false)
        override suspend fun setLocationTaggingEnabled(enabled: Boolean) {}

        override val mirrorFrontCameraSelfie: Flow<Boolean> = MutableStateFlow(true)
        override suspend fun setMirrorFrontCameraSelfie(enabled: Boolean) {}

        override val portraitBlurStrength: Flow<Float> = MutableStateFlow(0.50f)
        override suspend fun setPortraitBlurStrength(strength: Float) {}

        override val portraitSkinSmoothingStrength: Flow<Float> = MutableStateFlow(0.25f)
        override suspend fun setPortraitSkinSmoothingStrength(strength: Float) {}

        override val isPro: Flow<Boolean> = MutableStateFlow(false)
        override suspend fun setIsPro(isPro: Boolean) {}

        override val aiEnhanceKeptCount: Flow<Int> = MutableStateFlow(0)
        override val aiEnhanceRevertedCount: Flow<Int> = MutableStateFlow(0)
        override val aiEnhanceKeepRate: Flow<Float> = MutableStateFlow(1.0f)
        override suspend fun recordAiEnhanceOutcome(kept: Boolean) {}

        override val superResEnabled: Flow<Boolean> = MutableStateFlow(true)
        override suspend fun setSuperResEnabled(enabled: Boolean) {}

        override val superRes4xProEnabled: Flow<Boolean> = MutableStateFlow(false)
        override suspend fun setSuperRes4xProEnabled(enabled: Boolean) {}

        override val rawCaptureEnabled: Flow<Boolean> = MutableStateFlow(false)
        override suspend fun setRawCaptureEnabled(enabled: Boolean) {}

        override val rawCaptureFormat: Flow<RawCaptureFormatSetting> = MutableStateFlow(RawCaptureFormatSetting.RAW_SENSOR)
        override suspend fun setRawCaptureFormat(format: RawCaptureFormatSetting) {}

        override val rawCompanionJpegEnabled: Flow<Boolean> = MutableStateFlow(true)
        override suspend fun setRawCompanionJpegEnabled(enabled: Boolean) {}

        override val focusPeakingEnabled: Flow<Boolean> = MutableStateFlow(false)
        override suspend fun setFocusPeakingEnabled(enabled: Boolean) {}

        override val exposureZebraEnabled: Flow<Boolean> = MutableStateFlow(false)
        override suspend fun setExposureZebraEnabled(enabled: Boolean) {}

        override val histogramMode: Flow<HistogramModeSetting> = MutableStateFlow(HistogramModeSetting.LUMINANCE)
        override suspend fun setHistogramMode(mode: HistogramModeSetting) {}

        override val favoriteUris: Flow<Set<String>> = MutableStateFlow(emptySet())
        override suspend fun setFavorite(uri: String, isFavorite: Boolean) {}
    }
}
