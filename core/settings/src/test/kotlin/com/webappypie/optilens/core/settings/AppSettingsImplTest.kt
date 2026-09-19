package com.webappypie.optilens.core.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
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

/**
 * Unit tests for [AppSettingsImpl] with a real DataStore backed by a
 * temporary file — testing actual persistence behavior without mocks.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppSettingsImplTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private fun createSettings(scope: CoroutineScope): AppSettings {
        val uniqueDir = tmpFolder.newFolder(UUID.randomUUID().toString())
        val file = File(uniqueDir, "settings.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { file },
        )
        return AppSettingsImpl(dataStore)
    }

    // ── Theme ─────────────────────────────────────────────────────────────

    @Test
    fun `default theme mode is SYSTEM`() = runTest {
        val settings = createSettings(backgroundScope)
        assertEquals(ThemeMode.SYSTEM, settings.themeMode.first())
    }

    @Test
    fun `setThemeMode DARK persists and emits new value`() = runTest {
        val settings = createSettings(backgroundScope)
        assertEquals(ThemeMode.SYSTEM, settings.themeMode.first())
        settings.setThemeMode(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, settings.themeMode.first())
    }

    @Test
    fun `setThemeMode LIGHT persists and emits new value`() = runTest {
        val settings = createSettings(backgroundScope)
        settings.setThemeMode(ThemeMode.LIGHT)
        assertEquals(ThemeMode.LIGHT, settings.themeMode.first())
    }

    // ── Grid ──────────────────────────────────────────────────────────────

    @Test
    fun `default gridEnabled is false`() = runTest {
        val settings = createSettings(backgroundScope)
        assertFalse(settings.gridEnabled.first())
    }

    @Test
    fun `setGridEnabled true persists correctly`() = runTest {
        val settings = createSettings(backgroundScope)
        assertFalse(settings.gridEnabled.first())
        settings.setGridEnabled(true)
        assertTrue(settings.gridEnabled.first())
    }

    // ── Level indicator ───────────────────────────────────────────────────

    @Test
    fun `default levelEnabled is false and toggles correctly`() = runTest {
        val settings = createSettings(backgroundScope)
        assertFalse(settings.levelEnabled.first())
        settings.setLevelEnabled(true)
        assertTrue(settings.levelEnabled.first())
    }

    // ── Shutter sound ─────────────────────────────────────────────────────

    @Test
    fun `default shutterSound is true and toggles correctly`() = runTest {
        val settings = createSettings(backgroundScope)
        assertTrue(settings.shutterSoundEnabled.first())
        settings.setShutterSoundEnabled(false)
        assertFalse(settings.shutterSoundEnabled.first())
    }

    // ── Haptic feedback ───────────────────────────────────────────────────

    @Test
    fun `default hapticFeedback is true and toggles correctly`() = runTest {
        val settings = createSettings(backgroundScope)
        assertTrue(settings.hapticFeedbackEnabled.first())
        settings.setHapticFeedbackEnabled(false)
        assertFalse(settings.hapticFeedbackEnabled.first())
    }

    // ── Volume key shutter ────────────────────────────────────────────────

    @Test
    fun `default volumeKeyShutter is false and toggles correctly`() = runTest {
        val settings = createSettings(backgroundScope)
        assertFalse(settings.volumeKeyShutterEnabled.first())
        settings.setVolumeKeyShutterEnabled(true)
        assertTrue(settings.volumeKeyShutterEnabled.first())
    }

    // ── Color profile ─────────────────────────────────────────────────────

    @Test
    fun `default colorProfile is BALANCED`() = runTest {
        val settings = createSettings(backgroundScope)
        assertEquals(ColorProfile.BALANCED, settings.colorProfile.first())
    }

    @Test
    fun `setColorProfile VIVID persists correctly`() = runTest {
        val settings = createSettings(backgroundScope)
        settings.setColorProfile(ColorProfile.VIVID)
        assertEquals(ColorProfile.VIVID, settings.colorProfile.first())
    }

    @Test
    fun `setColorProfile NATURAL persists correctly`() = runTest {
        val settings = createSettings(backgroundScope)
        settings.setColorProfile(ColorProfile.NATURAL)
        assertEquals(ColorProfile.NATURAL, settings.colorProfile.first())
    }

    // ── Privacy ───────────────────────────────────────────────────────────

    @Test
    fun `default locationTagging is false (opt-in required)`() = runTest {
        val settings = createSettings(backgroundScope)
        assertFalse(settings.locationTaggingEnabled.first())
        settings.setLocationTaggingEnabled(true)
        assertTrue(settings.locationTaggingEnabled.first())
    }

    // ── Pro ───────────────────────────────────────────────────────────────

    @Test
    fun `default isPro is false and toggles correctly`() = runTest {
        val settings = createSettings(backgroundScope)
        assertFalse(settings.isPro.first())
        settings.setIsPro(true)
        assertTrue(settings.isPro.first())
    }

    // ── Keep original ─────────────────────────────────────────────────────

    @Test
    fun `default keepOriginal is true and toggles correctly`() = runTest {
        val settings = createSettings(backgroundScope)
        assertTrue(settings.keepOriginalEnabled.first())
        settings.setKeepOriginalEnabled(false)
        assertFalse(settings.keepOriginalEnabled.first())
    }

    // ── AI Enhance Analytics ──────────────────────────────────────────────

    @Test
    fun `recordAiEnhanceOutcome kept updates counts and calculates keep rate accurately`() = runTest {
        val settings = createSettings(backgroundScope)
        assertEquals(0, settings.aiEnhanceKeptCount.first())
        assertEquals(0, settings.aiEnhanceRevertedCount.first())
        assertEquals(1.0f, settings.aiEnhanceKeepRate.first(), 0.001f)

        settings.recordAiEnhanceOutcome(kept = true)

        assertEquals(1, settings.aiEnhanceKeptCount.first())
        assertEquals(0, settings.aiEnhanceRevertedCount.first())
        assertEquals(1.0f, settings.aiEnhanceKeepRate.first(), 0.001f)
    }

    @Test
    fun `recordAiEnhanceOutcome reverted updates counts and calculates keep rate accurately`() = runTest {
        val settings = createSettings(backgroundScope)
        settings.recordAiEnhanceOutcome(kept = false)

        assertEquals(0, settings.aiEnhanceKeptCount.first())
        assertEquals(1, settings.aiEnhanceRevertedCount.first())
        assertEquals(0.0f, settings.aiEnhanceKeepRate.first(), 0.001f)
    }

    // ── Super Resolution & AI Zoom ────────────────────────────────────────

    @Test
    fun `default superResEnabled is true and toggles correctly`() = runTest {
        val settings = createSettings(backgroundScope)
        assertTrue(settings.superResEnabled.first())
        settings.setSuperResEnabled(false)
        assertFalse(settings.superResEnabled.first())
    }

    @Test
    fun `default superRes4xProEnabled is false and toggles correctly`() = runTest {
        val settings = createSettings(backgroundScope)
        assertFalse(settings.superRes4xProEnabled.first())
        settings.setSuperRes4xProEnabled(true)
        assertTrue(settings.superRes4xProEnabled.first())
    }

}

