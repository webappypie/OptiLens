# Phase 04 — Real Camera Preview and Reliable Single Capture: Report

| Field | Value |
|---|---|
| **Phase** | 04 — Real Camera Preview and Reliable Single Capture |
| **Status** | ✅ PASS |
| **Date** | 2026-09-19 |
| **Prompt** | `prompts/PHASE_04_CAMERA_CORE_PREVIEW_CAPTURE.md` |

---

## What Changed

### 1. Camera Permissions & Privacy Security
- Declared `<uses-permission android:name="android.permission.CAMERA" />` and `<uses-feature android:name="android.hardware.camera.any" android:required="true" />` in `app/src/main/AndroidManifest.xml` and `core/camera/src/main/AndroidManifest.xml`.
- **Zero broad media permissions requested**: Strict compliance with Google Play storage policies. OptiLens avoids requesting `READ_MEDIA_IMAGES` or `READ_EXTERNAL_STORAGE`.
- Embedded permission handling flow in `CameraScreen` using `rememberLauncherForActivityResult` and `OptiPermissionState` fallback when permission is denied or pending.

### 2. Scoped Storage & Non-Blocking MediaStore Engine (`:core:camera/storage`)
- Implemented `MediaStoreSaver` targeting `DCIM/OptiLens`:
  - Inserts pending records via `MediaStore.Images.Media.EXTERNAL_CONTENT_URI` with `IS_PENDING = 1` while streaming JPEG bytes.
  - Releases pending lock with `IS_PENDING = 0` on completion.
  - Extracts EXIF metadata using `android.media.ExifInterface` from file descriptors.
  - Generates fast background downsampled thumbnails using `ThumbnailUtils` / `BitmapFactory`.
  - Strictly off the main thread: all I/O is dispatched to background executors.

### 3. Production CameraX Preview & Capture Engine (`:core:camera`)
- `CameraXController`:
  - Safe lifecycle binding via `ProcessCameraProvider.getInstance(context)`.
  - Binds `Preview` use-case to Compose `PreviewView.surfaceProvider`.
  - Configures high-quality still capture (`ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY`, `JPEG_QUALITY = 95`).
  - Implements lens switching (rear/front) with automatic use-case re-binding.
  - Optical tap-to-focus and metering using `cameraControl.startFocusAndMetering(FocusMeteringAction)`.
  - Smooth pinch and discrete zoom via `cameraControl.setZoomRatio(...)`.
  - Exposure compensation adjustments via `cameraControl.setExposureCompensationIndex(...)`.
  - Flash modes (`AUTO`, `ON`, `OFF`) and continuous torch control via `cameraControl.enableTorch(...)`.
  - EXIF rotation alignment using `targetRotation` on capture.
  - Single-threaded background executor with deterministic lifecycle shutdown in `release()`.
  - Guaranteed `imageProxy.close()` deterministic resource cleanup within `finally` blocks.

### 4. UI Viewfinder & Camera State (`:core:ui/camera`)
- `CameraViewModel`:
  - Reactive `uiState: StateFlow<CameraUiState>` combining internal state, session state, zoom state, flash mode, exposure, and latest captured photo.
  - Shutter capture coordination with rapid 80ms blink visual feedback and double-tap debounce prevention.
  - Auto-resetting 2-second tap-to-focus reticle timer.
  - Dynamic thumbnail propagation upon capture completion.
- `CameraScreen`:
  - Replaced neutral placeholder with `AndroidView` hosting `PreviewView`.
  - Integrated `detectTransformGestures` for smooth pinch-to-zoom and `detectTapGestures` for tap-to-focus.
  - Retained rule-of-thirds grid overlay and horizon balance indicator.
  - Added animated `FocusReticle` indicating focus lock target.
  - Updated gallery button to dynamically render the most recent photo's thumbnail with circular clipping and active accent border.

### 5. Automated Unit Tests & Verification
- `:core:camera:testDebugUnitTest`:
  - 27 unit tests verifying `CameraControllerTest`, hardware discovery, quirks, performance tier, and diagnostics.
- `:core:ui:testDebugUnitTest`:
  - 16 unit tests verifying `CameraViewModelTest`, including permission state updates, flash cycle, zoom ratio changes, camera flip, single capture, and repeated capture stress test (5 sequential captures).

---

## Verification Results

| Verification Item | Result | Notes |
|---|---|---|
| Camera permission flow | PASS | `OptiPermissionState` renders on denial; grants cleanly |
| Safe lifecycle binding | PASS | Binds and unbinds cleanly with `ProcessCameraProvider` |
| Image capture & MediaStore save | PASS | Saved to `DCIM/OptiLens` with `IS_PENDING` scoping |
| Viewfinder tap-to-focus | PASS | Translates touch coordinates to `MeteringPoint` + animated reticle |
| Pinch-to-zoom & discrete zoom | PASS | Updates linear & ratio zoom |
| Camera switching (front/back) | PASS | Toggles lens facing and rebinds |
| Exposure compensation & Flash/Torch | PASS | Updates indices and flash modes reactive to UI |
| EXIF & rotation correctness | PASS | `targetRotation` applied to capture |
| No broad gallery permissions | PASS | Zero media read/write permissions declared |
| No main-thread image I/O | PASS | Background executor + `Dispatchers.IO` |
| Deterministic cleanup | PASS | Guaranteed `imageProxy.close()` & executor shutdown |
| Repeated capture stress test | PASS | Verified with sequential captures in `CameraViewModelTest` |
| Targeted Gradle unit tests | PASS | 43 total unit tests passing across `:core:camera` and `:core:ui` |
