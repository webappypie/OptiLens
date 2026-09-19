# Phase 03 — Camera Capability Discovery Engine: Report

| Field | Value |
|---|---|
| **Phase** | 03 — Camera Capability Discovery Engine |
| **Status** | ✅ PASS |
| **Date** | 2026-09-19 |
| **Prompt** | `prompts/PHASE_03_CAMERA_CAPABILITY_ENGINE.md` |

---

## What Changed

### 1. CameraX 1.6+ & Camera2 Interop Dependencies
- Integrated the stable **CameraX 1.6.2** line into `gradle/libs.versions.toml`:
  - `androidx-camera-core`
  - `androidx-camera-camera2`
  - `androidx-camera-lifecycle`
  - `androidx-camera-view`
  - `androidx-camera-extensions`
- Added Kotlin Serialization (`kotlinx-serialization-json`) and Android Logging (`:core:logging`) dependencies to `:core:camera`.

### 2. Comprehensive Camera Hardware Models (`:core:camera/model`)
Implemented serializable data structures modeling device hardware:
- `CameraHardwareLevel`: Standard Camera2 hardware levels (`LEGACY`, `LIMITED`, `FULL`, `LEVEL_3`, `EXTERNAL`).
- `LensFacing`: Orientation relative to screen (`BACK`, `FRONT`, `EXTERNAL`).
- `SensorArrayInfo`: Active array bounds, pixel array dimensions, physical sensor size in millimeters, sensor orientation, and timestamp source (`REALTIME`/`UNKNOWN`).
- `ResolutionInfo`: Maximum resolutions and full output dimensions for JPEG, RAW_SENSOR, YUV_420_888, PRIVATE streams, and API 31+ ultra-high-resolution streams.
- `ControlCapabilities`: AF/AE/AWB modes, AE exposure compensation range and step, optical and digital zoom range, and physical flash availability.
- `StreamCapabilities`: Support for RAW sensor, burst capture, YUV reprocessing, private reprocessing, manual sensor (ISO/exposure/frame duration ranges), manual post-processing, logical multi-camera arrays, ultra-high-resolution sensors, and 10-bit dynamic range profiles (HLG10, HDR10, HDR10+, Dolby Vision).
- `ExtensionSupport`: CameraX extensions (`BOKEH`, `HDR`, `NIGHT`, `FACE_RETOUCH`, `AUTO`) and Android 15 / API 35+ Low-Light Boost mode.
- `StabilizationSupport`: Optical Image Stabilization (OIS), Electronic Video Stabilization (EIS), and API 33+ preview stabilization.
- `PlatformRawCapabilities`: Support for RAW_SENSOR, RAW10, RAW12, RAW_PRIVATE, and ultra-high-res RAW formats.
- `PhysicalSensorInfo`: Multi-camera constituent physical sub-sensor characteristics (focal length, sensor size, active array).
- `CameraDeviceProfile`: Complete profile for each individual logical and physical camera unit.
- `CameraCapabilityProfile`: Device-level root profile combining all detected cameras, default primary cameras, performance tier, active quirks, and system metrics.
- `CameraCapability.toLegacyCameraCapability()`: Backward-compatible adapter mapping `CameraCapabilityProfile` to the initial `CameraCapability` contract.

### 3. Hardware HAL Discovery Engine (`:core:camera/discovery`)
- `CameraCapabilityDetector` interface and `AndroidCameraCapabilityDetector` implementation:
  - Enumerates all logical camera IDs via `CameraManager.cameraIdList`.
  - Queries `CameraCharacteristics` keys across optics, sensors, stream configuration maps, stabilization, and capabilities arrays.
  - Recursively queries physical sub-cameras on logical multi-camera setups (`CameraCharacteristics.physicalCameraIds`).
  - Queries CameraX `ExtensionsManager` asynchronously to determine vendor extension availability.
  - Queries low-light boost AE mode availability (`CONTROL_AE_MODE_ON_LOW_LIGHT_BOOST_OR_CONTINUOUS`).
  - Safely extracts total system RAM and CPU core counts without blocking the UI thread.

