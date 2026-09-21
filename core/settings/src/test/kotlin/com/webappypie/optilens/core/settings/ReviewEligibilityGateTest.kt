package com.webappypie.optilens.core.settings

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewEligibilityGateTest {

    private class FakeAppSettings : AppSettings {
        val _hasUserReviewed = MutableStateFlow(false)
        override val hasUserReviewed: Flow<Boolean> = _hasUserReviewed
        override suspend fun setHasUserReviewed(reviewed: Boolean) { _hasUserReviewed.value = reviewed }

        val _successfulCapturesCount = MutableStateFlow(0)
        override val successfulCapturesCount: Flow<Int> = _successfulCapturesCount
        override suspend fun incrementSuccessfulCaptures() { _successfulCapturesCount.value++ }

        val _aiEnhanceKeptCount = MutableStateFlow(0)
        override val aiEnhanceKeptCount: Flow<Int> = _aiEnhanceKeptCount
        val _aiEnhanceRevertedCount = MutableStateFlow(0)
        override val aiEnhanceRevertedCount: Flow<Int> = _aiEnhanceRevertedCount
        override val aiEnhanceKeepRate: Flow<Float> = MutableStateFlow(1.0f)
        override suspend fun recordAiEnhanceOutcome(kept: Boolean) {
            if (kept) _aiEnhanceKeptCount.value++ else _aiEnhanceRevertedCount.value++
        }

        val _firstInstallTimestamp = MutableStateFlow(0L)
        override val firstInstallTimestamp: Flow<Long> = _firstInstallTimestamp
        override suspend fun setFirstInstallTimestamp(timestamp: Long) { _firstInstallTimestamp.value = timestamp }

        val _lastReviewPromptTimestamp = MutableStateFlow(0L)
        override val lastReviewPromptTimestamp: Flow<Long> = _lastReviewPromptTimestamp
        override suspend fun setLastReviewPromptTimestamp(timestamp: Long) { _lastReviewPromptTimestamp.value = timestamp }

        val _recentFailureTimestamp = MutableStateFlow(0L)
        override val recentFailureTimestamp: Flow<Long> = _recentFailureTimestamp
        override suspend fun setRecentFailureTimestamp(timestamp: Long) { _recentFailureTimestamp.value = timestamp }

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
        override val keepOriginalEnabled: Flow<Boolean> = MutableStateFlow(true)
        override suspend fun setKeepOriginalEnabled(enabled: Boolean) {}
        override val locationTaggingEnabled: Flow<Boolean> = MutableStateFlow(false)
        override suspend fun setLocationTaggingEnabled(enabled: Boolean) {}
        override val analyticsEnabled: Flow<Boolean> = MutableStateFlow(true)
        override suspend fun setAnalyticsEnabled(enabled: Boolean) {}
        override val crashReportingEnabled: Flow<Boolean> = MutableStateFlow(true)
        override suspend fun setCrashReportingEnabled(enabled: Boolean) {}
        override val mirrorFrontCameraSelfie: Flow<Boolean> = MutableStateFlow(true)
        override suspend fun setMirrorFrontCameraSelfie(enabled: Boolean) {}
        override val portraitBlurStrength: Flow<Float> = MutableStateFlow(0.5f)
        override suspend fun setPortraitBlurStrength(strength: Float) {}
        override val portraitSkinSmoothingStrength: Flow<Float> = MutableStateFlow(0.25f)
        override suspend fun setPortraitSkinSmoothingStrength(strength: Float) {}
        override val isPro: Flow<Boolean> = MutableStateFlow(false)
        override suspend fun setIsPro(isPro: Boolean) {}
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

    @Test
    fun `brand new user is not eligible for in-app review`() = runTest {
        val settings = FakeAppSettings()
        val gate = ReviewEligibilityGate(settings)

        assertFalse(gate.isEligible().first())
    }

    @Test
    fun `user with positive captures but fresh install is not eligible`() = runTest {
        val settings = FakeAppSettings()
        val gate = ReviewEligibilityGate(settings)
        val now = 1_000_000_000_000L

        settings.setFirstInstallTimestamp(now - 1_000L) // 1 second ago
        repeat(5) { settings.incrementSuccessfulCaptures() }

        assertFalse(gate.isEligible(nowMs = now).first())
    }

    @Test
    fun `user with 5 captures and 3 days install age is eligible`() = runTest {
        val settings = FakeAppSettings()
        val gate = ReviewEligibilityGate(settings)
        val now = 1_000_000_000_000L

        settings.setFirstInstallTimestamp(now - (3L * 24 * 60 * 60 * 1000)) // 3 days ago
        repeat(5) { settings.incrementSuccessfulCaptures() }

        assertTrue(gate.isEligible(nowMs = now).first())
    }

    @Test
    fun `user with 3 ai keeps is eligible even with fewer captures`() = runTest {
        val settings = FakeAppSettings()
        val gate = ReviewEligibilityGate(settings)
        val now = 1_000_000_000_000L

        settings.setFirstInstallTimestamp(now - (3L * 24 * 60 * 60 * 1000))
        repeat(3) { settings.recordAiEnhanceOutcome(kept = true) }

        assertTrue(gate.isEligible(nowMs = now).first())
    }

    @Test
    fun `recent failure suppresses review eligibility for 1 hour`() = runTest {
        val settings = FakeAppSettings()
        val gate = ReviewEligibilityGate(settings)
        val now = 1_000_000_000_000L

        settings.setFirstInstallTimestamp(now - (3L * 24 * 60 * 60 * 1000))
        repeat(5) { settings.incrementSuccessfulCaptures() }
        gate.recordProcessingFailure(nowMs = now - (10L * 60 * 1000)) // failure 10 mins ago

        assertFalse(gate.isEligible(nowMs = now).first())

        // After 61 minutes, eligibility is restored
        assertTrue(gate.isEligible(nowMs = now + (51L * 60 * 1000)).first())
    }

    @Test
    fun `review prompt enforces 30 day cooldown`() = runTest {
        val settings = FakeAppSettings()
        val gate = ReviewEligibilityGate(settings)
        val now = 1_000_000_000_000L

        settings.setFirstInstallTimestamp(now - (40L * 24 * 60 * 60 * 1000))
        repeat(5) { settings.incrementSuccessfulCaptures() }
        gate.markReviewPromptShown(nowMs = now - (5L * 24 * 60 * 60 * 1000)) // shown 5 days ago

        assertFalse(gate.isEligible(nowMs = now).first())

        // 31 days after prompt
        assertTrue(gate.isEligible(nowMs = now + (26L * 24 * 60 * 60 * 1000)).first())
    }

    @Test
    fun `user who already reviewed is never eligible`() = runTest {
        val settings = FakeAppSettings()
        val gate = ReviewEligibilityGate(settings)
        val now = 1_000_000_000_000L

        settings.setFirstInstallTimestamp(now - (40L * 24 * 60 * 60 * 1000))
        repeat(10) { settings.incrementSuccessfulCaptures() }
        gate.markUserReviewed()

        assertFalse(gate.isEligible(nowMs = now).first())
    }
}
