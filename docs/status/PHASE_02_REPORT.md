# Phase 02 — AI Tools and UI/UX Reliability: Report

| Field | Value |
|---|---|
| **Phase** | 02 — AI Tools / navigation / UI-state reliability (`docs/15_ROADMAP.md`) |
| **Prompt** | `prompts/PHASE_02_AI_TOOLS_UI_UX.md` |
| **Date** | 2026-10-12 |
| **Result** | ✅ Editing tools work end-to-end from a real source; all fake AI/cloud/download/pricing behaviour removed; dead controls fixed or hidden; build OK, unit tests pass, lint 0 errors; validated on device (details below) |
| **Device** | Xiaomi Redmi Note 11 Pro+ 5G (`2201116SI`), Android 13 / API 33 |

## 1. Tool inventory (classification)

| Tool | Implementation | Status after Phase 02 |
|---|---|---|
| Deblur | Directional Richardson–Lucy deconvolution + shock filter (Kotlin) | **Real, device-verified** (~4.5 s on 3264×2448) |
| Object Cleanup | Fast-marching (Telea) inpainting | Real; unit-tested; not exercised on device |
| Reflection Cleaner | Low-frequency veil subtraction | Real; unit-tested; not exercised on device |
| Upscale (2×/4×) | Edge-directed interpolation (no detail synthesis) | Real for small images; now **memory-guarded** (≤24 MP output); guard device-verified |
| Photo Restore | Scratch repair + colour-cast/contrast correction | Real; unit-tested; not exercised on device |
| Enhance (Photo Review) | `OnDeviceEnhancementEngine` tone/colour/detail | **Real, device-verified** (~7 s on 3264×2448) |
| "Cloud AI" | Was a local resize/no-op labelled cloud | **Removed** (WAPCentral `image` module not enabled; Phase 15) |

None of the tools uses a trained ML model. Names, descriptions, stage labels and the model registry now say so.

## 2. Defects fixed

| # | Severity | Defect | Fix |
|---|---|---|---|
| 1 | High | AI Tools from the camera showed an endless spinner (no source) | System Photo Picker (no permission), empty/loading/error states, "change photo" action |
| 2 | High | Fake "Cloud AI" (local resize or unchanged image, quota consumed, "☁️ Cloud AI Enhanced") | `core/imaging/.../cloud/` deleted; Review uses the real on-device engine only |
| 3 | High | Imaging pipeline `AI_ENHANCE` processed a synthetic flat buffer and reported success | Returns an error when no engine/undecodable input; output written as a new `_enhanced.jpg` (could previously overwrite non-`.jpg` input); no false `isHdrApplied` |
| 4 | High | Shared photo loader ignored EXIF orientation → portrait shots opened and were **saved sideways** | Loader applies EXIF orientation (incl. mirrored variants) |
| 5 | High | Loader decoded at full resolution with no cap (OOM risk on 50–108 MP); Upscale 4× of a 12 MP photo ≈ 768 MB | Bounded decode (long edge ≤ 4096 px); `UpscaleBudget` (≤ 24 MP output) enforced in coordinator + UI; `OutOfMemoryError` reported, not crashed |
| 6 | High | Invented prices/catalogue ($29.99/yr, $69.99, $4.99/mo, $14.99, $9.99) shown whenever Play data was missing; Play results were never stored, so the invented catalogue was **always** shown | Only Play-returned products/prices; honest "Purchases aren't available" state; products published to the repository; `FakeBillingManager` moved to test sources |
| 7 | High | Paywall advertised features that do not exist (30× AI super-resolution zoom, relighting/bokeh, astro synthesis) and a hard-coded "7-day free trial" | Lists only what Pro unlocks (editing tools, no ads); trial text only if Play reports a trial |
| 8 | Medium | Simulated model download (`delay` loop, `/tmp` file) and placeholder checksums/licences/benchmarks in the registry | Download fails explicitly for non-bundled models; registry marks every tool `EMBEDDED_ALGORITHM` with null checksum/benchmark and an honest source/licence note |
| 9 | Medium | Errors invisible once a photo was loaded (AI Tools and Review) | Dismissible error banner / inline error |
| 10 | Medium | After one save, further edits could not be saved; Save enabled before any edit | `canSave` = edited ∧ not saved; saved state reset on apply/undo/redo/revert |
| 11 | Medium | Activity recreation could replace a picked photo with the navigation argument | `loadInitialPhoto` loads the nav argument once |
| 12 | Medium | Dead Settings controls: Location tagging, Anonymous diagnostics, Crash reporting (nothing implemented/sent); Shutter sound & Haptic toggles unused; Colour profile & Save-original had no effect | Location/diagnostics/crash toggles removed; **shutter sound (MediaActionSound) and haptic now wired**; colour-profile/save-original hidden until capture processing exists |
| 13 | Medium | Permission screen title invisible in Light theme (dark text on black window) | Component paints its themed background (device-verified) |
| 14 | Low | Misleading AI wording: "AI Zoom" on digital crop, "AI RECOMMENDED", "AI" gallery badge, "Neural Enhanced", "AI Transparency Notice" | Honest labels ("Digital zoom", "RECOMMENDED", "Edited", "Enhanced on device", "Reconstructed content") |
| 15 | Low | Privacy dialog claimed working location tagging and telemetry transmission; did not mention ads SDK | Rewritten factually (no location, no analytics sent, Google Mobile Ads banner in gallery) |
| 16 | Low | Dead "Privacy & Terms" link on paywall | Removed (no policy URL yet — owner input) |
| 17 | Low | Splash held ~1.3 s of pure delay with an emoji logo | ~0.7 s, drawn aperture mark; no progress indicator |
| 18 | Low | Before/After badges overlapped status bar; edit actions shown with no photo; cache clearing did file I/O on the main thread | Fixed |
| 19 | — | Loader bug introduced and caught during this phase's device test (bounds-decode returned null → every photo "couldn't be opened") | Fixed before commit; device-verified |

