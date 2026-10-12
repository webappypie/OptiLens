# Consolidated Run — Capture Preview + On-Device Enhancement: Report

| Field | Value |
|---|---|
| **Scope** | Owner's consolidated mission (2026-10-12): camera reliability → capture/preview/save → on-device enhancement → UI → remaining phases → WAPCentral → validation. Cloud AI explicitly deferred. |
| **Date** | 2026-10-12 |
| **Device** | Xiaomi Redmi Note 11 Pro+ 5G (`2201116SI`), Android 13 / API 33, debug build |
| **Result** | ✅ Capture → preview → Enhance → compare → save flow implemented and device-verified. ✅ New on-device enhancement pipeline (tests + real-photo QA). ⏸ Multi-frame HDR/night fusion, WAPCentral ads/notifications SDK work and Cloud AI not implemented (reasons in §5). |

## 1. Implemented

### 1.1 Capture → Preview (device-verified)
- After a single-shot capture (Photo/Pro, Night, Pet, Moon; also Portrait/Food when re-enabled) the preview opens automatically. Burst, Best Shot and Wildlife keep their own alternates sheet and do **not** open it. RAW-only shots are not previewed (JPEG companion is, if saved).
- New setting **Preview After Capture** (default on) for people who shoot in quick succession.
- Preview screen (`PhotoReviewScreen`, redesigned): **Enhance Photo** primary action; **Retake** (deletes the just-captured photo after confirmation, handles Android 11+ consent intent), **Edit Tools**, Close; "Processed on this device · works offline"; **Enhance Photo by AI — Coming soon** shown as a non-interactive row (no button, no network).
- Processing: real progress (analysis, then per-strip completion), **Cancel**, back press cancels; repeated taps ignored; work is cancelled when the screen is closed (ViewModel cleared).
- Result: **Original / Enhanced / Compare** switch; compare slider (handle position bug fixed — it was offset from the centre of the box) and hold-to-compare (was broken: the gesture captured the initial `null` bitmap and never fired after enhancing); factual notes of what changed ("Brightened by 0.6 EV", "Colour cast reduced", "Noise reduced", clipped-highlight warning).
- **Save Enhanced Photo**: always a new file `OptiLens_Enhanced_<ts>.jpg` (labelled *Edited* in the gallery), original untouched; double-save guarded; spinner, success snackbar, error with retry; capture EXIF (time, make/model, exposure, ISO, aperture, focal length, GPS only if present in the original) copied; orientation normalised. Leaving with an unsaved enhancement asks Save / Discard.

### 1.2 On-device enhancement (replaces the previous per-channel LUT + 3×3 filters)
`LocalEnhancementPipeline` (pure Kotlin, multi-core strips, O(width × 70 rows) working memory): white balance (shades-of-grey, partial), guided-filter multi-scale tone mapping (exposure + shadow compression on the base layer, clarity on the mid band), noise-gated chroma and luma denoise, halo-free sharpening, skin-protected vibrance, gamut-safe encode. Details and integrity guards: `docs/04_COMPUTATIONAL_PHOTOGRAPHY.md`. No ML model, no generated content, no network, no ads, no tokens. RenderScript not used; no dependency added.

### 1.3 Other fixes
| Defect | Fix |
|---|---|
| Viewfinder scene pill said "HDR" although no HDR capture exists | Now "High contrast" (descriptive only) |
| Hold-to-compare never worked after enhancing (stale gesture closure) | Gesture keyed on the bitmap/view mode |
| Compare handle drawn away from the divider | Anchored to the container's top-left |
| Enhanced copies named `OptiLens_AI_*` (no AI involved) | `OptiLens_Enhanced_*` |

## 2. Benchmarks (measured)

