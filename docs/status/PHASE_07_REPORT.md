# Phase 07 — Burst and Multi-Frame Acquisition: Report

| Field | Value |
|---|---|
| **Phase** | 07 — Burst and Multi-Frame Acquisition |
| **Status** | ✅ PASS |
| **Date** | 2026-09-20 |
| **Prompt** | `prompts/PHASE_07_MULTIFRAME_ACQUISITION.md` |

---

## What Changed

### 1. HAL Buffer Starvation Prevention via BoundedBufferPool
- Android camera HALs typically maintain shallow buffer queues (2 to 4 buffers) for still captures. Holding multiple raw `ImageProxy` references simultaneously exhausts the HAL pipeline and locks up the camera session.
- Implemented [`BoundedBufferPool`](file:///d:/Mobile-App/OptiLens/core/camera/src/main/kotlin/com/webappypie/optilens/core/camera/burst/pool/BoundedBufferPool.kt) and [`PooledBuffer`](file:///d:/Mobile-App/OptiLens/core/camera/src/main/kotlin/com/webappypie/optilens/core/camera/burst/pool/PooledBuffer.kt):
  - Pre-allocates a bounded pool of reusable byte arrays (default 12 buffers max, up to 16MB per frame).
  - Copies incoming raw image bytes immediately upon capture and closes `ImageProxy` inside `finally` without delay.
  - Strict thread-safe tracking of active allocations with zero memory leaks.

### 2. Synchronized Frame Packets & Hardware Metadata Models
- Implemented [`FrameMetadata`](file:///d:/Mobile-App/OptiLens/core/camera/src/main/kotlin/com/webappypie/optilens/core/camera/burst/model/FrameMetadata.kt):
  - Captures hardware-level Camera2 exposure parameters: timestamp (ns), exposure time (ns), ISO sensitivity, focus distance (diopters), lens state, AE state, AWB state, aperture, focal length (mm), and display rotation.
  - Provides formatted photographic exposure fraction helpers (e.g. `1/1000s`, `1/60s`, `2.0s`).
- Implemented [`GyroSample`](file:///d:/Mobile-App/OptiLens/core/camera/src/main/kotlin/com/webappypie/optilens/core/camera/burst/model/GyroWindow.kt) and [`GyroWindow`](file:///d:/Mobile-App/OptiLens/core/camera/src/main/kotlin/com/webappypie/optilens/core/camera/burst/model/GyroWindow.kt):
  - Records continuous physical gyroscope angular velocity readings across the exact frame exposure window.
  - Computes `meanAngularSpeed`, `maxAngularSpeed`, and optical stability indicator (`isStable`) for downstream frame scoring and alignment.
- Implemented [`FramePacket`](file:///d:/Mobile-App/OptiLens/core/camera/src/main/kotlin/com/webappypie/optilens/core/camera/burst/model/FramePacket.kt):
  - Couples image buffer bytes with optical `FrameMetadata` and physical `GyroWindow`.
  - Implements `AutoCloseable` to return buffers to the pool once frame processing completes.
- Implemented [`BurstDiagnostics`](file:///d:/Mobile-App/OptiLens/core/camera/src/main/kotlin/com/webappypie/optilens/core/camera/burst/model/BurstDiagnostics.kt) & [`BurstResult`](file:///d:/Mobile-App/OptiLens/core/camera/src/main/kotlin/com/webappypie/optilens/core/camera/burst/model/BurstResult.kt):
  - Tracks inter-frame intervals, total burst duration, average latency, and dropped frames.
  - Cascades `AutoCloseable` closure across all constituent frame packets.

### 3. Production Burst Acquisition Engine (`Camera2BurstAcquisitionEngine`)
- Implemented [`Camera2BurstAcquisitionEngine`](file:///d:/Mobile-App/OptiLens/core/camera/src/main/kotlin/com/webappypie/optilens/core/camera/burst/Camera2BurstAcquisitionEngine.kt):
  - Uses `ImageCapture` and Camera2 interop to execute sequential exposures with dynamic EV offsets (e.g. bracketed HDR `[-2, 0, +2]`).
  - Samples continuous gyro motion from `GyroMotionTracker` throughout the exposure sequence.
  - Extracts hardware capture metadata via safe reflection fallback.
  - Guarantees cooperative coroutine cancellation and `withTimeout` protection.
  - Implements automatic graceful fallback to single-frame capture if hardware or session issues occur.
- Implemented [`FakeBurstAcquisitionEngine`](file:///d:/Mobile-App/OptiLens/core/camera/src/main/kotlin/com/webappypie/optilens/core/camera/burst/FakeBurstAcquisitionEngine.kt) for deterministic testing.

### 4. Burst Metadata Inspector
- Implemented [`BurstMetadataInspector`](file:///d:/Mobile-App/OptiLens/core/camera/src/main/kotlin/com/webappypie/optilens/core/camera/burst/BurstMetadataInspector.kt):
  - Evaluates multi-frame burst sequences for exposure consistency, ISO range, bracket spread, and gyro motion blur risk.
  - Generates comprehensive diagnostic summaries and human-readable diagnostic logs for sequence inspection.

### 5. Controller & ViewModel Integration
- Updated [`CameraController`](file:///d:/Mobile-App/OptiLens/core/camera/src/main/kotlin/com/webappypie/optilens/core/camera/CameraController.kt):
  - Added `lastBurstResult: Flow<BurstResult?>`.
  - Added `suspend fun acquireBurst(...)`.
- Updated [`CameraXController`](file:///d:/Mobile-App/OptiLens/core/camera/src/main/kotlin/com/webappypie/optilens/core/camera/CameraXController.kt):
  - Integrates `Camera2BurstAcquisitionEngine`, wired to background capture executor and gyro motion tracker.
  - Closes prior burst buffers to maintain zero active leaks.
- Updated [`FakeCameraController`](file:///d:/Mobile-App/OptiLens/core/camera/src/main/kotlin/com/webappypie/optilens/core/camera/FakeCameraController.kt) with fake burst simulation.
- Updated [`CameraViewModel`](file:///d:/Mobile-App/OptiLens/core/ui/src/main/kotlin/com/webappypie/optilens/core/ui/camera/CameraViewModel.kt):
  - Observes `lastBurstResult` and updates `CameraUiState`.
  - Exposes `takeBurstPhoto(frameCount, targetRotation)` which feeds dynamic recommendations from `CaptureStrategyEngine`.

---

## Verification Results

| Verification Item | Result | Notes |
|---|---|---|
| Buffer pool allocation & recycling | PASS | Tested in `BoundedBufferPoolTest` |
| Buffer capacity bounding & thread safety | PASS | Multi-thread concurrent acquire/release tested |
| FramePacket metadata & gyro binding | PASS | Tested in `FramePacketTest` |
| Cascading AutoCloseable cleanup | PASS | Verified `BurstResult.close()` closes all packets |
| Dynamic frame count & EV bracket sequencing | PASS | Tested in `BurstAcquisitionEngineTest` |
| Single-frame fallback degradation | PASS | Verified graceful fallback on hardware failure |
| Burst metadata inspector diagnostics | PASS | Tested in `BurstMetadataInspectorTest` |
| Burst stress test (5x 8 frames, zero leaks) | PASS | Verified `pool.activeAllocations == 0` in `BurstStressTest` |
| CameraController acquireBurst | PASS | Verified in `CameraControllerTest` |
| ViewModel takeBurstPhoto & state flow | PASS | Verified in `CameraViewModelTest` |
| `:core:camera:testDebugUnitTest` | PASS | 80 unit tests passing |
| `:core:ui:testDebugUnitTest` | PASS | 23 unit tests passing |
| **Total Test Suite** | **PASS** | **103 total unit tests passing** |

---

## Gate & Hardware Status
- **Physical Device Gate**: Status remains `PENDING` (tested via automated unit/Robolectric test suites per master instructions).
