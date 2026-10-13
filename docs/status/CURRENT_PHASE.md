# Current Phase

| Field | Value |
|---|---|
| **Roadmap** | `docs/15_ROADMAP.md` (new numbering; previous `phase-00`…`phase-24` commits belong to the earlier roadmap) |
| **Last completed** | Run 11 (2026-10-13): device verification of Run 10 on Redmi Note 11 Pro+ and 9 device-found fixes (flash-off bursts, dark-scene HDR, ASCII capture note, persistent mode, overlay overlaps, notes row, tracker orientation, faster face detection); Run 10: scene-adaptive Enhance, Portrait/Landscape, focus controls |
| **Result** | Capture → preview → Enhance → compare → save/retake · on-device enhancement (1.16 s / 8 MP non-debuggable) · real multi-frame Night, Moon and HDR (≈6–7 s) · WAPCentral `/ads/config` drives the gallery banner · all device-verified on Redmi Note 11 Pro+ (API 33) except Moon and the thermal/cancel fallbacks · unit tests and lint: see report §11 |
| **Reports** | `docs/status/PHASE_00_REPORT.md`, `PHASE_01_REPORT.md`, `PHASE_02_REPORT.md`, `PHASE_03_04_REPORT.md`, `CONSOLIDATED_COMPLETION_REPORT.md` |
| **Unit tests / lint** | 568 / 0 failures (1 skipped); lint 0 errors in all modules; `assembleDebug` OK (Run 11, 2026-10-13) · device: Run 10 + Run 11 fixes verified on Redmi Note 11 Pro+ (see report, Run 11) |
| **Deferred / blocked** | Enhance Photo by AI (cloud) — design in `docs/05`, still deferred (no upload, no tokens); WAPCentral notifications (owner work in progress, uncommitted); simulated portrait bokeh (not implemented by design — blur only via the device Bokeh extension); billing; release/device matrix |
| **Pending physical checks** | Bokeh-extension device; Landscape/HDR merge in bright high contrast; real hazy landscape (dehaze); Moon; Pet; thermal fallback; Editing Tools execution (needs billing); localized UI; low-RAM devices; signed release |

## Open owner decisions
- Fate of the uncommitted documentation changes (new doc pack, deleted legacy reports and release/licence docs) — still uncommitted. Run 10 only updated `docs/04_COMPUTATIONAL_PHOTOGRAPHY.md` (enhancement table + camera modes section) and left it uncommitted with the rest of the pack.
- Monetization model, Play Console product IDs, privacy-policy URL; server-side purchase verification.
- WAPCentral: ads now use the live `/ads/config` (AdMob chain); the X-App-Key is compiled into the APK from `local.properties` as the guide instructs — it is extractable; confirm or provide a per-install token. Notifications need FCM setup, a `.debug` Firebase client and the device-registration body.
- WAPCentral `image` module (needed for Enhance Photo by AI) is "Not enabled".
- Firebase debug package registration; project licence (none in repo).

STOPPED — waiting for explicit user instruction before the next phase.