| Where | Image | Time | Notes |
|---|---|---|---|
| Redmi Note 11 Pro+, **debug** build, 4 workers, `ln/exp` | 2448×3264 | 818 ms analysis + 5626 ms render | first version |
| Redmi Note 11 Pro+, **debug** build, 6 workers, fast log2/exp2 | 2448×3264 | 396 ms + 4386 ms ≈ **4.8 s** | current; previous engine ≈ 7 s (Phase 02 report) |
| Host JVM (dev PC), 4 workers | 3264×2448 | ≈ 0.9–1.4 s | not representative of phones |
| Release (non-debuggable) build on device | — | **not measured** | MIUI blocked installing the new release package over USB (`INSTALL_FAILED_USER_RESTRICTED`, needs on-device confirmation). Debuggable ART is typically markedly slower, so release should be faster — unverified. |

Memory: output bitmap (= input size) + ≈ 32 B × width × 70 rows per worker (≈ 5.5 MB at 2448 px); workers reduced automatically when free heap is low.

Quality QA on five real OptiLens captures (indoor, dim, very noisy near-black frame) with `EnhancementSampleHarness`: natural brightening of under-exposed shots, neutralised colour casts, preserved highlights and blacks, no halos. Limitation seen: on an extremely noisy near-black frame (σ ≈ 0.037), brightening is now deliberately capped (≈ +0.2 EV) because lifting would mostly amplify noise; such frames cannot be made clean from a single JPEG.

## 3. Verification executed

### Automated
See §6 for the final consolidated `assembleDebug testDebugUnitTest lintDebug` result.
New tests: `LocalEnhancementPipelineTest` (14: neutrality, exposure, black/white preservation, cast reduction, noise detection + chroma/luma reduction, clean-photo denoise skip, no halo overshoot, parallel determinism, progress, cancellation, tiny images, guided-filter edge preservation); `PhotoReviewViewModelTest` (+6: double save, failed save + retry, view-mode guard, cancel, retake/delete, factual notes); `CameraViewModelTest` (+2: single shot opens preview once; burst does not).

### Physical device (Redmi Note 11 Pro+, debug build)
| Check | Result |
|---|---|
| Capture → preview opens automatically | ✅ |
| Enhance → progress → Compare view, notes | ✅ (~4.8 s) |
| Compare slider handle on the divider | ✅ (after fix) |
| Save Enhanced Photo → snackbar, "Saved" state | ✅ |
| Saved file: separate, upright 2448×3264, Orientation=1, exposure/time EXIF kept | ✅ (pulled and inspected) |
| No crash during the session (`FATAL`/`AndroidRuntime` in logcat) | ✅ none |

| Retake → confirm → photo deleted from MediaStore, back to camera (final build) | ✅ |

**Not physically verified:** Cancel during processing, backgrounding mid-enhance, leave-without-saving dialog, Light theme of the preview (screen is intentionally dark), Settings toggle, release-build speed, low-RAM devices, the final small UI polish (shorter "Camera" label, divider limited to the image) — built and unit-tested, not re-checked on device.

## 4. WAPCentral / ads audit (no code changes)
- The guide targets Flutter packages (`wap_core_sdk`, `wap_ads_sdk`, …) and AppLovin MAX as sole mediation. This is a native Kotlin app; no Android WAP SDK exists in the repository, the AppLovin SDK key is "Not configured", and the `/ads/config` response schema is not documented. Building a REST adapter would mean guessing the contract, so nothing was added (policy: `docs/06`).
- Existing integration: Google Mobile Ads with **Google's official test IDs** only, one gallery banner, frequency-capped interstitial support, Pro suppression, no ads in viewfinder/near shutter. Capture, enhancement and saving are not gated by any ad. The guide's X-App-Key was **not** added to the app.
- Notifications: not implemented (needs FCM + WAPCentral SDK/contract decision). Open owner decisions unchanged (`docs/status/CURRENT_PHASE.md`).

