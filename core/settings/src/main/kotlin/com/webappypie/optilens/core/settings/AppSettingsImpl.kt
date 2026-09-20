package com.webappypie.optilens.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DataStore-backed implementation of [AppSettings].
 *
 * All reads use [Flow] — no blocking calls on any thread.
 * All writes use [DataStore.edit] which is atomic and crash-safe.
 */
@Singleton
class AppSettingsImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : AppSettings {

    // ── Keys ──────────────────────────────────────────────────────────────
    private object Keys {
        val THEME_MODE              = stringPreferencesKey("theme_mode")
        val GRID_ENABLED            = booleanPreferencesKey("grid_enabled")
        val LEVEL_ENABLED           = booleanPreferencesKey("level_enabled")
        val SHUTTER_SOUND           = booleanPreferencesKey("shutter_sound")
        val HAPTIC_FEEDBACK         = booleanPreferencesKey("haptic_feedback")
        val VOLUME_KEY_SHUTTER      = booleanPreferencesKey("volume_key_shutter")
        val COLOR_PROFILE           = stringPreferencesKey("color_profile")
        val KEEP_ORIGINAL           = booleanPreferencesKey("keep_original")
        val LOCATION_TAGGING        = booleanPreferencesKey("location_tagging")
        val MIRROR_FRONT_SELFIE     = booleanPreferencesKey("mirror_front_selfie")
        val PORTRAIT_BLUR_STRENGTH  = floatPreferencesKey("portrait_blur_strength")
        val PORTRAIT_SKIN_SMOOTHING = floatPreferencesKey("portrait_skin_smoothing")
        val IS_PRO                  = booleanPreferencesKey("is_pro")
        val AI_ENHANCE_KEPT_COUNT   = intPreferencesKey("ai_enhance_kept_count")
        val AI_ENHANCE_REVERTED_COUNT = intPreferencesKey("ai_enhance_reverted_count")
        val SUPER_RES_ENABLED       = booleanPreferencesKey("super_res_enabled")
        val SUPER_RES_4X_PRO_ENABLED = booleanPreferencesKey("super_res_4x_pro_enabled")
        val RAW_CAPTURE_ENABLED     = booleanPreferencesKey("raw_capture_enabled")
        val RAW_CAPTURE_FORMAT      = stringPreferencesKey("raw_capture_format")
        val RAW_COMPANION_JPEG      = booleanPreferencesKey("raw_companion_jpeg")
        val FOCUS_PEAKING_ENABLED   = booleanPreferencesKey("focus_peaking_enabled")
        val EXPOSURE_ZEBRA_ENABLED  = booleanPreferencesKey("exposure_zebra_enabled")
        val HISTOGRAM_MODE          = stringPreferencesKey("histogram_mode")
    }

