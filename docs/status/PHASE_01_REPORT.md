# Phase 01 — Crash and Capture Reliability: Report

| Field | Value |
|---|---|
| **Phase** | 01 — Crash and Capture Reliability (`docs/15_ROADMAP.md`) |
| **Prompt** | `prompts/PHASE_01_CRASH_CAPTURE_RELIABILITY.md` |
| **Date** | 2026-10-12 |
| **Result** | ✅ Root-caused and fixed a recurring native crash; every capture mode now persists a real photo; build OK, 473/473 unit tests pass, lint 0 errors; validated on a physical device (details and pending items below) |
| **Device** | Xiaomi Redmi Note 11 Pro+ 5G (`2201116SI`), Android 13 / API 33, arm64-v8a |

Prior commit `01f4f4d` snapshots, unmodified, the code that was uncommitted before this roadmap, so this phase's changes are reviewable on their own.

## 1. Defects fixed (root causes)

| # | Severity | Defect | Root cause | Fix |
|---|---|---|---|---|
| 1 | **Critical** | App killed by **native crash (SIGSEGV)** 25–55 s after opening the camera (2 occurrences on device: 16:52:34 and 17:22:41) | `RealtimeIntelligenceAnalyzer.analyze()` handed the `ImageProxy` to an async coroutine for ML Kit face detection, then closed it immediately in `finally`. ML Kit read the freed media image → `Image_getFormat` null deref in `libmedia_jni.so` (tombstone from dropbox `data_app_native_crash`). | Frame ownership moves to the detection coroutine (`CoroutineStart.ATOMIC`, detection in `NonCancellable`), closed exactly once after ML Kit finishes; throttled frames still close immediately. |
| 2 | High | Night mode (non-vendor path) never saved a photo | Burst acquired and discarded; 300 ms fake "processing" state | Saves the steadiest base-exposure frame (`BurstFrameSelector`), shows processing only while really saving. Multi-frame fusion is **not** wired yet (Phases 05–08). |
| 3 | High | Night burst on device returned an error and saved nothing | Per-frame capture takes ~1.3–1.5 s on this device; the 6 s `withTimeout` throws `TimeoutCancellationException` (a `CancellationException`), which the engine treated as user cancellation and discarded all captured frames | Timeout now returns the frames already captured as a partial burst (`droppedFramesCount` recorded); true cancellation still releases everything. |
| 4 | High | Pooled buffer leak after a burst timeout/cancel | The in-flight frame resumed a cancelled continuation and its pooled buffer was never released | `resume(packet) { … unreceived.close() }` returns it to the pool. |
| 5 | High | Best Shot and Wildlife never saved any frame | Sheet "Keep Frame #N" only dismissed the sheet | "Keep" saves the selected (recommended or overridden) frame; ✕ explicitly discards. |
| 6 | High | Moon mode saved nothing and ran garbage processing | JPEG-compressed burst bytes were fed to a stacker expecting luma planes | Saves the steadiest real frame; lunar stacking deferred until decoded frames exist. |
| 7 | High | Photos saved sideways when the phone is held in landscape | Every shutter path passed `Surface.ROTATION_0` | `rememberCaptureRotation()` (OrientationEventListener → CameraX mapping) used by the shutter and volume keys. |
| 8 | High | Front-camera "mirrored" selfies flipped upside down for 90/270° sensors | Mirror applied on the buffer's horizontal axis before EXIF rotation | `mirrorScaleForOrientation()` flips in display space; EXIF orientation always written. |
| 9 | Medium | RAW/RAW+JPEG orientation wrong | `Surface.ROTATION_*` constant passed as degrees and overwrote CameraX's EXIF | Orientation read from the JPEG/DNG EXIF CameraX wrote. |
| 10 | Medium | Manual shutter/EV from Pet, Moon, Wildlife leaked into later shots (and Pro HUD) | Per-shot overrides never reset | Restored to auto / previous EV in `finally`; burst EV bracketing restored on every exit path, including before the single-frame fallback. |
| 11 | Medium | Legacy action bar drawn over the viewfinder; top controls under status bar | `installSplashScreen()` called after `super.onCreate()` | Called before `super.onCreate()`. Verified on device. |
| 12 | Medium | Permission state written during composition; granting in system Settings did not restore the camera without restart | `onPermissionResult()` called in composition body | `LifecycleEventEffect(ON_RESUME)` re-check. Verified on device (revoke → deny → grant → resume). |
| 13 | Medium | Video mode was a dead control (shutter took a still) | No video pipeline | Hidden via `CameraViewModel.IMPLEMENTED_MODES`. |
| 14 | Medium | Volume-key shutter ignored Moon/Wildlife and orientation | Separate, divergent dispatch | Single `dispatchShutter` for button and volume keys. |
| 15 | High | Android 8.0/8.1 crash risk in capability discovery | `physicalCameraIds` (API 28) without guard (minSdk 26) | `SDK_INT >= P` guard. Not device-verified (no API 26/27 device). |
| 16 | — | Fake post-capture delays (Portrait 200 ms, Pet/Food 150 ms, Document 200 ms, Night 300 ms) | Simulated "processing" | Removed; indicators reflect real work only. |
| 17 | — | `lintDebug` failed (45 + 1 + 2 errors) | Missing CameraX opt-ins, restricted `MeteringPoint.getX/Y`, `NewApi`, library manifest missing INTERNET, unescaped `%` | Opt-in annotations, removed restricted log, guard, `core:ui` manifest permission, `formatted="false"`. |
| 18 | — | Navigation unit tests did not compile | `AppDestination.AiTools` became a data class | Test updated + new photo-URI test. |

