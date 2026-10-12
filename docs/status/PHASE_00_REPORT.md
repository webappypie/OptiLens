# Phase 00 — Existing Project Baseline: Report

| Field | Value |
|---|---|
| **Phase** | 00 — Existing Project Baseline (new roadmap, `docs/15_ROADMAP.md`) |
| **Prompt** | `prompts/PHASE_00_EXISTING_PROJECT_BASELINE.md` |
| **Date** | 2026-10-12 |
| **Result** | Baseline recorded. Build OK; unit tests 441/441 pass in 7 modules; **navigation unit tests do not compile**; **lint fails** (2 modules) |
| **Scope rule** | No bug fixes, no Firebase moves, no WAPCentral work. Only `.gitignore` (secret protection) and status docs changed |

> Note: Git history already contains `phase-00`…`phase-24` commits from the previous roadmap. This report supersedes the previous `PHASE_00_REPORT.md` (repository bootstrap), which remains in Git history at `060c1c2`.

## 1. Repository and Git state

| Item | Value |
|---|---|
| Branch / remote | `main`, `origin` → `https://github.com/webappypie/OptiLens.git` |
| Sync with origin before phase | 0 ahead / 0 behind (`716e75a`) |
| Uncommitted owner/agent work | 118 entries, **preserved, not committed by this phase** (see §7) |
| Secret scan | `wap_key_` appears only in the owner guide (now gitignored); never in Git history (`git log --all -S`) |

## 2. Application identity and modules

| Item | Value |
|---|---|
| applicationId | `com.webappypie.optilens` (debug: `com.webappypie.optilens.debug`, `versionName 1.0.0-debug`) |
| versionCode / versionName | 1 / 1.0.0 |
| minSdk / targetSdk / compileSdk | 26 / 37 / 37 |
| Modules | `:app`, `:core:common`, `:core:logging`, `:core:settings`, `:core:navigation`, `:core:ui`, `:core:camera`, `:core:imaging` (+ CMake native lib `optilens_imaging`) |
| Native ABIs built | arm64-v8a, armeabi-v7a, x86, x86_64 |

## 3. Toolchain and environment (observed)

| Item | Value |
|---|---|
| Gradle wrapper | 9.7.1 (launcher/daemon JVM: Oracle JDK 25.0.2) |
| AGP / Kotlin compose plugin / KSP | 9.4.0 / 2.4.20 / 2.3.4 |
| Kotlin JVM toolchain declared | 17 |
| Android SDK platforms | android-36, android-37, android-37.0 |
| NDK / CMake | 28.2.13676358 / 3.22.1 |
| Emulator AVDs | none |
| Physical device | Xiaomi Redmi Note 11 Pro+ 5G (`2201116SI`), Android 13 / API 33, arm64-v8a |

## 4. Dependencies (version catalog, unchanged)

Compose BOM 2026.09.00, core-ktx 1.16.0, lifecycle 2.9.1, activity-compose 1.10.1, core-splashscreen 1.0.1 (uncommitted addition), navigation 2.10.1, datastore 1.2.1, CameraX 1.6.2 (core/camera2/lifecycle/view/extensions), ML Kit face-detection 16.1.7, Hilt 2.60.1, hilt-navigation-compose 1.4.0, Play Billing 7.1.1, play-services-ads 23.6.0, kotlinx-serialization-json 1.8.0, coroutines 1.10.2.
Declared but **unused**: `firebase-bom` 33.9.0, `firebase-config-ktx`, `firebase-analytics-ktx` (no module depends on them; no google-services plugin).

## 5. SDK / configuration inventory (not moved, contents not printed)

| File | Location | Matches guide | Git |
|---|---|---|---|
| `google-services.json` | repo root (app module is `app/`) | project `optilens-511410`, package `com.webappypie.optilens` ✓ | ignored |
| `GoogleService-Info.plist` | repo root | bundle `com.webappypie.optilens.ios` ✓ (iOS out of scope) | **now ignored** (this phase) |
| `WAPCentral_OptiLens_Integration_Guide.md` | repo root | — (contains X-App-Key) | **now ignored** (this phase) |

Gaps: debug package `com.webappypie.optilens.debug` is not registered in `google-services.json`. The WAPCentral guide targets Flutter; no native Android WAPCentral SDK is present.

## 6. Verification executed (2026-10-12)

Command: `./gradlew assembleDebug testDebugUnitTest lintDebug --continue` (7 m 27 s, `BUILD FAILED`, 446 tasks), then
`./gradlew :core:camera:testDebugUnitTest --rerun :core:imaging:testDebugUnitTest --rerun :core:ui:testDebugUnitTest --rerun` (`BUILD SUCCESSFUL`) because those suites were initially reported UP-TO-DATE.

### Build
| Task | Result |
|---|---|
| `assembleDebug` | ✅ success (Kotlin compile up to date for current sources; CMake rebuilt for 4 ABIs) |
| `assembleRelease` | not run in this phase |

### Unit tests (all executed today)
| Module | Tests | Failures | Errors |
|---|---|---|---|
| app | 7 | 0 | 0 |
| core:camera | 183 | 0 | 0 |
| core:common | 27 | 0 | 0 |
| core:imaging | 97 | 0 | 0 |
| core:logging | 16 | 0 | 0 |
| core:settings | 36 | 0 | 0 |
| core:ui | 75 | 0 | 0 |
| core:navigation | **not run — test compilation fails** | — | — |
| **Total executed** | **441** | **0** | **0** |

Navigation failure: `NavigationManagerTest.kt:25` passes `AppDestination.AiTools` (now a `data class` in uncommitted work) where an `AppDestination` instance is expected.