    // ── Theme ─────────────────────────────────────────────────────────────
    override val themeMode: Flow<ThemeMode> = dataStore.data.map { prefs ->
        prefs[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            ?: ThemeMode.SYSTEM
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    // ── Camera ────────────────────────────────────────────────────────────
    override val gridEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.GRID_ENABLED] ?: false }
    override suspend fun setGridEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.GRID_ENABLED] = enabled }
    }

    override val levelEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.LEVEL_ENABLED] ?: false }
    override suspend fun setLevelEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.LEVEL_ENABLED] = enabled }
    }

    override val shutterSoundEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.SHUTTER_SOUND] ?: true }
    override suspend fun setShutterSoundEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.SHUTTER_SOUND] = enabled }
    }

    override val hapticFeedbackEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.HAPTIC_FEEDBACK] ?: true }
    override suspend fun setHapticFeedbackEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.HAPTIC_FEEDBACK] = enabled }
    }

    override val volumeKeyShutterEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.VOLUME_KEY_SHUTTER] ?: false }
    override suspend fun setVolumeKeyShutterEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.VOLUME_KEY_SHUTTER] = enabled }
    }

    // ── Processing ────────────────────────────────────────────────────────
    override val colorProfile: Flow<ColorProfile> = dataStore.data.map { prefs ->
        prefs[Keys.COLOR_PROFILE]?.let { runCatching { ColorProfile.valueOf(it) }.getOrNull() }
            ?: ColorProfile.BALANCED
    }

    override suspend fun setColorProfile(profile: ColorProfile) {
        dataStore.edit { it[Keys.COLOR_PROFILE] = profile.name }
    }

    override val keepOriginalEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.KEEP_ORIGINAL] ?: true }
    override suspend fun setKeepOriginalEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.KEEP_ORIGINAL] = enabled }
    }

    // ── Storage & Privacy ─────────────────────────────────────────────────
    override val locationTaggingEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.LOCATION_TAGGING] ?: false }
    override suspend fun setLocationTaggingEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.LOCATION_TAGGING] = enabled }
    }

    // ── Portrait & Selfie ─────────────────────────────────────────────────
    override val mirrorFrontCameraSelfie: Flow<Boolean> = dataStore.data.map { it[Keys.MIRROR_FRONT_SELFIE] ?: true }
    override suspend fun setMirrorFrontCameraSelfie(enabled: Boolean) {
        dataStore.edit { it[Keys.MIRROR_FRONT_SELFIE] = enabled }
    }

    override val portraitBlurStrength: Flow<Float> = dataStore.data.map { it[Keys.PORTRAIT_BLUR_STRENGTH] ?: 0.50f }
    override suspend fun setPortraitBlurStrength(strength: Float) {
        dataStore.edit { it[Keys.PORTRAIT_BLUR_STRENGTH] = strength }
    }

    override val portraitSkinSmoothingStrength: Flow<Float> = dataStore.data.map { it[Keys.PORTRAIT_SKIN_SMOOTHING] ?: 0.25f }
    override suspend fun setPortraitSkinSmoothingStrength(strength: Float) {
        dataStore.edit { it[Keys.PORTRAIT_SKIN_SMOOTHING] = strength }
    }

    // ── Pro ───────────────────────────────────────────────────────────────
    override val isPro: Flow<Boolean> = dataStore.data.map { it[Keys.IS_PRO] ?: false }
    override suspend fun setIsPro(isPro: Boolean) {
        dataStore.edit { it[Keys.IS_PRO] = isPro }
    }

    // ── AI Enhance & Analytics ────────────────────────────────────────────
    override val aiEnhanceKeptCount: Flow<Int> = dataStore.data.map { it[Keys.AI_ENHANCE_KEPT_COUNT] ?: 0 }
    override val aiEnhanceRevertedCount: Flow<Int> = dataStore.data.map { it[Keys.AI_ENHANCE_REVERTED_COUNT] ?: 0 }
    override val aiEnhanceKeepRate: Flow<Float> = dataStore.data.map { prefs ->
        val kept = prefs[Keys.AI_ENHANCE_KEPT_COUNT] ?: 0
        val reverted = prefs[Keys.AI_ENHANCE_REVERTED_COUNT] ?: 0
        val total = kept + reverted
        if (total > 0) kept.toFloat() / total.toFloat() else 1.0f
    }

    override suspend fun recordAiEnhanceOutcome(kept: Boolean) {
        dataStore.edit { prefs ->
            if (kept) {
                val current = prefs[Keys.AI_ENHANCE_KEPT_COUNT] ?: 0
                prefs[Keys.AI_ENHANCE_KEPT_COUNT] = current + 1
            } else {
                val current = prefs[Keys.AI_ENHANCE_REVERTED_COUNT] ?: 0
                prefs[Keys.AI_ENHANCE_REVERTED_COUNT] = current + 1
            }
        }
    }

    // ── Super Resolution & AI Zoom ────────────────────────────────────────
    override val superResEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.SUPER_RES_ENABLED] ?: true }
    override suspend fun setSuperResEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.SUPER_RES_ENABLED] = enabled }
    }

    override val superRes4xProEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.SUPER_RES_4X_PRO_ENABLED] ?: false }
    override suspend fun setSuperRes4xProEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.SUPER_RES_4X_PRO_ENABLED] = enabled }
    }

    // ── Pro & RAW / DNG ───────────────────────────────────────────────────
    override val rawCaptureEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.RAW_CAPTURE_ENABLED] ?: false }
    override suspend fun setRawCaptureEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.RAW_CAPTURE_ENABLED] = enabled }
    }

    override val rawCaptureFormat: Flow<RawCaptureFormatSetting> = dataStore.data.map { prefs ->
        prefs[Keys.RAW_CAPTURE_FORMAT]?.let { runCatching { RawCaptureFormatSetting.valueOf(it) }.getOrNull() }
            ?: RawCaptureFormatSetting.RAW_SENSOR
    }
    override suspend fun setRawCaptureFormat(format: RawCaptureFormatSetting) {
        dataStore.edit { it[Keys.RAW_CAPTURE_FORMAT] = format.name }
    }

    override val rawCompanionJpegEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.RAW_COMPANION_JPEG] ?: true }
    override suspend fun setRawCompanionJpegEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.RAW_COMPANION_JPEG] = enabled }
    }

    override val focusPeakingEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.FOCUS_PEAKING_ENABLED] ?: false }
    override suspend fun setFocusPeakingEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.FOCUS_PEAKING_ENABLED] = enabled }
    }

    override val exposureZebraEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.EXPOSURE_ZEBRA_ENABLED] ?: false }
    override suspend fun setExposureZebraEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.EXPOSURE_ZEBRA_ENABLED] = enabled }
    }

    override val histogramMode: Flow<HistogramModeSetting> = dataStore.data.map { prefs ->
        prefs[Keys.HISTOGRAM_MODE]?.let { runCatching { HistogramModeSetting.valueOf(it) }.getOrNull() }
            ?: HistogramModeSetting.LUMINANCE
    }
    override suspend fun setHistogramMode(mode: HistogramModeSetting) {
        dataStore.edit { it[Keys.HISTOGRAM_MODE] = mode.name }
    }
}
