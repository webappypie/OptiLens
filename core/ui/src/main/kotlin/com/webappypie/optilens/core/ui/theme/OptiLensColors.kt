package com.webappypie.optilens.core.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// ── Dark Palette (Primary Camera Experience) ──────────────────────────────
val DarkBackground         = Color(0xFF090A0D)
val DarkSurface            = Color(0xFF13161D)
val DarkSurfaceVariant     = Color(0xFF1C202B)
val DarkSurfaceContainer   = Color(0xFF242A38)
val DarkOnBackground       = Color(0xFFF1F3F9)
val DarkOnSurface          = Color(0xFFE5E9F0)
val DarkOnSurfaceVariant   = Color(0xFF9DA7B8)

val DarkPrimary            = Color(0xFFFFB74D) // Warm camera gold accent
val DarkOnPrimary          = Color(0xFF3E2800)
val DarkPrimaryContainer   = Color(0xFF5A3C00)
val DarkOnPrimaryContainer = Color(0xFFFFDDB3)

val DarkSecondary          = Color(0xFF60A5FA) // Precision blue
val DarkOnSecondary        = Color(0xFF003062)
val DarkSecondaryContainer = Color(0xFF00468B)
val DarkOnSecondaryContainer = Color(0xFFD6E4FF)

val DarkTertiary           = Color(0xFF34D399) // Success / Focus green
val DarkOnTertiary         = Color(0xFF003822)

val DarkError              = Color(0xFFF87171)
val DarkOnError            = Color(0xFF4C0005)

val DarkOutline            = Color(0xFF3B4354)
val DarkOutlineVariant     = Color(0xFF262C38)

// ── Light Palette (Clean Gallery / Settings Experience) ───────────────────
val LightBackground        = Color(0xFFF8F9FC)
val LightSurface           = Color(0xFFFFFFFF)
val LightSurfaceVariant    = Color(0xFFEEF1F6)
val LightSurfaceContainer  = Color(0xFFE2E7F0)
val LightOnBackground      = Color(0xFF0F141C)
val LightOnSurface         = Color(0xFF1A202C)
val LightOnSurfaceVariant  = Color(0xFF5F6B7D)

val LightPrimary           = Color(0xFFC05621) // Rich amber
val LightOnPrimary         = Color(0xFFFFFFFF)
val LightPrimaryContainer  = Color(0xFFFFE0B2)
val LightOnPrimaryContainer= Color(0xFF3B1A00)

val LightSecondary         = Color(0xFF2563EB) // Precision royal blue
val LightOnSecondary       = Color(0xFFFFFFFF)
val LightSecondaryContainer= Color(0xFFDBEAFE)
val LightOnSecondaryContainer = Color(0xFF001B3F)

val LightTertiary          = Color(0xFF059669) // Success green
val LightOnTertiary        = Color(0xFFFFFFFF)

val LightError             = Color(0xFFDC2626)
val LightOnError           = Color(0xFFFFFFFF)

val LightOutline           = Color(0xFFCBD5E1)
val LightOutlineVariant    = Color(0xFFE2E8F0)

// ── Material 3 Color Schemes ──────────────────────────────────────────────
val OptiLensDarkColorScheme = darkColorScheme(
    primary                 = DarkPrimary,
    onPrimary               = DarkOnPrimary,
    primaryContainer        = DarkPrimaryContainer,
    onPrimaryContainer      = DarkOnPrimaryContainer,
    secondary               = DarkSecondary,
    onSecondary             = DarkOnSecondary,
    secondaryContainer      = DarkSecondaryContainer,
    onSecondaryContainer    = DarkOnSecondaryContainer,
    tertiary                = DarkTertiary,
    onTertiary              = DarkOnTertiary,
    background              = DarkBackground,
    onBackground            = DarkOnBackground,
    surface                 = DarkSurface,
    onSurface               = DarkOnSurface,
    surfaceVariant          = DarkSurfaceVariant,
    onSurfaceVariant        = DarkOnSurfaceVariant,
    error                   = DarkError,
    onError                 = DarkOnError,
    outline                 = DarkOutline,
    outlineVariant          = DarkOutlineVariant,
)

val OptiLensLightColorScheme = lightColorScheme(
    primary                 = LightPrimary,
    onPrimary               = LightOnPrimary,
    primaryContainer        = LightPrimaryContainer,
    onPrimaryContainer      = LightOnPrimaryContainer,
    secondary               = LightSecondary,
    onSecondary             = LightOnSecondary,
    secondaryContainer      = LightSecondaryContainer,
    onSecondaryContainer    = LightOnSecondaryContainer,
    tertiary                = LightTertiary,
    onTertiary              = LightOnTertiary,
    background              = LightBackground,
    onBackground            = LightOnBackground,
    surface                 = LightSurface,
    onSurface               = LightOnSurface,
    surfaceVariant          = LightSurfaceVariant,
    onSurfaceVariant        = LightOnSurfaceVariant,
    error                   = LightError,
    onError                 = LightOnError,
    outline                 = LightOutline,
    outlineVariant          = LightOutlineVariant,
)
