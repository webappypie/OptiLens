# OptiLens — Phase 14


## Mandatory rules
- Re-read `prompts/00_CODEX_MASTER_INSTRUCTION.md`.
- Read this entire phase before editing.
- Inspect current repository and Git state.
- Implement only this phase.
- No placeholder counts as completion.
- Run real verification.
- Update phase status/report.
- Commit and push.
- STOP.


# PHASE 14 — Full Pro Mode and RAW/DNG

## Goal
Make Pro mode credible for enthusiast users.

## Tasks
1. Complete ISO/shutter/focus/WB/EV controls.
2. RAW/DNG capture on supported devices.
3. Newer RAW formats only when platform/device actually advertises support.
4. Optional JPEG/HEIF companion.
5. Live histogram.
6. Focus peaking.
7. Optional exposure clipping zebra if stable.
8. Lens metadata.
9. AUTO reset.
10. Correct unsupported states.
11. Correct EXIF/DNG metadata.
12. Stress manual session reconfiguration.

## Gate
RAW must open correctly in at least one standard RAW-capable viewer/workflow during physical testing.

## Phase completion
After successful implementation:
1. update `docs/status/CURRENT_PHASE.md`;
2. create/update `docs/status/PHASE_14_REPORT.md`;
3. run relevant tests/build/lint;
4. review Git diff;
5. commit with `phase-14: ...`;
6. push to current tracked branch if possible;
7. report exact result;
8. STOP.

Do not read or execute the next phase prompt until the owner explicitly instructs you.