## 3. Verification executed

### Build / unit tests / lint (`./gradlew assembleDebug testDebugUnitTest lintDebug --continue`)
Final run: `BUILD SUCCESSFUL`. Unit tests **493 / 0 failures** (app 7, camera 194, common 27, imaging 103, logging 16, navigation 7, settings 36, ui 103). Lint **0 errors** in every module (warnings: app 78, camera 22, imaging 9, ui 15 — the 2 new ui warnings recommend `androidx.exifinterface`, consistent with existing platform `ExifInterface` use; no dependency added).

New/updated tests:
- `PhotoDecodeMathTest` (5), `UpscaleBudgetTest` (4), `ProductionImagingPipelineAiEnhanceTest` (rewritten, 2: no fake success).
- `AiToolsViewModelTest` (+9): save rules, fresh session on new photo, nav argument does not override picked photo, load-failure message, upscale budget refusal, factor validation.
- `ProUpgradeViewModelTest` (+1): no invented products when Play returns none; existing tests now wait for real (fake-billing) products.
- `AiToolsCoreTest`: registry/download tests now assert honest metadata and no simulated progress.

### Physical device (Redmi Note 11 Pro+, API 33)
| Check | Result |
|---|---|
| Splash → camera | ✅ aperture mark + wordmark; direct to camera |
| Editing Tools opened from camera without photo | ✅ "Choose a photo to edit" + button (was endless spinner) |
| Photo Picker → load | ✅ (after fixing the loader regression found here) |
| Deblur apply → Undo/Save enabled | ✅ ~4.5 s |
| Save Copy | ✅ new `OptiLens_AI_*.jpg` 3264×2448, upright, original untouched |
| Upscale on 8 MP photo | ✅ 2×/4× disabled with explanation |
| Photo Review: no Cloud AI button (even as Pro); Enhance | ✅ ~7 s; "Enhanced on device • Drag to compare" |
| Gallery labels | ✅ "Edited" badge/filter |
| Settings (Light & Dark) | ✅ dead toggles gone; privacy text factual |
| Upgrade screen, debug build (no Play products) | ✅ "Purchases aren't available right now", no prices |
| Permission screen, Light theme | ✅ legible |
| Capture with shutter sound + haptic on | ✅ saved, no crash (audibility not verifiable via adb) |
| Native crashes since Phase 01 fix | ✅ none in device dropbox |

**Device test method for gated tools:** the debug build trusts entitlements stored locally in DataStore. To exercise the tools, a `{"isPro":true}` entry was written into the **debug app's own** DataStore via `run-as`, and the original file was restored afterwards (byte-identical, verified with `cmp`). No app code was added for this. This also shows entitlements are not server-verified — see §4.

**Not validated on device:** Object Cleanup, Reflection Cleaner, Photo Restore, Upscale on a small image, font scale / small-screen layouts (changing system font/size settings on the owner's phone was not done), share sheet.

## 4. Remaining issues (out of Phase 02 scope)

| Issue | Proposed phase |
|---|---|
| Entitlements trusted from local DataStore (no purchase verification) | 16 (billing) |
| Edited copies drop all EXIF (date taken, camera) | 09 / 14 |
| Blur classifier reported "Sharp / In Focus" on a clearly defocused photo; scene hint showed "Wildlife" for a keyboard | 09 / 06 |
| Deblur effect weak on strong defocus; enhancement ~7 s (Kotlin per-pixel) | 09 / 17 |
| HDR toggle dead; zoom stops; Food WB unused (from Phase 01) | 04 |
| Monetization model, product IDs, privacy-policy URL | Owner decision |

## 5. Files changed (high level)

Imaging: `cloud/` (deleted), `ProductionImagingPipeline.kt`, `AiToolType.kt`, `AiToolModels.kt` (+`UpscaleBudget`), `AiToolsCoordinator.kt`, `AiModelRegistry.kt`, `ModelDownloadManager.kt`, `PhotoRestorationEngine.kt`.
UI: `PhotoBitmapLoader.kt`, `AiToolsViewModel.kt`, `aitools/AiToolsScreen.kt`, `AiFidelityDisclosureBanner.kt`, `PhotoReviewViewModel.kt`, `PhotoReviewScreen.kt`, `BeforeAfterSplitViewer.kt`, `ProUpgradeViewModel.kt`, `ProUpgradeScreen.kt`, `BillingManager.kt`, `SettingsScreen.kt`, `SplashScreen.kt`, `OptiPermissionState.kt`, `CameraScreen.kt`, `BestShotAlternatesSheet.kt`, gallery files.
Common/settings: `EntitlementModels.kt`, `EntitlementRepository.kt`, `EntitlementRepositoryImpl.kt`. Camera: `CaptureStrategy.kt` label. App: `MainActivity.kt`.
Tests: listed in §3 (+ `FakeBillingManager` moved to `core/ui/src/test`).
