# OptiLens — Toolchain Record

Generated at end of Phase 00.

## Build Tools

| Tool | Version | Source |
|---|---|---|
| **Gradle** | 9.7.1 | Gradle Wrapper (`gradle/wrapper/gradle-wrapper.properties`) |
| **Android Gradle Plugin (AGP)** | 9.4.0 | `gradle/libs.versions.toml` |
| **Kotlin** | 2.4.20 | `gradle/libs.versions.toml` |
| **Kotlin Compose Compiler Plugin** | 2.4.20 | `gradle/libs.versions.toml` |
| **Jetpack Compose BOM** | 2026.09.00 | `gradle/libs.versions.toml` |

## JDK Configuration

| Item | Value |
|---|---|
| **System JDK** | JDK 25.0.2 (Java HotSpot 64-Bit, `C:\Program Files\Java\jdk-25.0.2`) |
| **Project JDK (Toolchain)** | JDK 17 — configured via `kotlin.jvmToolchain(17)` in `app/build.gradle.kts` |
| **sourceCompatibility** | `JavaVersion.VERSION_17` |
| **targetCompatibility** | `JavaVersion.VERSION_17` |
| **Note** | Gradle provisions JDK 17 automatically via toolchain support. System JDK unchanged. |

## Android SDK

| Item | Value |
|---|---|
| **ANDROID_HOME** | `C:\Users\azadt\AppData\Local\Android\Sdk` |
| **compileSdk** | 37 (bumped from 36 — required by Compose BOM 2026.09.00 / Compose 1.12.1) |
| **targetSdk** | 37 |
| **minSdk** | 26 |
| **Build Tools** | 36.1.0 |
| **NDK** | 28.2.13676358 (available; not used in Phase 00) |
| **CMake** | 3.22.1 (available; not used in Phase 00) |

## Installed Android API Levels

| API Level | Platform |
|---|---|
| 31 | android-31 |
| 34 | android-34 |
| 35 | android-35 |
| 36 | android-36 ✅ |
| 37 | android-37 |
| 37.0 | android-37.0 |

## Emulator / Device

| Item | Value |
|---|---|
| **Running emulator** | `emulator-5580` |
| **Android version** | 16 (API 36) |
| **System images available** | android-36, android-36.1 |
| **Named AVDs** | None configured (emulator launched from Android Studio) |

## Application Identity

| Item | Value |
|---|---|
| **applicationId (release)** | `com.webappypie.optilens` |
| **applicationId (debug)** | `com.webappypie.optilens.debug` |
| **versionCode** | 1 |
| **versionName** | 0.1.0 |

## Git

| Item | Value |
|---|---|
| **Git version** | 2.53.0.windows.1 |
| **Branch** | `main` |
| **Remote** | `https://github.com/webappypie/OptiLens.git` |
