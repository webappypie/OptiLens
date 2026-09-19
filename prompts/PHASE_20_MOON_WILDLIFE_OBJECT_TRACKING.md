# OptiLens — Phase 20


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


# PHASE 20 — Moon Assist, Wildlife/Bird and Object Tracking

## Goal
Deliver advanced camera modes based on real captured data.

## Moon Assist
- moon/bright-disc detection
- tele lens preference
- spot exposure
- stability cue
- controlled short exposures
- frame stacking
- conservative detail recovery
- no synthetic moon texture.

## Wildlife/Bird
- subject detection
- ROI tracking
- fast shutter bias
- burst
- Best Shot
- fur/feather detail protection
- tele routing.

## Object Tracking
- tap/select subject
- real-time tracker
- recover after short occlusion where feasible
- no blockage of core camera analysis
- graceful disable on constrained devices.

## Gate
Include thermal/battery testing.

## Phase completion
After successful implementation:
1. update `docs/status/CURRENT_PHASE.md`;
2. create/update `docs/status/PHASE_20_REPORT.md`;
3. run relevant tests/build/lint;
4. review Git diff;
5. commit with `phase-20: ...`;
6. push to current tracked branch if possible;
7. report exact result;
8. STOP.

Do not read or execute the next phase prompt until the owner explicitly instructs you.