### 4. Deterministic Performance Tier Heuristic (`:core:camera/tier`)
- `PerformanceTierEvaluator`:
  - Computes a deterministic score (0–100) and assigns tiers (`FLAGSHIP`, `HIGH_PERFORMANCE`, `MID_RANGE`, `ENTRY_LEVEL`).
  - Evaluates:
    - Primary camera hardware level (LEVEL_3 = 30 pts, FULL = 20 pts, LIMITED = 8 pts, LEGACY = 0 pts).
    - Camera hardware capabilities (RAW, Burst, YUV/Private Reprocessing, Manual Sensor, Multi-Camera, 10-bit HDR = up to 30 pts).
    - System RAM capacity (12+ GB = 25 pts, 8+ GB = 20 pts, 6+ GB = 15 pts, 4+ GB = 8 pts).
    - CPU cores and modern Android API levels (up to 15 pts).
  - Enforces hard guardrails: Flagship requires LEVEL_3/FULL + RAW + 7+ GB RAM; Legacy hardware or < 3 GB RAM is capped at ENTRY_LEVEL.

### 5. Hardware Quirk Registry (`:core:camera/quirks`)
- `DeviceQuirkRegistry` with targeted workarounds:
  - `SamsungPreviewAspectQuirk`: Viewfinder aspect ratio container locking.
  - `PixelAeConvergenceQuirk`: Exposure settling frames during AE transitions.
  - `MediaTekYuvStrideQuirk`: Row stride byte buffer alignment for image analysis.
  - `LimitedHardwareStreamConstraintQuirk`: Automatic resolution downsampling on LIMITED/LEGACY devices.
  - `HighResolutionCaptureLagQuirk`: UI capture lock feedback for sensors >= 48 MP.
  - `InvertedSensorOrientationQuirk`: Front sensor orientation normalization on foldables/tablets.

### 6. Persistent Capability Cache (`:core:camera/discovery`)
- `CameraCapabilityCache`:
  - Stores non-sensitive capability profiles as JSON in internal app storage.
  - Verifies cache validity against `Build.FINGERPRINT` and `BuildInfo.versionCode`.
  - Automatically invalidates when firmware or app version changes.
- `CameraCapabilityRepository`:
  - Coordinates instant 0ms cached startup with background refresh and emits `StateFlow<CameraCapabilityProfile?>`.

### 7. In-App Diagnostics Screen & Exporters
- `CameraDiagnosticsExporter`: Formats profiles into structured JSON and human-readable Markdown reports.
- `CameraDiagnosticsViewModel` & `CameraDiagnosticsScreen`:
  - Accessible from Settings under **Hardware & Advanced -> Hardware Diagnostics**.
  - Shows Device Overview card with Performance Tier badge, CPU/RAM, and active quirks.
  - Interactive tabs for selecting each enumerated camera unit.
  - Detailed spec cards for optical properties, stream formats, hardware capabilities, and CameraX extensions.
  - Direct "Copy JSON" and "Share Report" actions.

---

## Gate Verification

> **Gate Requirement**: Never infer a capability only from manufacturer/model if Android reports it directly.

**Verification**:
- All capabilities (`supportsRaw`, `supportsHdr`, `opticalImageStabilization`, `supportsYuvReprocessing`, `isLogicalMultiCamera`, etc.) are read directly from `CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES`, `LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION`, and `SCALER_STREAM_CONFIGURATION_MAP`.
- Unit tests (`PerformanceTierEvaluatorTest.gate rule compliance`) verify that device manufacturer/model strings have zero effect on capability reporting or performance scoring.
- The Quirk Registry exclusively deals with known driver HAL bug workarounds, never with capability gating.

---

## Verification Results

| Check | Result |
|---|---|
| Targeted tests: `:core:camera:testDebugUnitTest` | ✅ PASS (24 tests: models, fixtures, tier evaluator, quirks, cache, exporter) |
| Targeted tests: `:core:navigation:testDebugUnitTest` | ✅ PASS (all routes including `CameraDiagnostics`) |
| Targeted tests: `:core:ui:testDebugUnitTest` | ✅ PASS (theme tokens, navigation shell, diagnostics integration) |
| CameraX 1.6.2 dependency resolution | ✅ PASS |
| Kotlin serialization roundtrip | ✅ PASS |
| Cache invalidation on firmware change | ✅ PASS |
| Performance tier heuristic grading | ✅ PASS |
| Gate compliance verification | ✅ PASS |

---

## Physical Device Status
PENDING (No physical device connected; all unit tests, fixtures, and targeted component validations passed cleanly on local toolchain.)

---

## Git Status
Ready to commit as `phase-03: camera capability discovery engine` and push to `origin/main`.
