package com.webappypie.optilens.core.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.webappypie.optilens.core.settings.AppSettings
import com.webappypie.optilens.core.settings.ColorProfile
import com.webappypie.optilens.core.settings.ThemeMode
import com.webappypie.optilens.core.ui.components.OptiSegmentedControl
import com.webappypie.optilens.core.ui.components.OptiSettingsRow
import com.webappypie.optilens.core.ui.components.OptiSettingsToggleRow
import com.webappypie.optilens.core.ui.components.OptiTopBar
import com.webappypie.optilens.core.ui.theme.OptiLensTheme
import kotlinx.coroutines.launch

/**
 * Settings Screen for OptiLens.
 *
 * Implements:
 * - Theme selection (System, Light, Dark) with persistent DataStore storage.
 * - Viewfinder grid, level, and shutter sound preferences.
 * - Color profile selection (Natural, Balanced, Vivid).
 * - Location tagging and photo storage options.
 * - Pro membership status and upgrade navigation.
 */
@Composable
fun SettingsScreen(
    appSettings: AppSettings,
    onNavigateBack: () -> Unit,
    onNavigateToPro: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()

    val currentTheme by appSettings.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
    val gridEnabled by appSettings.gridEnabled.collectAsStateWithLifecycle(initialValue = false)
    val levelEnabled by appSettings.levelEnabled.collectAsStateWithLifecycle(initialValue = false)
    val shutterSoundEnabled by appSettings.shutterSoundEnabled.collectAsStateWithLifecycle(initialValue = true)
    val hapticFeedbackEnabled by appSettings.hapticFeedbackEnabled.collectAsStateWithLifecycle(initialValue = true)
    val volumeKeyShutterEnabled by appSettings.volumeKeyShutterEnabled.collectAsStateWithLifecycle(initialValue = false)
    val colorProfile by appSettings.colorProfile.collectAsStateWithLifecycle(initialValue = ColorProfile.BALANCED)
    val keepOriginalEnabled by appSettings.keepOriginalEnabled.collectAsStateWithLifecycle(initialValue = true)
    val locationTaggingEnabled by appSettings.locationTaggingEnabled.collectAsStateWithLifecycle(initialValue = false)
    val isPro by appSettings.isPro.collectAsStateWithLifecycle(initialValue = false)

    Scaffold(
        topBar = {
            OptiTopBar(
                title = "Settings",
                onBackClick = onNavigateBack,
            )
        },
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            // ── Pro Upgrade Banner ─────────────────────────────────────────
            if (!isPro) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = OptiLensTheme.spacing.l, vertical = OptiLensTheme.spacing.m)
                        .clickable(onClick = onNavigateToPro),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(OptiLensTheme.spacing.l),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Star,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(OptiLensTheme.iconSizes.large),
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = OptiLensTheme.spacing.m),
                        ) {
                            Text(
                                text = "Unlock OptiLens Pro",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            )
                            Text(
                                text = "RAW DNG capture, unlimited AI tools & studio filters",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Upgrade to Pro",
                        )
                    }
                }
            }

            // ── Appearance Section ─────────────────────────────────────────
            SettingsSectionHeader(title = "Appearance")

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = OptiLensTheme.spacing.l, vertical = OptiLensTheme.spacing.s),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = OptiLensTheme.spacing.s),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DarkMode,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(OptiLensTheme.iconSizes.standard),
                    )
                    Text(
                        text = "App Theme",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.padding(start = OptiLensTheme.spacing.l),
                    )
                }

                OptiSegmentedControl(
                    items = listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK),
                    selectedIndex = when (currentTheme) {
                        ThemeMode.SYSTEM -> 0
                        ThemeMode.LIGHT  -> 1
                        ThemeMode.DARK   -> 2
                    },
                    onItemSelected = { index ->
                        val selected = when (index) {
                            0 -> ThemeMode.SYSTEM
                            1 -> ThemeMode.LIGHT
                            else -> ThemeMode.DARK
                        }
                        coroutineScope.launch { appSettings.setThemeMode(selected) }
                    },
                    itemLabel = { mode ->
                        when (mode) {
                            ThemeMode.SYSTEM -> "System"
                            ThemeMode.LIGHT  -> "Light"
                            ThemeMode.DARK   -> "Dark"
                        }
                    },
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = OptiLensTheme.spacing.s),
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            // ── Camera Controls Section ────────────────────────────────────
            SettingsSectionHeader(title = "Camera & Viewfinder")

            OptiSettingsToggleRow(
                title = "Rule of Thirds Grid",
                subtitle = "Display alignment grid on the viewfinder",
                leadingIcon = Icons.Outlined.GridOn,
                checked = gridEnabled,
                onCheckedChange = { checked ->
                    coroutineScope.launch { appSettings.setGridEnabled(checked) }
                },
            )

            OptiSettingsToggleRow(
                title = "Horizon Level Indicator",
                subtitle = "Assists in leveling horizontal and vertical shots",
                checked = levelEnabled,
                onCheckedChange = { checked ->
                    coroutineScope.launch { appSettings.setLevelEnabled(checked) }
                },
            )

            OptiSettingsToggleRow(
                title = "Shutter Sound",
                subtitle = "Audio feedback when capturing photos",
                leadingIcon = Icons.Default.VolumeUp,
                checked = shutterSoundEnabled,
                onCheckedChange = { checked ->
                    coroutineScope.launch { appSettings.setShutterSoundEnabled(checked) }
                },
            )

            OptiSettingsToggleRow(
                title = "Haptic Shutter Feedback",
                subtitle = "Tactile vibration when pressing shutter",
                leadingIcon = Icons.Outlined.Vibration,
                checked = hapticFeedbackEnabled,
                onCheckedChange = { checked ->
                    coroutineScope.launch { appSettings.setHapticFeedbackEnabled(checked) }
                },
            )

            OptiSettingsToggleRow(
                title = "Volume Key Shutter",
                subtitle = "Press volume keys to trigger camera shutter",
                checked = volumeKeyShutterEnabled,
                onCheckedChange = { checked ->
                    coroutineScope.launch { appSettings.setVolumeKeyShutterEnabled(checked) }
                },
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = OptiLensTheme.spacing.s),
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            // ── Processing & Color Section ─────────────────────────────────
            SettingsSectionHeader(title = "Image Processing")

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = OptiLensTheme.spacing.l, vertical = OptiLensTheme.spacing.s),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = OptiLensTheme.spacing.s),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ColorLens,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(OptiLensTheme.iconSizes.standard),
                    )
                    Text(
                        text = "Color Rendering Profile",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.padding(start = OptiLensTheme.spacing.l),
                    )
                }

                OptiSegmentedControl(
                    items = listOf(ColorProfile.NATURAL, ColorProfile.BALANCED, ColorProfile.VIVID),
                    selectedIndex = when (colorProfile) {
                        ColorProfile.NATURAL  -> 0
                        ColorProfile.BALANCED -> 1
                        ColorProfile.VIVID    -> 2
                    },
                    onItemSelected = { index ->
                        val selected = when (index) {
                            0 -> ColorProfile.NATURAL
                            1 -> ColorProfile.BALANCED
                            else -> ColorProfile.VIVID
                        }
                        coroutineScope.launch { appSettings.setColorProfile(selected) }
                    },
                    itemLabel = { profile ->
                        when (profile) {
                            ColorProfile.NATURAL  -> "Natural"
                            ColorProfile.BALANCED -> "Balanced"
                            ColorProfile.VIVID    -> "Vivid"
                        }
                    },
                )
            }

            OptiSettingsToggleRow(
                title = "Save Original Photo",
                subtitle = "Keep unenhanced shot alongside processed photo",
                leadingIcon = Icons.Outlined.Photo,
                checked = keepOriginalEnabled,
                onCheckedChange = { checked ->
                    coroutineScope.launch { appSettings.setKeepOriginalEnabled(checked) }
                },
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = OptiLensTheme.spacing.s),
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            // ── Privacy Section ────────────────────────────────────────────
            SettingsSectionHeader(title = "Privacy & Location")

            OptiSettingsToggleRow(
                title = "Location Tagging",
                subtitle = "Add GPS coordinates to photo EXIF metadata (opt-in)",
                leadingIcon = Icons.Outlined.LocationOn,
                checked = locationTaggingEnabled,
                onCheckedChange = { checked ->
                    coroutineScope.launch { appSettings.setLocationTaggingEnabled(checked) }
                },
            )

            Spacer(modifier = Modifier.height(OptiLensTheme.spacing.giant))
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = androidx.compose.ui.unit.TextUnit(1.2f, androidx.compose.ui.unit.TextUnitType.Sp),
        ),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(
            start = OptiLensTheme.spacing.l,
            top = OptiLensTheme.spacing.l,
            bottom = OptiLensTheme.spacing.xs,
        ),
    )
}
