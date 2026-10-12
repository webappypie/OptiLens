# Current Phase

| Field | Value |
|---|---|
| **Roadmap** | `docs/15_ROADMAP.md` (new numbering; previous `phase-00`…`phase-24` commits belong to the earlier roadmap) |
| **Last completed** | Consolidated run — capture preview + on-device enhancement (2026-10-12) |
| **Result** | Capture → preview → Enhance → Original/Enhanced/Compare → Save copy / Retake, device-verified · new guided-filter on-device enhancement pipeline (no cloud, no ads, no tokens) · 528/528 unit tests (1 opt-in harness skipped) · lint 0 errors · validated on Redmi Note 11 Pro+ (API 33, debug build) |
| **Reports** | `docs/status/PHASE_00_REPORT.md`, `PHASE_01_REPORT.md`, `PHASE_02_REPORT.md`, `PHASE_03_04_REPORT.md`, `CONSOLIDATED_COMPLETION_REPORT.md` |
| **Deferred** | Enhance Photo by AI (cloud) — design in `docs/05`; multi-frame HDR/night fusion in capture; WAPCentral ads/notifications SDK work; billing; release/device matrix |
| **Pending physical checks** | Release-build enhancement speed; cancel/background during enhancement; low-RAM devices; Light theme/large font on the preview |

## Open owner decisions
- Fate of the uncommitted documentation changes (new doc pack, deleted legacy reports and release/licence docs) — still uncommitted, untouched by this run.
- Monetization model, Play Console product IDs, privacy-policy URL; server-side purchase verification.
- WAPCentral: no native Android SDK in the repo (guide is Flutter-only); AppLovin SDK key "Not configured"; `/ads/config` schema undocumented; X-App-Key must not ship in the APK without a decision.
- WAPCentral `image` module (needed for Enhance Photo by AI) is "Not enabled".
- Firebase debug package registration; project licence (none in repo).

STOPPED — waiting for explicit user instruction before the next phase.
