package com.webappypie.optilens.core.settings

import kotlinx.coroutines.flow.Flow

/**
 * App-wide user preferences interface.
 *
 * All settings are exposed as [Flow] so the UI can react to changes
 * in real time. Writes are suspend functions.
 *
 * Default values are documented beside each property.
 */
interface AppSettings {

    // ── Theme ────────────────────────────────────────────────
    /** User's chosen theme. Default: [ThemeMode.SYSTEM]. */
    val themeMode: Flow<ThemeMode>
    suspend fun setThemeMode(mode: ThemeMode)

    // ── Camera ───────────────────────────────────────────────
    /** Whether the grid overlay is shown in the viewfinder. Default: false. */
    val gridEnabled: Flow<Boolean>
    suspend fun setGridEnabled(enabled: Boolean)

    /** Whether the level/horizon indicator is shown. Default: false. */
    val levelEnabled: Flow<Boolean>
    suspend fun setLevelEnabled(enabled: Boolean)

    /** Whether shutter sound is played (subject to device/locale rules). Default: true. */
    val shutterSoundEnabled: Flow<Boolean>
    suspend fun setShutterSoundEnabled(enabled: Boolean)

    /** Whether haptic feedback fires on shutter. Default: true. */
    val hapticFeedbackEnabled: Flow<Boolean>
    suspend fun setHapticFeedbackEnabled(enabled: Boolean)

    /** Whether volume keys act as a shutter trigger. Default: false. */
    val volumeKeyShutterEnabled: Flow<Boolean>
    suspend fun setVolumeKeyShutterEnabled(enabled: Boolean)

    // ── Processing ───────────────────────────────────────────
    /** Active color profile. Default: [ColorProfile.BALANCED]. */
    val colorProfile: Flow<ColorProfile>
    suspend fun setColorProfile(profile: ColorProfile)

    /** Whether the original photo is kept alongside the processed result. Default: true. */
    val keepOriginalEnabled: Flow<Boolean>
    suspend fun setKeepOriginalEnabled(enabled: Boolean)

    // ── Storage & Privacy ────────────────────────────────────
    /** Whether location tagging is enabled. Default: false (opt-in only). */
    val locationTaggingEnabled: Flow<Boolean>
    suspend fun setLocationTaggingEnabled(enabled: Boolean)

    /** Whether anonymous diagnostic analytics are enabled. Default: true. */
    val analyticsEnabled: Flow<Boolean>
    suspend fun setAnalyticsEnabled(enabled: Boolean)

    /** Whether crash and non-fatal error reporting is enabled. Default: true. */
    val crashReportingEnabled: Flow<Boolean>
    suspend fun setCrashReportingEnabled(enabled: Boolean)

    // ── Portrait & Selfie ────────────────────────────────────
    /** Whether front-camera selfies are saved mirrored as previewed. Default: true. */
    val mirrorFrontCameraSelfie: Flow<Boolean>
    suspend fun setMirrorFrontCameraSelfie(enabled: Boolean)

    /** Portrait mode bokeh blur strength factor (0.0 to 1.0). Default: 0.50f (f/2.8). */
    val portraitBlurStrength: Flow<Float>
    suspend fun setPortraitBlurStrength(strength: Float)

    /** Portrait skin smoothing strength (0.0 to 1.0). Default: 0.25f (subtle cleanup). */
    val portraitSkinSmoothingStrength: Flow<Float>
    suspend fun setPortraitSkinSmoothingStrength(strength: Float)

    // ── Pro ──────────────────────────────────────────────────
    /** Whether the user has an active Pro entitlement. Set by billing layer. Default: false. */
    val isPro: Flow<Boolean>
    suspend fun setIsPro(isPro: Boolean)

    // ── AI Enhance & Analytics ───────────────────────────────
    /** Total number of times an AI enhancement was kept / saved. Default: 0. */
    val aiEnhanceKeptCount: Flow<Int>

    /** Total number of times an AI enhancement was reverted. Default: 0. */
    val aiEnhanceRevertedCount: Flow<Int>

    /** Lifetime AI Enhance Keep Rate in [0.0, 1.0]. */
    val aiEnhanceKeepRate: Flow<Float>

    /** Records an AI enhance session outcome (kept or reverted) anonymously without photo content. */
    suspend fun recordAiEnhanceOutcome(kept: Boolean)

    // ── Super Resolution & AI Zoom ───────────────────────────
    /** Whether AI Super Resolution is enabled for digital zoom crops. Default: true. */
    val superResEnabled: Flow<Boolean>
    suspend fun setSuperResEnabled(enabled: Boolean)

    /** Whether 4x Pro Super Resolution is enabled on supported hardware. Default: false. */
    val superRes4xProEnabled: Flow<Boolean>
    suspend fun setSuperRes4xProEnabled(enabled: Boolean)

    // ── Pro & RAW / DNG ──────────────────────────────────────
    /** Whether RAW sensor capture mode is enabled. Default: false. */
    val rawCaptureEnabled: Flow<Boolean>
    suspend fun setRawCaptureEnabled(enabled: Boolean)

    /** Desired RAW output format. Default: [RawCaptureFormatSetting.RAW_SENSOR]. */
    val rawCaptureFormat: Flow<RawCaptureFormatSetting>
    suspend fun setRawCaptureFormat(format: RawCaptureFormatSetting)

    /** Whether companion JPEG is saved alongside RAW capture. Default: true. */
    val rawCompanionJpegEnabled: Flow<Boolean>
    suspend fun setRawCompanionJpegEnabled(enabled: Boolean)

    /** Whether focus peaking edge detection overlay is enabled. Default: false. */
    val focusPeakingEnabled: Flow<Boolean>
    suspend fun setFocusPeakingEnabled(enabled: Boolean)

    /** Whether exposure clipping zebra stripes overlay is enabled. Default: false. */
    val exposureZebraEnabled: Flow<Boolean>
    suspend fun setExposureZebraEnabled(enabled: Boolean)

    /** Live histogram channel display mode. Default: [HistogramModeSetting.LUMINANCE]. */
    val histogramMode: Flow<HistogramModeSetting>
    suspend fun setHistogramMode(mode: HistogramModeSetting)

    // ── Gallery & Favorites ──────────────────────────────────
    /** Set of photo URIs marked as favorites by the user. */
    val favoriteUris: Flow<Set<String>>
    suspend fun setFavorite(uri: String, isFavorite: Boolean)
}

/** User-visible theme preference. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Color rendering profile for processed photos. */
enum class ColorProfile { NATURAL, BALANCED, VIVID }

/** RAW output format setting. */
enum class RawCaptureFormatSetting { RAW_SENSOR, RAW10, RAW12, RAW_PRIVATE }

/** Live histogram mode setting. */
enum class HistogramModeSetting { LUMINANCE, RGB, BOTH }

