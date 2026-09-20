# OptiLens — Comprehensive Privacy, Analytics & Security Audit Report
**Phase 21 Deliverable** | Date: September 2026 | Version: 1.0.0-rc1 | Target OS: Android 14+ (API 34, minSdk 26)

---

## Executive Summary
Before broad device matrix testing and Google Play Store release, OptiLens underwent an exhaustive 15-point privacy, analytics, and security hardening audit. OptiLens is architected from first principles as a **100% on-device computational camera application**. The core guarantee of OptiLens is that **photo bytes, raw Bayer buffers, viewfinder frames, and visual metadata NEVER leave the user's device**.

This audit verifies all permissions, manifest definitions, exported components, network security boundaries, telemetry pipelines, log redaction mechanisms, cache lifecycles, and entitlement gates to ensure total alignment with Google Play Data Safety policies, GDPR, and Android platform security guidelines.

---

## 15-Point Privacy & Security Audit

### 1. Permission Inventory
An audit of all declared and requested permissions across `app/src/main/AndroidManifest.xml` and library dependencies was performed:

| Permission | Protection Level | Runtime Requested | Purpose & User Justification |
|---|---|---|---|
| `android.permission.CAMERA` | `dangerous` | Yes | Essential for live viewfinder preview, frame analysis, hardware sensor control, and photo/video capture. |
| `android.permission.RECORD_AUDIO` | `dangerous` | Yes | Audio recording accompanying video capture in high-fps and slow-motion video modes. |
| `android.permission.ACCESS_FINE_LOCATION` | `dangerous` | Yes (Opt-in) | Geotagging EXIF metadata in captured photos. Strictly optional; disabled by default. |
| `android.permission.ACCESS_COARSE_LOCATION` | `dangerous` | Yes (Opt-in) | Approximate location fallback for geotagging when fine precision is denied or unavailable. |
| `android.permission.POST_NOTIFICATIONS` | `dangerous` | Yes | Processing completion notifications for extended RAW/HDR multi-frame stacking. |

#### Storage Permission Verification:
* OptiLens requests **ZERO broad storage permissions**:
  * `android.permission.READ_EXTERNAL_STORAGE`: **NOT REQUESTED**
  * `android.permission.WRITE_EXTERNAL_STORAGE`: **NOT REQUESTED**
  * `android.permission.MANAGE_EXTERNAL_STORAGE`: **NOT REQUESTED**
  * `android.permission.READ_MEDIA_IMAGES`: **NOT REQUESTED**
  * `android.permission.READ_MEDIA_VIDEO`: **NOT REQUESTED**
* **Mechanism**: OptiLens leverages standard Android Scoped Storage via `MediaStore.Images.Media` insertion and the system `ActivityResultContracts.PickVisualMedia` PhotoPicker. This enforces the principle of least privilege, eliminating unnecessary access to user media files.

#### Hardware Features Declared:
```xml
<uses-feature android:name="android.hardware.camera" android:required="true" />
<uses-feature android:name="android.hardware.camera.autofocus" android:required="false" />
<uses-feature android:name="android.hardware.camera.flash" android:required="false" />
```
Declaring autofocus and flash as non-required ensures wide compatibility across ultra-wide, secondary sensor, and tablet configurations without artificial Play Store installation barriers.

---

### 2. Merged Manifest Audit
The merged manifest was analyzed to verify that third-party SDKs do not inject stealth permissions or misconfigured attributes:

* `android:allowBackup="false"`: Enforced at the `<application>` tag. Disables ADB backup extraction and automated Google Drive backup of internal application DataStore preferences, preventing credential or token exfiltration.
* `android:supportsRtl="true"`: Fully configured for internationalization.
* `android:networkSecurityConfig="@xml/network_security_config"`: Bound to application root.
* No advertising ID permission (`com.google.android.gms.permission.AD_ID`): Verified absent. OptiLens contains zero ad networks.
* No foreground service types with sensitive or background tracking capabilities.

---

### 3. Exported Component Audit
To prevent inter-process unauthorized access, Intent spoofing, and privilege escalation, all application components were reviewed:

| Component | Type | Exported | Intent Filters / Protection | Security Assessment |
|---|---|---|---|---|
| `com.webappypie.optilens.MainActivity` | Activity | `true` | `android.intent.action.MAIN`<br>`android.intent.category.LAUNCHER` | **SECURE**. Only launcher entry point is exported. |
| `androidx.core.content.FileProvider` | ContentProvider | `false` | `android:grantUriPermissions="true"`<br>`android:authorities="${applicationId}.fileprovider"` | **SECURE**. Private provider; grants scoped URI permissions only via explicit Intent flags. |
| Services | Service | None | N/A | **SECURE**. Zero background services exported. |
| Broadcast Receivers | Receiver | None | N/A | **SECURE**. Zero public broadcast receivers declared. |