### Lint (`lintDebug`)
| Module | Errors | Warnings | Notes |
|---|---|---|---|
| app | 2 | 80 | `StringFormatInvalid` on `privacy_notice_text` (unescaped `%`); app has `abortOnError = false` |
| core:camera | **45** (fails build) | 21 | 40 × unsafe Camera2 interop opt-in, 4 × `RestrictedApi` (`MeteringPoint.getX/getY`), 1 × `NewApi` |
| core:ui | **1** (fails build) | 13 | `MissingPermission` INTERNET for `AdView.loadAd` (permission is declared in app manifest, not library) |
| core:imaging | 0 | 12 | `UseKtx` |
| core:common / logging / navigation / settings | 0 | 0 | — |

### Physical device (install + cold launch only)
| Check | Result |
|---|---|
| `adb install -r app-debug.apk` | ✅ Success |
| Cold start (`am start -W`) | ✅ `TotalTime 2596 ms` |
| Crash buffer / FATAL after launch | ✅ none |
| Camera preview | ✅ live preview rendered; camera permission already granted; no permission screen shown |
| Visual defects observed | ❌ legacy "OptiLens" action bar drawn over the viewfinder; top camera controls drawn under the status bar; zoom selector shows only `1x` |

Capture, mode, AI Tools and gallery flows were **not** exercised (Phase 01/02 scope).

## 7. Evidence-backed defects recorded (not fixed — Phase 00 rule)

| # | Severity | Defect | Evidence |
|---|---|---|---|
| 1 | High | Crash risk on Android 8.0/8.1: `CameraCharacteristics.physicalCameraIds` (API 28) called without version guard, minSdk 26 | lint `NewApi`, `CameraCapabilityDetector.kt:295` |
| 2 | High | Night mode (non-vendor path) acquires a burst but never fuses or saves it; shows a 300 ms "processing" state | `CameraViewModel.takeNightPhoto` |
| 3 | High | Imaging pipeline (fusion/HDR/alignment/SR/portrait) has no consumer in capture or UI | no references outside `core:imaging` |
| 4 | High | "Cloud AI" path is simulated locally and consumes quota | `CloudEnhancementRepository.executeCloudRequest` |
| 5 | High | `AI_ENHANCE` fallback processes a synthetic flat buffer and reports success | `ProductionImagingPipeline` |
| 6 | Medium | Action bar over viewfinder: `installSplashScreen()` called after `super.onCreate()` | `MainActivity.kt:60,74`; device screenshot |
| 7 | Medium | AI Tools opened from camera has empty URI → indefinite spinner, no picker | `AiToolsViewModel.loadPhoto`, `AiToolsScreen` |
| 8 | Medium | Video mode in mode rail takes a still photo; no video dependency | `CameraScreen` shutter dispatch |
| 9 | Medium | Navigation unit tests do not compile | §6 |
| 10 | Medium | Lint errors fail `lintDebug` in camera and ui (CI `lintDebug` step will fail) | §6 |
| 11 | Medium | Model download progress simulated; placeholder checksums | `ModelDownloadManager`, `AiModelRegistry` |
| 12 | Low | Location tagging toggle has no implementation/permission; telemetry toggles only log to logcat | `SettingsScreen`, `CrashReporter` |
| 13 | Low | Invented fallback subscription prices; subscription products conflict with `docs/09` | `BillingManager.kt:177`, `EntitlementModels` |
| 14 | Low | Hardcoded fallback zoom stops label 0.6x/5x as optical | `CameraScreen` zoom selector |
| 15 | Risk | Release signing falls back to debug keystore | `app/build.gradle.kts` |

Previous-roadmap status reports (Phases 00–24, all marked PASS) are deleted in the uncommitted working tree; several of their claims (e.g. Night/Portrait processing) are contradicted by defects 2–3.

## 8. Files changed in this phase

| File | Change |
|---|---|
| `.gitignore` | Added `GoogleService-Info.plist` and `WAPCentral_*_Integration_Guide.md` (guide contains X-App-Key) |
| `docs/status/PHASE_00_REPORT.md` | This report |
| `docs/status/CURRENT_PHASE.md` | Updated phase status |

Everything else in the working tree (118 pre-existing changes) was left untouched and uncommitted.

## 9. Blockers and owner inputs

1. Decision on the 118 uncommitted changes (review/commit as-is, split, or discard) and on restoring deleted release docs (`THIRD_PARTY_LICENSES.md`, `PLAY_STORE_DATA_SAFETY.md`, `RELEASE_CHECKLIST.md`).
2. Native Android WAPCentral SDK, or approval of a Kotlin adapter over the documented REST API (plus request/response schemas).
3. AppLovin SDK key and MAX ad-unit IDs in the WAPCentral dashboard.
4. X-App-Key handling: build-time injection from `local.properties` (proposed).
5. Firebase: register `com.webappypie.optilens.debug` or drop the debug suffix.
6. Cloud AI: WAPCentral `image` module is not enabled for this app.
7. Monetization model and Play Console product IDs/prices.

## 10. Recommended next phase

Phase 01 — Crash and Capture Reliability (`prompts/PHASE_01_CRASH_CAPTURE_RELIABILITY.md`): defects 1, 2, 6, 8, 9 and the camera/ui lint errors, with on-device capture/save/review verification on the connected Redmi Note 11 Pro+.

## Correction (recorded in Phase 01, 2026-10-12)

The device check above observed the app for only ~6 s after launch. The device's crash dropbox shows that same session terminated with a native crash (SIGSEGV in `libmedia_jni` `Image_getFormat`, via ML Kit face detection) 53 s after launch. Root cause and fix: `docs/status/PHASE_01_REPORT.md`, defect #1.
