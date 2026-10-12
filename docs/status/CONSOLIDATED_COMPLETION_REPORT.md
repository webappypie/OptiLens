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

## 7. Known issues found during validation (not fixed)
| Issue | Severity |
|---|---|
| After Retake, the camera's gallery thumbnail still shows the deleted photo (stale `lastCapturedPhoto`); tapping it opens "couldn't be opened" | Low |
| Scene hint pill labelled a desk scene "Wildlife" (heuristic classifier) | Low — consider hiding low-confidence scene pills |
| Enhancement ≈ 4.8 s on 8 MP in the debug build; release unmeasured | Medium — native/NEON or GPU path is the next optimisation if release is still slow |
