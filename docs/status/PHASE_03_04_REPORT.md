# Phases 03 + 04 — Startup polish and Capability / Lens / Zoom / Control Audit: Report

| Field | Value |
|---|---|
| **Phases** | 03 (splash, permission, startup) merged with 04 (capability/lens/zoom/control audit), as recommended in the Phase 02 report — most of Phase 03 was already delivered in Phases 01–02 |
| **Prompt** | `prompts/PHASE_TEMPLATE.md` (no dedicated prompt files exist for 03/04) |
| **Date** | 2026-10-12 |
| **Result** | ✅ Capability discovery now actually runs; truthful zoom stops; real 16:9 capture; manual/scene exposure fixed (no more near-black frames); non-functional modes and toggles hidden; real startup metrics. Details and test counts below. |
| **Device** | Xiaomi Redmi Note 11 Pro+ 5G (`2201116SI`), Android 13 / API 33 |

## 1. Hardware facts established on device

| Camera ID | Facing | Focal / sensor | 35 mm eq. | Visible to OptiLens |
|---|---|---|---|---|
| 0 | back | 5.89 mm / 8.4×6.3 mm | ~24 mm (1x) | ✅ |
| 1 | front | 3.49 mm | — | ✅ |
| 2 | back (macro) | 2.0 mm / 2.8×2.1 mm | — | ❌ hidden by MIUI |
| 3 | back (ultrawide) | 1.65 mm / 3.7×2.8 mm | ~15 mm (≈0.63x) | ❌ hidden by MIUI |
| 4 | back logical (0+3) | zoom 1x–8x (no sub-1x) | — | ❌ |

Third-party apps can open only cameras 0 and 1 on this device, so the only honest zoom set is **1x optical + digital 2x/5x/10x**. CameraX vendor extensions (HDR, Night, Bokeh, …): none available. Sensor: analog ISO 50–2000, post-RAW boost 100–3199.

## 2. Defects fixed

| # | Severity | Defect | Root cause | Fix / verification |
|---|---|---|---|---|
| 1 | High | Zoom bar showed only "1x"; Pro ISO/shutter ranges, RAW support flag and profile-based Night/Portrait plans stayed at defaults | Capability discovery (`getOrDiscoverCapabilities`) had **no caller**; it only ran from the Diagnostics screen | Discovery runs (cached) after every bind; active profile refreshed after bind/flip. Device: 1x/2x/5x/10x shown on back and front |
| 2 | High | Optical/digital zoom classification compared raw focal lengths across different sensor sizes (ultrawide would be "0.28x") | Missing 35 mm-equivalent conversion | `ZoomStop.deriveFromProfile` uses equivalent focal lengths; only the bound camera's own physical sensors can make a stop optical. Device: "Digital zoom 2.0x" readout at 2x |
| 3 | High | Pet/Moon/Wildlife "fast shutter" photos near-black (device: 1/250 s, mean luma 2.5 vs 67 for Photo) | Manual exposure set AE off + exposure time only (ISO undefined); auto exposure's large **post-RAW sensitivity boost** was ignored | Live AE metering from the preview (`CaptureCallback`), shutter/ISO priority incl. post-RAW boost (`ExposurePriority`), options awaited before capture. Device: Pet correctly exposed |
| 4 | Medium | In dim light, forced fast shutter gave noisy, tinted frames with no motion benefit; interop options also altered the HAL's still pipeline | Scene modes always forced manual exposure | Scene modes cap total gain (4× analog max) and bound the shutter at 1/60 s; if light only allows ≥1/60 s they keep auto and clear interop options. Device: Pet in a dim room = 1/50 s auto, identical to Photo. Pro mode still honours the user's shutter exactly |
| 5 | Medium | Aspect-ratio toggle only reshaped the viewfinder; every photo was 4:3 (1:1 never cropped the photo) | Only UI state changed | 4:3/16:9 requested from the sensor via `ResolutionSelector` for preview + capture; 1:1 removed. Device: 16:9 photo saved at 3840×2160 |
| 6 | Medium | Hardcoded fallback zoom stops advertised "0.6x" and "5x" as optical | UI fallback list | Fallback is 1x only |
| 7 | Medium | HDR toggle was a dead control | Local UI state only | Hidden until real multi-frame HDR (Phases 05–07) |
| 8 | Medium | Portrait (no bokeh; aperture selector ineffective), Food (WB/colour values discarded), Document (no detection/crop; colour mode ineffective) behaved exactly like Photo | No processing path | Removed from the mode rail (`IMPLEMENTED_MODES`) until implemented |
| 9 | Low | Moon badge claimed "Stacking real photons…" | Stale text | "Capturing…" |
| 10 | Low | Diagnostics: "Optical Zoom Multipliers 1x, 2x, 3x, 5x, 10x" (all digital here); startup/jank/native metrics shown as 0 with "OPTIMAL"; "App Init" printed an absolute timestamp as ms | Labels/unwired metrics | "Zoom Stops: 1x optical, 2x digital…"; startup tracker wired (process start, app/activity create, first draw, first preview frame); unmeasured metrics show "Not measured" |
| 11 | Low | Camera init started only after the 0.7 s splash | No prewarm | `CameraController.prewarm()` from `MainActivity.onCreate` overlaps CameraX init with the splash |