---

### 4. Analytics Payload Audit
OptiLens maintains a strict **Zero-PII Analytics Guarantee**:
* **Payload Structure**: Telemetry events recorded via `MonetizationAnalytics` and feature instrumentation capture strictly enumerated operational metrics:
  * `feature_name`: (e.g. `hdr_fusion`, `super_res_2x`, `pro_shutter`)
  * `tier`: (e.g. `tier_0_basic`, `tier_1_standard`, `tier_2_extreme`)
  * `latency_ms`: Execution time integer
  * `outcome`: (e.g. `success`, `fallback_applied`, `cancelled`)
* **Excluded Data**:
  * ZERO user identifiers, email addresses, names, or device serial numbers.
  * ZERO location coordinates or IP addresses.
  * ZERO image buffers, filenames, dimensions, or EXIF metadata.
* **Consent Control**: Analytics transmission is gated by `AppSettings.analyticsEnabled`, which **defaults to `false`** (explicit opt-in required).

---

### 5. Crash Reporting Configuration
Crash reporting is governed by the `CrashReporter` interface in `core:logging`:
* **Default Posture**: `crashReportingEnabled` **defaults to `false`**.
* **Pre-Transmission Redaction**: Even when opt-in is provided by the user, all error messages, custom keys, and stack traces pass through `LogRedactor` before transmission.
* **Privacy Isolation**: Crash reporting operates independently from analytics toggles.

---

### 6. Production Log Redaction
A regex-driven sanitization layer (`LogRedactor.kt` and `RedactingLogger.kt`) wraps the logging system:
* **Redacted Patterns**:
  1. **Absolute Paths**: Redacts `/data/user/0/...`, `/storage/emulated/0/...`, `C:\...`, and `D:\...` file paths to `[PATH_REDACTED]`.
  2. **Content URIs**: Redacts `content://...` to `[CONTENT_URI_REDACTED]`.
  3. **Email Addresses**: Redacts `user@example.com` to `[EMAIL_REDACTED]`.
  4. **IP Addresses**: Redacts IPv4 and IPv6 strings to `[IP_REDACTED]`.
  5. **Authentication Tokens**: Redacts `Bearer <token>`, `api_key=...`, `jwt=...` to `Bearer [REDACTED]`.
  6. **Payment Cards**: Redacts PAN strings matching credit card sequences.
* **Verification**: Verified via automated unit tests in `core:logging:testDebugUnitTest`.

---

### 7. Cache & Temporary File Lifecycle
Computational photography operations generate large intermediate data structures (raw Bayer mosaics, float32 feature maps, multi-frame stacking buffers).
* **Storage Location**: Stored strictly in sandbox directories:
  * `context.cacheDir`
  * `context.externalCacheDir`
* **Lifecycle Rules**:
  * Temporary image buffers are automatically released and deleted upon pipeline completion or error.
  * Preview thumbnails in Coil cache are bounded by memory and disk size quotas.
* **User Control**: Added a dedicated **"Clear Temporary Cache"** row in the Settings UI, allowing users to wipe all cached buffers and previews on demand.

---

### 8. Safe Content URI Handling & FileProvider Hardening
The application `file_paths.xml` configuration was hardened against directory traversal and path manipulation vulnerabilities:

#### Hardened `file_paths.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<paths xmlns:android="http://schemas.android.com/apk/res/android">
    <files-path name="internal_files" path="." />
    <cache-path name="cache_files" path="." />
    <external-files-path name="external_app_files" path="." />
    <external-media-path name="optilens_pictures" path="Pictures/OptiLens" />
</paths>
```
* **Security Improvement**: Removed dangerous `<external-path name="external_files" path="." />` which previously exposed root external storage. Paths are now tightly scoped to the app's dedicated subdirectories.
* **Intent Sharing**: Photo sharing passes content URIs with `Intent.FLAG_GRANT_READ_URI_PERMISSION` explicitly targeted to the receiving activity.

---

### 9. Network Security Configuration
A dedicated `network_security_config.xml` was created and linked to the manifest:

```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <base-config cleartextTrafficPermitted="false">
        <trust-anchors>
            <certificates src="system" />
        </trust-anchors>
    </base-config>
    <debug-overrides>
        <trust-anchors>
            <certificates src="system" />
            <certificates src="user" />
        </trust-anchors>
    </debug-overrides>
</network-security-config>
```
* **Cleartext Blocked**: Plaintext HTTP traffic (`http://`) is strictly prohibited application-wide.
* **Certificate Pinning / Trust**: Only system CA certificates are trusted in production builds. User-installed certificates (e.g. from malicious proxies) are ignored in release builds.

