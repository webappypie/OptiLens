// Top-level build file — configuration shared across all sub-projects/modules.
// NOTE: org.jetbrains.kotlin.android is intentionally absent.
// AGP 9.0+ includes Kotlin support built-in; applying it explicitly causes a build error.
plugins {
    alias(libs.plugins.android.application)  apply false
    alias(libs.plugins.android.library)      apply false
    alias(libs.plugins.kotlin.compose)       apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp)                  apply false
    alias(libs.plugins.hilt)                 apply false
}
