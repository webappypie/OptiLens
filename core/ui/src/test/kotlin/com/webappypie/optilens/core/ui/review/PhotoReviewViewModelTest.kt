package com.webappypie.optilens.core.ui.review

import android.graphics.Bitmap
import app.cash.turbine.test
import com.webappypie.optilens.core.imaging.enhance.AiEnhanceConfig
import com.webappypie.optilens.core.common.coroutines.AppDispatchers
import com.webappypie.optilens.core.imaging.enhance.OnDeviceEnhancementEngine
import com.webappypie.optilens.core.logging.NoOpLogger
import com.webappypie.optilens.core.settings.AppSettings
import com.webappypie.optilens.core.settings.ColorProfile
import com.webappypie.optilens.core.settings.HistogramModeSetting
import com.webappypie.optilens.core.settings.RawCaptureFormatSetting
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
    private val testDispatchers = object : AppDispatchers {
        override val default = testDispatcher
        override val io = testDispatcher
        override val main = testDispatcher
        override val mainImmediate = testDispatcher
    }
    private val enhancementEngine = OnDeviceEnhancementEngine(testDispatchers, NoOpLogger())

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeSettings = FakeAppSettings()
        fakeLoader = FakePhotoBitmapLoader()
        viewModel = PhotoReviewViewModel(
            photoBitmapLoader = fakeLoader,
            appSettings = fakeSettings,
            logger = NoOpLogger(),
            enhancementEngine = enhancementEngine,
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

    @Test
    fun `saving twice while a save is running writes one file`() = runTest {
        viewModel.loadPhoto("content://media/test/orig.jpg")
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.setEnhancedStateForTesting(createDummyBitmap())
        viewModel.saveEnhanced()
        viewModel.saveEnhanced()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, fakeLoader.saveCallCount)
        assertEquals(
            com.webappypie.optilens.core.ui.text.UiText(com.webappypie.optilens.core.ui.R.string.review_saved_message),
            viewModel.uiState.value.message,
        )
    }

    @Test
    fun `failed save reports an error and allows retry`() = runTest {
        fakeLoader.savedUriResult = null
        viewModel.loadPhoto("content://media/test/orig.jpg")
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.setEnhancedStateForTesting(createDummyBitmap())
        viewModel.saveEnhanced()
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isSaved)
        assertFalse(viewModel.uiState.value.isSaving)
        assertNotNull(viewModel.uiState.value.errorMessage)

        fakeLoader.savedUriResult = "content://media/external/images/media/9"
        viewModel.saveEnhanced()
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isSaved)
    }

    @Test
    fun `enhanced views are unavailable before enhancement`() {
        viewModel.setViewMode(ReviewViewMode.COMPARE)
        assertEquals(ReviewViewMode.ORIGINAL, viewModel.uiState.value.viewMode)
        viewModel.setEnhancedStateForTesting(null)
        viewModel.setViewMode(ReviewViewMode.ENHANCED)
        assertEquals(ReviewViewMode.ENHANCED, viewModel.uiState.value.viewMode)
    }

    @Test
    fun `cancel clears processing state`() {
        viewModel.cancelEnhance()
        assertFalse(viewModel.uiState.value.isEnhancing)
        assertEquals(0f, viewModel.uiState.value.enhanceProgress, 0f)
    }

    @Test
    fun `retake deletes the captured photo and closes`() = runTest {
        val repo = FakeGalleryRepository()
        val vm = PhotoReviewViewModel(fakeLoader, fakeSettings, NoOpLogger(), enhancementEngine, testDispatcher, repo)
        vm.loadPhoto("content://media/external/images/media/5")
        testDispatcher.scheduler.advanceUntilIdle()
        vm.discardCapturedPhoto()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf("content://media/external/images/media/5"), repo.deleted)
        assertTrue(vm.uiState.value.isDiscarded)
    }

    @Test
    fun `notes describe only what was measured`() {
        val report = com.webappypie.optilens.core.imaging.enhance.EnhancementReport(
            width = 10, height = 10, exposureStops = 0.8f, shadowCompression = 0.8f,
            whiteBalanceGainR = 0.95f, whiteBalanceGainG = 1f, whiteBalanceGainB = 1.04f,
            shadowNoiseSigma = 0.01f, lumaDenoiseRadius = 1, clippedHighlightFraction = 0.05f,
            analysisMs = 1, renderMs = 1,
        )
        val notes = PhotoReviewViewModel.describe(report)
        assertEquals(
            listOf(
                com.webappypie.optilens.core.ui.R.string.note_brightened,
                com.webappypie.optilens.core.ui.R.string.note_shadows,
                com.webappypie.optilens.core.ui.R.string.note_color,
                com.webappypie.optilens.core.ui.R.string.note_noise,
                com.webappypie.optilens.core.ui.R.string.note_clipped,
            ),
            notes.map { it.id },
        )
        assertEquals(listOf<Any>(0.8f), notes[0].args)
        assertEquals(listOf<Any>(5), notes[4].args)
        val untouched = report.copy(
            exposureStops = 0f, shadowCompression = 1f, whiteBalanceGainR = 1f, whiteBalanceGainB = 1f,
            lumaDenoiseRadius = 0, clippedHighlightFraction = 0f,
        )
        assertTrue(PhotoReviewViewModel.describe(untouched).isEmpty())
    }

    private class FakeGalleryRepository : com.webappypie.optilens.core.camera.storage.GalleryRepository {
        val deleted = mutableListOf<String>()
        override fun observeMedia(): Flow<List<com.webappypie.optilens.core.camera.storage.MediaItem>> =
            kotlinx.coroutines.flow.flowOf(emptyList())
        override suspend fun loadMedia(): List<com.webappypie.optilens.core.camera.storage.MediaItem> = emptyList()
        override suspend fun deleteMedia(uriString: String): com.webappypie.optilens.core.common.result.OptiResult<android.app.PendingIntent?> {
            deleted += uriString
            return com.webappypie.optilens.core.common.result.OptiResult.Success(null)
        }
        override suspend fun toggleFavorite(uriString: String) =
            com.webappypie.optilens.core.common.result.OptiResult.Success(false)
        override suspend fun extractExifMetadata(uriString: String): com.webappypie.optilens.core.camera.storage.MediaExifSummary? = null
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

        override val analyticsEnabled: Flow<Boolean> = MutableStateFlow(true)
        override suspend fun setAnalyticsEnabled(enabled: Boolean) {}

        override val crashReportingEnabled: Flow<Boolean> = MutableStateFlow(true)
        override suspend fun setCrashReportingEnabled(enabled: Boolean) {}

        override val successfulCapturesCount: Flow<Int> = MutableStateFlow(0)
        override suspend fun incrementSuccessfulCaptures() {}

        override val lastReviewPromptTimestamp: Flow<Long> = MutableStateFlow(0L)
        override suspend fun setLastReviewPromptTimestamp(timestamp: Long) {}

        override val hasUserReviewed: Flow<Boolean> = MutableStateFlow(false)
        override suspend fun setHasUserReviewed(reviewed: Boolean) {}

        override val firstInstallTimestamp: Flow<Long> = MutableStateFlow(0L)
        override suspend fun setFirstInstallTimestamp(timestamp: Long) {}

        override val recentFailureTimestamp: Flow<Long> = MutableStateFlow(0L)
        override suspend fun setRecentFailureTimestamp(timestamp: Long) {}
    }
}
