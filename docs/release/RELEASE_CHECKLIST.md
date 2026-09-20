# OptiLens — Release Hardening Checklist (v1.0.0 Candidate)

| Parameter | Specification | Status |
|---|---|---|
| **App Name** | OptiLens | ✅ PASS |
| **Package / Application ID** | `com.webappypie.optilens` | ✅ PASS |
| **Release Version** | `1.0.0` (`versionCode = 1`) | ✅ PASS |
| **Target SDK / Min SDK** | Target SDK 37 / Min SDK 26 | ✅ PASS |
| **Toolchain** | AGP 9.4.0, Kotlin Compose 2.4.20, Java 17 | ✅ PASS |
| **Build Target** | Android App Bundle (`:app:bundleRelease`) | ✅ PASS |

---

## 1. Toolchain, SDK, and Platform Validation

- [x] **Target SDK 37 (Android 15 / 16 Preview)**: Fully targets the latest required Google Play API level.
- [x] **Min SDK 26 (Android 8.0 Oreo)**: Covers over 96% of active global Android hardware while guaranteeing modern Camera2 / CameraX HAL support.
- [x] **Single-Source Versioning**: `versionCode = 1` and `versionName = "1.0.0"` declared in `app/build.gradle.kts`.
- [x] **Java 17 & Kotlin Toolchain**: JDK 17 bytecode compatibility configured via `jvmToolchain(17)`.

---

## 2. Signing Configuration & Secret Protection

- [x] **Zero Hardcoded Secrets**: No passwords, keystore binaries, or API keys are committed in git.
- [x] **Gitignore Hardening**: `.gitignore` explicitly filters `*.jks`, `*.keystore`, `keystore.properties`, `signing.properties`, and `secrets.properties`.
- [x] **Flexible Production Signing**:
  - Automatically ingests environment variables (`OPTILENS_KEYSTORE_PATH`, `OPTILENS_KEYSTORE_PASSWORD`, `OPTILENS_KEY_ALIAS`, `OPTILENS_KEY_PASSWORD`) or Gradle project properties.
  - Gracefully falls back to debug signing for automated local validation and CI verification pipelines without exposing production keys.

---

## 3. R8 Optimization, Obfuscation & Resource Shrinking

- [x] **R8 Optimization Enabled**: `isMinifyEnabled = true` in `buildTypes.release`.
- [x] **Resource Shrinking Enabled**: `isShrinkResources = true` removes unused drawables, layouts, and strings.
- [x] **ProGuard Rule Hardening (`app/proguard-rules.pro`)**:
  - Keep entry points (`OptiLensApp`, `MainActivity`).
  - Keep `@Serializable` classes and companions for type-safe navigation and config parsing.
  - Keep Hilt components and dependency injection bindings.
  - Keep CameraX and Camera2 vendor extension hooks.
  - Keep Google Play Billing API interfaces.
  - Keep Google Mobile Ads safe monetization classes.
  - Keep Firebase Remote Config and Analytics models.
  - Keep ML Kit Face Detection reflection references.
  - Keep native JNI bindings and OpenCV native symbols.
- [x] **Deobfuscation Mapping Generation**: Verified output at `app/build/outputs/mapping/release/mapping.txt` for upload to Play Console.

---

## 4. Permissions & Privacy Integrity

- [x] **Minimal Permissions Principle**:
  - `android.permission.CAMERA`: Strictly required for viewfinder and capture.
  - `com.android.vending.BILLING`: Strictly required for Google Play in-app purchases.
- [x] **Zero Storage Permissions**: No `READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE`, or `READ_MEDIA_IMAGES`. The app exclusively uses the Android Photo Picker (`ActivityResultContracts.PickVisualMedia`) and scoped `MediaStore` insertions.
- [x] **Zero Location Permissions**: No location permissions requested or tracked.
- [x] **Hardware Features Configured**:
  - `android.hardware.camera.any` (`required = true`).
  - `android.hardware.camera.autofocus` (`required = false`) to support fixed-focus devices.
  - `android.hardware.camera.flash` (`required = false`) to support devices without flash.
- [x] **Network Security**: Cleartext traffic disabled by default; `network_security_config.xml` active.
- [x] **Scoped Backup Rules**: `backup_rules.xml` and `data_extraction_rules.xml` exclude sensitive internal tokens and cache.

---

## 5. Google Play Policy & Data Safety Disclosures

- [x] **Data Safety Form**: Completed in [`docs/release/PLAY_STORE_DATA_SAFETY.md`](file:///D:/Mobile-App/OptiLens/docs/release/PLAY_STORE_DATA_SAFETY.md).
- [x] **100% On-Device Photo Processing**: User captures and edited photos are processed strictly on the device ISP/NPU/CPU. Zero photo bytes are transmitted off-device.
- [x] **Ads Policy Compliance**:
  - Google Mobile Ads sample test ID properly documented for replacement with production ad unit before launch.
  - Ads are strictly restricted to the review screen; **never** displayed during live viewfinder preview, burst capture, or shutter execution.
- [x] **Play Billing Compliance**:
  - Subscription/Lifetime products (`optilens_pro_lifetime`, `optilens_pro_annual`) backed by dynamic Remote Config fallback.
  - Entitlements verified locally via encrypted cache.
- [x] **Truth in Advertising & Store Claims**:
  - Explicitly disclaims fake "100x digital zoom" or synthetic moon replacements.
  - Positioned honestly as an on-device computational photography tool that optimizes dynamic range, noise, and exposure.

---

## 6. Stability, Thermal, and Resource Gates

- [x] **Thermal Throttling**: Active monitoring via `PowerManager` thermal status listener (Phase 19). Automatically drops frame burst count and disables heavy multi-frame fusion at `THERMAL_STATUS_SEVERE`.
- [x] **Memory Management**: Low-memory callbacks (`ComponentCallbacks2`) bound to capture queues. Caps burst depth to 4 frames on $\le$ 4GB RAM devices.
- [x] **Resource Leaks**: Verified zero memory leak across 100 rapid consecutive captures (Phase 22 matrix).
- [x] **Multi-Device Quirk Isolation**: Non-global isolation verified. A broken OEM model pattern only throttles or patches that specific model; other devices remain unrestricted.