## 3. Startup (measured on device, not promised)

| Run | First frame drawn | Camera ready (first preview frame) | Process → App init |
|---|---|---|---|
| Cold start right after reinstall | 2717 ms | 5845 ms | 1276 ms |
| Normal cold start (before prewarm) | 1616 ms | 3992 ms | 934 ms |
| Normal cold start (with prewarm) | 1500 ms | 3639 ms | 847 ms |

`am start -W` TotalTime (first drawn activity frame): 1.7–2.5 s. The prewarm gain (~0.35 s camera-ready) is from single samples and within run-to-run variation; treat as indicative. Process→App init ≈ 0.9 s (Hilt graph / Application work) remains a Phase 17 optimisation target.

## 4. Verification executed

### Unit tests / lint
Final run: `BUILD SUCCESSFUL`. Unit tests **505 / 0 failures** (app 7, camera 206, common 27, imaging 103, logging 16, navigation 7, settings 36, ui 103). Lint **0 errors** in every module. (An intermediate run hit an internal lint crash while a file was being edited concurrently; it did not reproduce.) New tests: `ZoomStopDerivationTest` (5, incl. this device's real optics), `ExposurePriorityTest` (7, incl. device-observed boost and dim-light cases); updated `CameraControllerTest` (sensor sizes required for optical matching), `CameraViewModelTest` (aspect ratio drives capture).

### Device checks (Redmi Note 11 Pro+, API 33)
| Check | Result |
|---|---|
| Zoom stops after startup (back / front) | ✅ 1x, 2x, 5x, 10x |
| 2x labelled digital | ✅ "Digital zoom 2.0x" |
| 16:9 capture | ✅ 3840×2160 saved |
| Pet before fix (dim room) | ❌ 1/250 s ISO 2000, mean luma 2.5 (near-black) |
| Pet with boost-aware priority | ✅ exposed (mean 56–81) but noisy at extreme gain |
| Pet final (dim room) | ✅ auto 1/50 s, mean 65.7 — same as Photo (62) |
| Mode rail | ✅ Photo, Night, Best Shot, Pet, Moon, Wildlife, Pro |
| Diagnostics | ✅ honest zoom stops, measured startup, "Not measured" for unwired metrics |

**Not validated on device:** Pet/Wildlife fast shutter in bright light (could not change lighting), Moon spot exposure on an actual moon, Pro-mode manual ISO/shutter via the UI, RAW with the now-populated capability flags, lens switching (no extra lenses visible on this device).

## 5. Remaining / next

| Item | Phase |
|---|---|
| Real multi-frame HDR / fusion wired into capture; HDR toggle returns | 05–07 |
| Portrait bokeh, Night fusion; Food/Document processing | 08–09 |
| Startup: Application/Hilt init ~0.9 s; camera ready ~4 s | 17 |
| Lens switching for devices that expose ultrawide/tele to third-party apps (code path ready via zoom stops; not testable here) | 04 follow-up when a suitable device is available |
