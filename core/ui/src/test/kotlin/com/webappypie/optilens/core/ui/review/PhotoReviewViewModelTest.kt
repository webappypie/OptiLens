package com.webappypie.optilens.core.ui.review

import android.graphics.Bitmap
import app.cash.turbine.test
import com.webappypie.optilens.core.imaging.enhance.AiEnhanceConfig
import com.webappypie.optilens.core.imaging.enhance.NativeAiEnhanceEngine
import com.webappypie.optilens.core.logging.NoOpLogger
import com.webappypie.optilens.core.settings.AppSettings
import com.webappypie.optilens.core.settings.ColorProfile
import com.webappypie.optilens.core.settings.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
class PhotoReviewViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeSettings: FakeAppSettings
    private lateinit var fakeLoader: FakePhotoBitmapLoader
    private lateinit var viewModel: PhotoReviewViewModel
    private val fakeAiEngine = NativeAiEnhanceEngine(useNativeIfAvailable = false)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeSettings = FakeAppSettings()
        fakeLoader = FakePhotoBitmapLoader()
        viewModel = PhotoReviewViewModel(
            photoBitmapLoader = fakeLoader,
            appSettings = fakeSettings,
            logger = NoOpLogger(),
            aiEnhanceEngine = fakeAiEngine,
            ioDispatcher = testDispatcher,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `splitFraction updates accurately`() {
        assertEquals(0.50f, viewModel.uiState.value.splitFraction, 0.001f)
        viewModel.setSplitFraction(0.75f)
        assertEquals(0.75f, viewModel.uiState.value.splitFraction, 0.001f)
    }

    @Test
    fun `holdingToCompare updates state correctly`() {
        assertFalse(viewModel.uiState.value.isHoldingToCompare)
        viewModel.setHoldingToCompare(true)
        assertTrue(viewModel.uiState.value.isHoldingToCompare)
        viewModel.setHoldingToCompare(false)
        assertFalse(viewModel.uiState.value.isHoldingToCompare)
    }

    @Test
    fun `loadPhoto loads bitmap into state`() = runTest {
        val testBitmap = createDummyBitmap()
        fakeLoader.loadedBitmap = testBitmap

        viewModel.loadPhoto("content://media/external/images/media/photo_1")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("content://media/external/images/media/photo_1", viewModel.uiState.value.photoUri)
        assertNotNull(viewModel.uiState.value.originalBitmap)
    }

    @Test
    fun `revert when not enhanced is a no-op`() = runTest {
        viewModel.revert()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isEnhanced)
        assertNull(viewModel.uiState.value.enhancedBitmap)
        assertTrue(fakeSettings.keptOutcomes.isEmpty())
    }

    @Test
    fun `revert resets enhanced state and records outcome anonymously`() = runTest {
        val dummy = createDummyBitmap()
        viewModel.setEnhancedStateForTesting(dummy)
        assertTrue(viewModel.uiState.value.isEnhanced)

        viewModel.revert()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isEnhanced)
        assertNull(viewModel.uiState.value.enhancedBitmap)
        assertTrue(fakeSettings.keptOutcomes.contains(false))
    }

    @Test
    fun `saveEnhanced when enhanced delegates to loader and records keep outcome`() = runTest {
        val dummy = createDummyBitmap()
        fakeLoader.loadedBitmap = dummy
        viewModel.loadPhoto("content://media/test/orig.jpg")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.setEnhancedStateForTesting(dummy)
        viewModel.saveEnhanced()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, fakeLoader.saveCallCount)
        assertTrue(viewModel.uiState.value.isSaved)
        assertTrue(fakeSettings.keptOutcomes.contains(true))
    }

    companion object {
        fun createDummyBitmap(): Bitmap {
            val field = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe")
            field.isAccessible = true
            val unsafe = field.get(null) as sun.misc.Unsafe
            return unsafe.allocateInstance(Bitmap::class.java) as Bitmap
        }
    }

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

        val keptOutcomes = mutableListOf<Boolean>()
        private val _keptCount = MutableStateFlow(0)
        private val _revertedCount = MutableStateFlow(0)
        override val aiEnhanceKeptCount: Flow<Int> = _keptCount.asStateFlow()
        override val aiEnhanceRevertedCount: Flow<Int> = _revertedCount.asStateFlow()
        override val aiEnhanceKeepRate: Flow<Float> = MutableStateFlow(1.0f)
        override suspend fun recordAiEnhanceOutcome(kept: Boolean) {
            keptOutcomes.add(kept)
            if (kept) _keptCount.value += 1 else _revertedCount.value += 1
        }

        private val _superResEnabled = MutableStateFlow(true)
        override val superResEnabled: Flow<Boolean> = _superResEnabled.asStateFlow()
        override suspend fun setSuperResEnabled(enabled: Boolean) { _superResEnabled.value = enabled }

        private val _superRes4xProEnabled = MutableStateFlow(false)
        override val superRes4xProEnabled: Flow<Boolean> = _superRes4xProEnabled.asStateFlow()
        override suspend fun setSuperRes4xProEnabled(enabled: Boolean) { _superRes4xProEnabled.value = enabled }
    }
}
