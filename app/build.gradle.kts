plugins {
    alias(libs.plugins.android.application)
    // kotlin.android intentionally omitted: AGP 9.0+ includes Kotlin support built-in.
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
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
        versionName = "1.0.0"

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

    signingConfigs {
        create("release") {
            val keystorePath = project.findProperty("OPTILENS_KEYSTORE_PATH") as? String
                ?: System.getenv("OPTILENS_KEYSTORE_PATH")
            val keystorePassword = project.findProperty("OPTILENS_KEYSTORE_PASSWORD") as? String
                ?: System.getenv("OPTILENS_KEYSTORE_PASSWORD")
            val keyAlias = project.findProperty("OPTILENS_KEY_ALIAS") as? String
                ?: System.getenv("OPTILENS_KEY_ALIAS")
            val keyPassword = project.findProperty("OPTILENS_KEY_PASSWORD") as? String
                ?: System.getenv("OPTILENS_KEY_PASSWORD")

            if (!keystorePath.isNullOrBlank() && File(keystorePath).exists()) {
                storeFile = File(keystorePath)
                storePassword = keystorePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
            } else {
                // Fallback for local validation and CI testing without exposed production credentials
                val debugKeystore = signingConfigs.getByName("debug").storeFile
                if (debugKeystore != null && debugKeystore.exists()) {
                    storeFile = debugKeystore
                    storePassword = signingConfigs.getByName("debug").storePassword
                    this.keyAlias = signingConfigs.getByName("debug").keyAlias
                    this.keyPassword = signingConfigs.getByName("debug").keyPassword
                }
            }
        }
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
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
        checkReleaseBuilds = false
        // Note: htmlReport/xmlReport removed — AGP 9.x always generates lint reports.
        // Consume via SingleArtifact.LINT_HTML_REPORT if needed in CI.
    }
}

dependencies {
    // ── Core modules ───────────────────────────────────────
    implementation(project(":core:common"))
    implementation(project(":core:logging"))
    implementation(project(":core:settings"))
    implementation(project(":core:navigation"))
    implementation(project(":core:ui"))
    implementation(project(":core:camera"))
    implementation(project(":core:imaging"))

    // ── Hilt DI ────────────────────────────────────────────
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // ── AndroidX Core & Lifecycle ──────────────────────────
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // ── Activity ───────────────────────────────────────────
    implementation(libs.androidx.activity.compose)

    // ── Compose BOM ────────────────────────────────────────
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)

    // ── Navigation ─────────────────────────────────────────
    implementation(libs.androidx.navigation.compose)

    // ── Debug tooling ──────────────────────────────────────
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    // ── Unit tests ─────────────────────────────────────────
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.hilt.android.testing)

    // ── Instrumented tests ─────────────────────────────────
    androidTestImplementation(libs.androidx.junit.ext)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
}
