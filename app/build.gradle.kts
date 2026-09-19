plugins {
    alias(libs.plugins.android.application)
    // kotlin.android intentionally omitted: AGP 9.0+ includes Kotlin support built-in.
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.webappypie.optilens"
    // compileSdk 37 required by Compose BOM 2026.09.00 (Compose 1.12.1).
    // android-37 platform is installed on this machine.
    compileSdk = 37

    defaultConfig {
        applicationId = "com.webappypie.optilens"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    // JDK 17 toolchain — does not change the system JDK; Gradle provisions it automatically.
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(17)
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isDebuggable = true
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Signing config will be added in Phase 23 (Release Hardening).
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        // Fail on release lint errors; warnings are informational only.
        abortOnError = false
        warningsAsErrors = false
        // Note: htmlReport/xmlReport removed — AGP 9.x always generates lint reports.
        // Consume via SingleArtifact.LINT_HTML_REPORT if needed in CI.
    }
}

dependencies {
    // ── AndroidX Core ──────────────────────────────────────
    implementation(libs.androidx.core.ktx)

    // ── Lifecycle ──────────────────────────────────────────
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)

    // ── Activity ───────────────────────────────────────────
    implementation(libs.androidx.activity.compose)

    // ── Compose BOM (pin all Compose versions via single BOM) ──
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)

    // ── Debug tooling ──────────────────────────────────────
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    // ── Unit tests ─────────────────────────────────────────
    testImplementation(libs.junit)

    // ── Instrumented tests ─────────────────────────────────
    androidTestImplementation(libs.androidx.junit.ext)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
}
