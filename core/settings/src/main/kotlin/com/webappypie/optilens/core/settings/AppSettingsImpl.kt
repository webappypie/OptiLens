package com.webappypie.optilens.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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
        val IS_PRO                  = booleanPreferencesKey("is_pro")
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

    // ── Pro ───────────────────────────────────────────────────────────────
    override val isPro: Flow<Boolean> = dataStore.data.map { it[Keys.IS_PRO] ?: false }
    override suspend fun setIsPro(isPro: Boolean) {
        dataStore.edit { it[Keys.IS_PRO] = isPro }
    }
}
