package com.webappypie.optilens.core.navigation

import kotlinx.serialization.Serializable

/**
 * All navigable destinations in OptiLens.
 *
 * Uses type-safe Navigation 2.10+ routes backed by Kotlin Serialization.
 * Each destination is a @Serializable object or data class.
 * Arguments are part of the type (data classes), not path parameters.
 */
sealed interface AppDestination {

    // ── Top-level screens ──────────────────────────────────────────────

    /** Branded splash screen shown once per process on cold start. */
    @Serializable
    data object Splash : AppDestination

    /** Main camera viewfinder — the app entry point. */
    @Serializable
    data object Camera : AppDestination

    /** Full-screen photo gallery / media browser. */
    @Serializable
    data object Gallery : AppDestination

    /** User settings screen. */
    @Serializable
    data object Settings : AppDestination

    /**
     * AI computational photography tools screen.
     * @param photoUri Optional content URI of a photo to load for editing. Empty string if opened standalone.
     */
    @Serializable
    data class AiTools(val photoUri: String = "") : AppDestination

    // ── Photo detail / result ─────────────────────────────────────────

    /**
     * Photo detail / AI enhancement result viewer.
     * @param photoUri Content URI of the photo to display.
     * @param fromCapture True when opened automatically right after capture (enables Retake).
     */
    @Serializable
    data class PhotoDetail(val photoUri: String, val fromCapture: Boolean = false) : AppDestination

    // ── Pro / Onboarding ──────────────────────────────────────────────

    /** Pro upgrade screen (paywall). */
    @Serializable
    data object ProUpgrade : AppDestination

    /** First-run permission onboarding. */
    @Serializable
    data object Onboarding : AppDestination

    // ── Diagnostics ───────────────────────────────────────────────────

    /** Hardware and camera capability diagnostics screen. */
    @Serializable
    data object CameraDiagnostics : AppDestination
}