## 5. Not done in this run (and why)
| Item | Status / reason |
|---|---|
| Enhance Photo by AI (cloud) | Deferred by instruction; design in `docs/05_AI_AND_HYBRID_PROCESSING.md`. WAPCentral `image` module is "Not enabled". |
| Multi-frame HDR / Night fusion in capture (roadmap 05–08) | Not wired. Burst acquisition exists, but fusion (`NativeMultiFrameFusionEngine`) works on planar buffers and burst frames are JPEG; wiring decode + alignment + fusion reliably needs its own device-validated phase. Shipping it unvalidated would risk the capture reliability that ranks first. |
| WAPCentral ads/notifications SDK (10–12), Firebase alignment (13) | Blocked on SDK/contract/key decisions (§4). |
| Billing, release, device matrix (16–19) | Not started; need owner inputs and more devices. |
| Release-build performance | Unmeasured (see §2). |

## 6. Final consolidated validation
`./gradlew assembleDebug testDebugUnitTest lintDebug --continue` → **BUILD SUCCESSFUL**.

| Module | Unit tests | Failures | Skipped | Lint errors |
|---|---|---|---|---|
| app | 7 | 0 | 0 | 0 |
| core/camera | 206 | 0 | 0 | 0 |
| core/common | 27 | 0 | 0 | 0 |
| core/imaging | 118 | 0 | 1 (opt-in sample harness) | 0 |
| core/logging | 16 | 0 | 0 | 0 |
| core/navigation | 7 | 0 | 0 | 0 |
| core/settings | 36 | 0 | 0 | 0 |
| core/ui | 111 | 0 | 0 | 0 |
| **Total** | **528** | **0** | 1 | 0 |

`assembleRelease` also built (R8) but was not installed (see §2). No instrumentation tests were run.

## 7. Known issues found during validation
| Issue | Status |
|---|---|
| After Retake, the camera's gallery thumbnail still showed the deleted photo | **Fixed in run 2** (§8) |
| Scene hint pill labelled a desk scene "Wildlife" | **Fixed in run 2** (§8) |
| Enhancement ≈ 4.8 s on 8 MP in the debug build | **Measured in run 2:** 1.16 s non-debuggable (§8) |

---

# Run 2 — remaining phases (2026-10-13)

Owner instruction: fix the stale thumbnail and the scene label, complete the remaining phases, then do one final real-device pass.

## 8. Implemented in run 2

