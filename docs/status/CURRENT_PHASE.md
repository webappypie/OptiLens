# Current Phase

| Field | Value |
|---|---|
| **Roadmap** | `docs/15_ROADMAP.md` (new numbering; previous `phase-00`…`phase-24` commits belong to the earlier roadmap) |
| **Last completed** | UI localization parts 1–2 (6 languages) + automated validation (2026-10-13); earlier: burst pipelining, capture tags, splash overlap, tappable hints |
| **Result** | Capture → preview → Enhance → compare → save/retake · on-device enhancement (1.16 s / 8 MP non-debuggable) · real multi-frame Night, Moon and HDR (≈6–7 s) · WAPCentral `/ads/config` drives the gallery banner · all device-verified on Redmi Note 11 Pro+ (API 33) except Moon and the thermal/cancel fallbacks · unit tests and lint: see report §11 |
| **Reports** | `docs/status/PHASE_00_REPORT.md`, `PHASE_01_REPORT.md`, `PHASE_02_REPORT.md`, `PHASE_03_04_REPORT.md`, `CONSOLIDATED_COMPLETION_REPORT.md` |
| **Unit tests / lint** | 543 / 0 failures; lint 0 errors (2026-10-13) |
| **Deferred / blocked** | Enhance Photo by AI (cloud) — design in `docs/05`; WAPCentral notifications (FCM + debug package + registration contract); portrait bokeh (integrity decision); billing; release/device matrix |
| **Pending physical checks** | Runs 4–9 on device (burst pipelining, capture tags + gentle Enhance + HDR persistence, splash overlap, tappable hints, localized UI incl. long German/French labels, camera error snackbar); Moon merge; thermal/cancel fallbacks; low-RAM devices; signed release |

## Open owner decisions
- Fate of the uncommitted documentation changes (new doc pack, deleted legacy reports and release/licence docs) — still uncommitted, untouched by this run.
- Monetization model, Play Console product IDs, privacy-policy URL; server-side purchase verification.
- WAPCentral: ads now use the live `/ads/config` (AdMob chain); the X-App-Key is compiled into the APK from `local.properties` as the guide instructs — it is extractable; confirm or provide a per-install token. Notifications need FCM setup, a `.debug` Firebase client and the device-registration body.
- WAPCentral `image` module (needed for Enhance Photo by AI) is "Not enabled".
- Firebase debug package registration; project licence (none in repo).

STOPPED — waiting for explicit user instruction before the next phase.
