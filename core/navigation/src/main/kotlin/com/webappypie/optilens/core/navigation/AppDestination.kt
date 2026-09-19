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

    /** Main camera viewfinder — the app entry point. */
    @Serializable
    data object Camera : AppDestination

    /** Full-screen photo gallery / media browser. */
    @Serializable
    data object Gallery : AppDestination

    /** User settings screen. */
    @Serializable
    data object Settings : AppDestination

    // ── Photo detail / result ─────────────────────────────────────────

    /**
     * Photo detail / AI enhancement result viewer.
     * @param photoUri Content URI of the photo to display.
     */
    @Serializable
    data class PhotoDetail(val photoUri: String) : AppDestination

    // ── Pro / Onboarding ──────────────────────────────────────────────

    /** Pro upgrade screen (paywall). */
    @Serializable
    data object ProUpgrade : AppDestination

    /** First-run permission onboarding. */
    @Serializable
    data object Onboarding : AppDestination
}