| Phase | Work | Verification |
|---|---|---|
| Fix | **Stale thumbnail**: the camera cached its last capture in memory and never re-checked it. `CameraController.revalidateLastCapturedPhoto()` queries MediaStore on every camera resume and clears the thumbnail if the photo is gone (Retake, Gallery delete, other apps) | 2 unit tests; device: after Retake the thumbnail returns to the plain gallery icon |
| Fix | **Scene label**: "Wildlife"/"Pet" came from green colour + any frame motion, and "Food" from warm colours. No recognition model exists, so these scene types are no longer inferred, the texture-cluster "wildlife detector" no longer relabels the scene, and pills are shown only for measured signals (faces via ML Kit, low light, backlight/high contrast, hold steady, document, moon). Selecting Pet/Wildlife mode still works | Classifier tests rewritten to assert no guessing; device: same desk scene with a moving hand now shows "High contrast" |
| 05–08 | **Multi-frame merge** (`BurstMerger`, HDR+-style). Reference = sharpest frame (noise-robust, per-axis), coarse-to-fine global alignment, per-tile sub-pixel refinement with a smoothed displacement field, exposure normalisation by **measured** brightness (metadata ISO misses this sensor's post-RAW boost), robust √-domain merge with per-band noise estimation (2σ–4σ ghost rejection), bounded memory (row bands + region decoding) | 6 unit tests: ≥35 % RMSE reduction on 6 shifted noisy frames, no ghosting of a moving object, exposure drift normalised, blurred/different frames rejected, single frame passes through unchanged |
| 08 | **Night** = burst → merge → tone mapping (replaces "save the steadiest frame"). **Moon** = merge without tone mapping. Fallback: if merging fails, runs out of memory, the phone is SEVERE/CRITICAL hot, or the job is cancelled, the steadiest real frame is saved (never a lost shot). UI says "Merging frames…" | Device: 3–4 frames merged, acceptance 0.96, clean brighter result |
| 07 | **HDR toggle returns (Photo mode)**: −0.7 EV burst of 4 frames → merge → tone mapping; exposure restored afterwards | Unit test (fallback path); device: highlights on a laptop screen kept, shadows lifted |
| 10–11 | **WAPCentral-controlled ads**: `WapCentralAdsRepository` reads the live `GET /ads/config` (schema observed from the real endpoint, read-only), in memory only. The gallery banner and the (unused) interstitial take their ad unit, enable flags and kill switch from it. No config / offline / disabled → no ad; nothing is blocked. Key from gitignored `local.properties` → BuildConfig (never committed or logged). Diagnostics screen shows the connection status | 6 unit tests (live-schema parse, auth headers, 403/offline/unconfigured fail closed, key redaction); device: banner served as "Test Ad" using the WAPCentral-supplied unit |
| Bug found on device | First WAPCentral build showed no banner: the HTTP helper's single `read()` returned only the first network chunk, so the JSON was truncated and rejected. The host-JVM tests use an in-memory fake, so they did not catch it | Fixed (read to EOF, size-capped); confirmed on device |
| 17 | Thermal guard for multi-frame processing; measured non-debuggable performance (below) | Device |
| 18 | Privacy text updated: WAPCentral config fetch (app ID only) and the GPS copy rule for enhanced copies | — |

### Non-debuggable performance (same `.debug` package built with `isDebuggable=false` temporarily; the change was reverted and is not committed)
| Operation (2448×3264) | Debug build | Non-debuggable |
|---|---|---|
| Enhance Photo (single image) | 4.8 s | **1.16 s** (210 ms analysis + 952 ms render) |
| Night, 4 frames: align / merge / tone | 14.2 / 10.3 / 10.0 s (3 frames) | **1.7 / 3.0 / 2.3 s** ≈ 7 s |
| HDR, 4 frames: align / merge / tone | — | **1.5 / 2.8 / 1.5 s** ≈ 5.8 s |

## 9. Not done in run 2 (blocked or out of scope, with reasons)
| Phase | Reason |
|---|---|
| 12 Notifications | The feed needs a registered `deviceId`; registration needs an FCM token and its request body is undocumented. Firebase isn't integrated, and `google-services.json` has no client for `com.webappypie.optilens.debug`, so applying the plugin would break debug builds. Owner: register the debug package (or provide a debug config) and the registration contract |
| 13 Firebase | Inventory only: project `optilens-511410`, package `com.webappypie.optilens` (matches the guide); file stays in the root (gitignored). No change until FCM is unblocked |
| Portrait bokeh (08) | Needs segmentation plus a synthetic background blur. That conflicts with the "don't alter backgrounds" integrity rule; it needs an explicit owner decision. Mode stays hidden |
| 09 Editing tools | Navigation from the preview works; the tools are Pro-gated and purchases can't run in debug, so they weren't re-run (Phase 02 verified them) |
| 14 Model governance | No ML models are shipped; the pipelines are documented classic algorithms |
| 15 Cloud AI, 16 Billing, 19 Release, 20 Growth | Deferred by instruction or blocked on owner inputs (product IDs, signing, policy URL, analytics decision) |
| custom_ads house ads | Present in WAPCentral's mediation chain but there is no documented client API for serving creatives; skipped |

## 10. Final validation (run 2)
- `assembleDebug testDebugUnitTest lintDebug` → see §11.
- Device (Redmi Note 11 Pro+, API 33):
  - ✅ startup; ✅ Photo capture → preview
  - ✅ Retake → file deleted, thumbnail cleared; ✅ scene pill honest
  - ✅ Night merge; ✅ HDR merge
  - ✅ Enhance + leave-without-saving dialog
  - ✅ gallery banner from WAPCentral config; ✅ Editing Tools navigation
  - ✅ no crashes
- Not device-verified: Moon merge (needs the moon); thermal fallback; cancellation mid-merge; low-RAM devices; the X-App-Key in the APK is extractable (owner decision).

## 11. Final automated validation (run 2)
`assembleDebug` (earlier in run 2, plus final rebuild) and `testDebugUnitTest lintDebug --continue` → **BUILD SUCCESSFUL**. Unit tests **543, 0 failures** (app 7, camera 206, common 33, imaging 124 incl. 1 opt-in harness skipped, logging 16, navigation 7, settings 36, ui 114). Lint **0 errors** in every module. No instrumentation tests were run.

---

# Run 3 — Night/HDR processing speed (2026-10-13)

## 12. Changes
| Change | Effect |
|---|---|
| Frame statistics (brightness, sharpness) from ¼-scale decodes; each frame decoded **once** at ½ scale for alignment (was twice); frames aligned **in parallel** (was sequential) | Align 1.5–1.7 s → 0.68–0.72 s |
| Merge: one region decoder **per thread** sharing the JPEG bytes (was one shared, copying decoder per frame); a reusable decode target bitmap per thread; displacement field interpolated once per row; bilinear sampling on 8-bit values plus one sRGB→linear lookup per channel (3 lookups instead of 12) | Merge 2.8–3.0 s → 1.7–2.0 s |
| Worker budgets fixed: the old estimate counted a decode bitmap per frame per thread and fell back to **1 worker**; merge and tone mapping are now budgeted separately (up to 6) | 5–6 workers on the 256 MB heap |
| Tone-mapped output written straight into the native-memory output bitmap (no second 32 MB Java array, no copy) | Lower peak heap |
| Luma denoise: 5×5 range-weighted window → local Wiener (Lee) filter on box sums (same edge/texture protection, cost independent of range weighting); visually checked at 1:1 on the noisiest real sample | Tone faster on noisy frames |
| Realtime viewfinder analysis (scene, faces, histogram) paused while a burst is processed; always resumed in `finally` | Tone 2.6 s → 1.2–1.5 s during Night/HDR |

Sharpness is now measured on the ¼-scale plane without extra smoothing; all merger tests (blurred-frame rejection, noisy reference, exposure drift, ghosting) still pass.

## 13. Measured on device (non-debuggable `.debug` package, 2448×3264; the flag change was temporary and is not committed)
| Shot | Before (run 2) | After |
|---|---|---|
| Night, 3 frames | — (debug build only) | **4.1 s** (align 0.68 + merge 1.72 + tone 1.52 + encode 0.16) |
| Night, 4 frames | 7.0 s | **5.0 s** (intermediate step, before the merge-loop changes) |
| HDR, 4 frames | 5.8 s | **4.06 s** (align 0.72 + merge 1.98 + tone 1.21 + encode 0.15) |

Not included: the burst capture itself (frames are taken one after another with standard JPEG captures; its duration isn't in these numbers, and this device suppresses the app's capture-engine log lines). Results checked visually: no ghosting, clean shadows. A bright blob on a laptop screen in the HDR test also appears in a normal photo of the same scene (screen glare, not a processing artefact).

## 14. Validation (run 3)
`assembleDebug testDebugUnitTest lintDebug --continue` → **BUILD SUCCESSFUL**; **543 unit tests, 0 failures**; lint **0 errors**. The normal debuggable build was reinstalled on the device after benchmarking.

---

# Run 4 — burst capture pipelining (2026-10-13) — NOT TESTED

Owner instruction: proceed without testing. Only a compile check (`:core:camera:compileDebugKotlin`) was run; **no unit tests, no lint, no device run**.

| Change | Expected effect | Status |
|---|---|---|
| `Camera2BurstAcquisitionEngine`: when every frame of a burst has the same exposure (Night, HDR), all `takePicture` requests are issued at once (in order, `CoroutineStart.UNDISPATCHED`) and collected in sequence, so CameraX can pipeline captures instead of waiting for each JPEG to be delivered and copied before starting the next. EV-bracketed bursts stay sequential. Frames that finish after a timeout are kept as a partial burst; on failure/cancel they are released to the pool as before. The log line now says `pipelined`/`sequential` | Shorter burst capture (and "hold steady" time) for Night/HDR; size of the gain is unmeasured | **Untested.** To verify: Night/HDR capture on device (frame count, no pool exhaustion, timeout/partial path), plus `testDebugUnitTest` (the fake burst engine doesn't exercise this path) |

Risk: pipelining depends on CameraX queueing several in-flight in-memory captures on this HAL. If a device rejects or drops queued requests, the existing fallback (single frame / partial burst) applies, but this has not been observed.

# Run 5 — processed-capture awareness, HDR setting (2026-10-13) — NOT TESTED

Owner instruction: proceed without testing. Only compile checks (`:core:camera`, `:core:settings`, `:core:imaging`, `:core:ui`, `:app` `compileDebugKotlin`) were run; **no unit tests, no lint, no device run**.

| Change | Why |
|---|---|
| Night/HDR/Moon merges are saved with EXIF Software `OptiLens <Mode> · <n>-frame merge[, tone-mapped]` (`CameraController.saveProcessedJpeg(processingNote)`, `MediaStoreSaver.saveJpeg(software)`; other photos keep "OptiLens Pro Engine 1.0") | Records what was actually done at capture |
| Preview reads it (`PhotoBitmapLoader.readCaptureProcessing`) and shows e.g. "✓ Night · 4-frame merge, tone-mapped on capture" | Honest information; the user knows the shot is already processed |
| Enhance on a tone-mapped capture uses `EnhancementConfig.ALREADY_TONE_MAPPED` (exposure correction 0.3, max lift 0.5 EV, shadow recovery 0.3, clarity 0.2, luma denoise 0.3) | On device, Enhance on a Night shot brightened it a second time (+0.8 EV) |
| HDR toggle persisted (`AppSettings.hdrEnabled`, DataStore key `hdr_enabled`, default off) | The toggle used to reset on every launch |

To verify: take Night/HDR shots → preview shows the note → Enhance changes them only mildly; HDR toggle survives an app restart; run `testDebugUnitTest lintDebug`.

# Run 6 — splash overlaps camera start (2026-10-13) — NOT TESTED

Owner instruction: proceed without testing. Only `:app:compileDebugKotlin` was run; **no unit tests, no lint, no device run**.

| Change | Why |
|---|---|
| When camera permission is already granted (every launch after the first), the app starts directly on the Camera destination and draws the existing splash as an overlay on top (fades out in 200 ms, swallows touches while visible). Without permission, the original Splash → Camera navigation is unchanged, so the system permission dialog never appears over the splash | Previously the camera began binding only after the 0.7 s splash finished; now the preview binds while the splash animates. Expected time-to-camera saving up to ≈0.7 s; **not measured** |

Audit note (no change): `AdProvider`/`GoogleMobileAdProvider` is not injected anywhere, so the Mobile Ads SDK is not initialised at startup and interstitial code is unused; the gallery `AdView` initialises the SDK lazily on first load.

To verify: cold start with permission granted (splash shows, then a live preview with no delay or flicker; taps during splash do nothing); first install without permission (old flow, permission dialog after the splash); rotation/process restore don't show the splash again; `testDebugUnitTest lintDebug`; compare Diagnostics "camera ready" against the Phase 03–04 figures (≈3.6 s).

# Run 7 — actionable viewfinder hints (2026-10-13) — NOT TESTED

Owner instruction: proceed without testing. Only `:core:ui:compileDebugKotlin` was run; **no unit tests, no lint, no device run**.

| Change | Why |
|---|---|
| Hint pills can carry an action. "High contrast"/"Backlight" → **Tap for HDR** (offered only in Photo mode while HDR is off); "Night suggested" → **Tap for Night** (offered when not already in Night). Actionable pills get a stronger outline, a larger touch area, the "· Tap for …" suffix and a Button role/click label for TalkBack. Other hints stay purely informational | The hints were suggestions the user had to act on elsewhere; now they lead straight to the real feature they describe |

To verify: point at a backlit/high-contrast scene in Photo mode with HDR off → pill reads "High contrast · Tap for HDR" → tap turns the HDR button on and the pill returns to plain "High contrast"; in a dark scene "Night suggested · Tap for Night" switches the mode rail to Night; TalkBack announces the action.