---

### 10. Third-Party SDK Data Inventory
Every third-party dependency was inventoried for data collection practices:

| Dependency | Purpose | Network Calls? | Data Transmitted |
|---|---|---|---|
| `androidx.camera:camera-*` | CameraX hardware abstraction | None | Local device only |
| `androidx.compose.*` | Declarative UI | None | Local device only |
| `com.google.android.play:billing-ktx` | Pro entitlement management | Handled by Google Play | Transaction token, Product ID |
| `com.google.mlkit:face-detection` | On-device face detection | None | Local model execution only |
| `io.coil-kt:coil-compose` | Image loading / thumbnails | None in camera pipeline | Local disk/memory cache only |
| `com.jakewharton.timber:timber` | Logging | None | Local Logcat / disk only |

---

### 11. Draft Google Play Data Safety Mapping
For submission in Google Play Console:

* **Does your app collect or share any user data?**
  * **Yes (Optional, Opt-in Only)**: App info and performance (Crash logs, Diagnostics).
* **Is this data shared with third parties?**
  * **No**. OptiLens shares zero data with third parties.
* **Data Types Collected**:
  1. **App Info and Performance**:
     * *Crash logs*: Collected only if user enables Crash Reporting. Purpose: App functionality, Analytics. Not linked to user identity.
     * *Diagnostics / Performance metrics*: Collected only if user enables Anonymous Diagnostics. Purpose: Analytics. Not linked to user identity.
  2. **Location**:
     * Optional EXIF tagging. Data is written directly to the local image file on the device. **Zero location data is collected or transmitted by OptiLens**.
  3. **Photos and Videos**:
     * **NOT COLLECTED**. All photos and videos remain 100% on the device.
* **Security Practices**:
  * Data is encrypted in transit (HTTPS enforced via Network Security Config).
  * Request data deletion: Supported via in-app cache wipe and standard uninstall.

---

### 12. Search for Debug & Pro Entitlement Bypasses
A security code audit was conducted across the codebase to ensure no backdoors exist:
* **Queries Executed**: `isTest`, `bypassPro`, `debugPro`, `alwaysActive`, `DEBUG_ENTITLEMENTS`.
* **Findings**:
  * `EntitlementRepositoryImpl`: Uses real Google Play Billing client queries and encrypted DataStore state. No hardcoded test entitlements or debug overrides exist in release builds.
  * `SrDeviceTierGate`: Tier classification evaluates actual device RAM, GPU capabilities, and Vulkan level. No hardcoded bypass logic found.

---

### 13. Dependency Vulnerability & Open-Source License Review
* **License Compliance**: All direct and transitive dependencies utilize permissive open-source licenses:
  * Apache 2.0: AndroidX, Kotlin Coroutines, Google Play Billing, ML Kit, Coil.
  * MIT: Timber, Kotlin Serialization.
  * **Zero GPL / AGPL copyleft libraries present**.
* **Known CVEs**: No vulnerable versions identified. Dependencies use modern, actively patched releases.

---

### 14. User-Facing Privacy & Data Safety Settings UI
Implemented in `core/ui/src/main/kotlin/.../screens/SettingsScreen.kt`:
1. **100% On-Device Processing Guarantee Card**: Prominent badge explaining zero-cloud architecture.
2. **Privacy Policy Dialog**: Comprehensive modal dialog detailing permissions, zero-cloud processing, and data policies.
3. **Location Tagging Toggle**: Opt-in control for EXIF GPS coordinates.
4. **Anonymous Diagnostics Toggle**: Opt-in control for performance telemetry.
5. **Crash Reporting Toggle**: Opt-in control for diagnostic logs.
6. **Clear Temporary Cache Action**: Live button to wipe all temporary image processing buffers and free device storage.

---

### 15. Verification that Core Photo Bytes Are Never Uploaded
A static codebase audit across all modules confirmed:
* **Zero Networking in Media Modules**: `core:camera`, `core:imaging`, `core:ml`, `core:processing`, and `core:ui` contain **ZERO** imports of `java.net.HttpURLConnection`, `okhttp3.*`, `retrofit2.*`, `ktor.*`, or cloud storage SDKs.
* **Pipeline Verification**:
  $$\text{Sensor / Camera2 API} \longrightarrow \text{Raw Bayer / YUV Buffer} \longrightarrow \text{HDR / AI Processing} \longrightarrow \text{MediaStore Scoped Write}$$
  At no point in this sequence is a network connection instantiated or referenced.

---

## Conclusion & Sign-Off
Phase 21 Privacy & Security Hardening audit is complete and **PASSED**. The application meets the highest standards for user privacy, platform security, and Google Play compliance.