## 2. Verification executed

### Build / unit tests / lint (`./gradlew assembleDebug testDebugUnitTest lintDebug --continue`)
| Module | Tests | Failures |
|---|---|---|
| app | 7 | 0 |
| core:camera | 194 | 0 |
| core:common | 27 | 0 |
| core:imaging | 97 | 0 (see note) |
| core:logging | 16 | 0 |
| core:navigation | 7 | 0 |
| core:settings | 36 | 0 |
| core:ui | 89 | 0 |
| **Total** | **473** | **0** |

Lint: **0 errors in every module** (was 48). Warnings unchanged in nature (app 78, camera 22, ui 13, imaging 12).

Note: in the final combined run, `ProductionImagingPipelinePortraitTest` hit `runTest`'s 60 s timeout (80 s under parallel lint/build load). Re-run in isolation (`:core:imaging:testDebugUnitTest --rerun`): 97/97 pass, that test 8.8 s. No imaging code changed in this phase; recorded as a load-sensitive test for the imaging phases.

### New regression tests
- `RealtimeIntelligenceAnalyzerImageLifecycleTest` (3) — **confirmed failing against the pre-fix analyzer** and passing after the fix.
- `BurstFrameSelectorTest` (5), `MediaStoreOrientationTest` (3), `CaptureRotationTest` (6).
- `CameraViewModelCaptureReliabilityTest` (8): Night saves one base-exposure frame; failed burst reports an error and saves nothing; Best Shot keep / override / discard; Pet restores shutter; Moon saves and restores EV; Video not offered.
- `NavigationManagerTest` (+1). Two existing tests now use simulated camera latency in the fakes instead of relying on the removed app-side delays.

### Physical device (Redmi Note 11 Pro+, API 33)
| Check | Result |
|---|---|
| Install + cold start | ✅ 2.1–2.9 s |
| No action bar; controls below status bar | ✅ screenshot |
| **Soak on camera screen, 3 min** | ✅ same PID throughout; no new native crash (pre-fix: crashed at 25 s and 53 s) |
| Photo capture → MediaStore (`DCIM/OptiLens`) | ✅ new entry |
| Landscape capture orientation | ✅ phone propped in landscape (accelerometer −6.96, −0.30, 6.84); saved image upright (visually inspected). Old `ROTATION_0` path would have saved it sideways. |
| Night capture | ✅ saved; log: "Burst timed out after 3/10 frames in 6001ms; returning partial burst" |
| Best Shot → Keep | ✅ +1 photo; → ✕ Discard: ✅ no photo |
| Background (Home) → resume → capture | ✅ same process, preview live, +1 photo |
| Permission revoked → system prompt → deny → in-app permission screen | ✅ |
| Permission granted externally → resume | ✅ camera restored without restart |
| Front camera capture | ✅ saved image matches on-screen preview handedness (correlation 0.96 vs −0.56 upside-down) |

**Pending / not physically validated:** portrait-held capture orientation (phone was propped in landscape; mapping unit-tested), physical rotation/configuration change, storage-full failure, camera-unavailable (another app holding the camera), RAW/DNG capture, Pet/Food/Document/Moon/Wildlife captures on device, Android 8.x devices.

Test photos created on the device (owner may delete): `OptiLens_20261012_173144_615.jpg`, `…173916_478.jpg`, `…174004_957.jpg`, `…174107_835.jpg`, `…174221_863.jpg` in `DCIM/OptiLens`.

## 3. Correction to Phase 00

Phase 00 reported "no crash after launch" based on a 6-second observation. The device dropbox shows that session crashed natively 53 s after launch (16:52:34). That was defect #1 above, now fixed.

## 4. Remaining issues found (out of Phase 01 scope)

| Issue | Proposed phase |
|---|---|
| Burst capture is ~1.3–1.5 s per frame (full-quality still + AE precapture per frame); Night gets ~3/10 frames | 06 (multi-frame capture) |
| Multi-frame fusion / HDR / portrait bokeh / SR engines still not connected to capture | 05–08 |
| HDR toggle in top bar is local UI state only (dead control) | 04 |
| Zoom shows only `1x` on this device; hardcoded fallback stops label 0.6x/5x as optical | 04 |
| "AI RECOMMENDED" label on heuristic Best Shot scoring; "Neural" labels; fake cloud path | 02 |
| Permission screen title nearly invisible (dark text on black) | 02 |
| AI Tools opened from camera: no source picker | 02 |
| Food WB value computed but not applied; Document quad is default | 04 / 09 |

## 5. Files changed

`MainActivity.kt`; `CameraController.kt`, `CameraXController.kt`, `FakeCameraController.kt`, `RealtimeIntelligenceAnalyzer.kt`, `MlKitFaceDetector.kt`, `CameraCapabilityDetector.kt`, `Camera2BurstAcquisitionEngine.kt`, `FakeBurstAcquisitionEngine.kt`, `BurstFrameSelector.kt` (new), `MediaStoreSaver.kt`; `CameraScreen.kt`, `CameraViewModel.kt`, `BestShotAlternatesSheet.kt`, `sensor/CaptureRotation.kt` (new), `core/ui/AndroidManifest.xml`; `strings.xml` (6 locales); tests listed above; `docs/status/*`.
