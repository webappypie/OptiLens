# OptiLens — Phase 05


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


# PHASE 05 — Lenses, Zoom, Controls and Pro Base

## Goal
Expose truthful device-aware camera controls.

## Tasks
1. Detect optical/physical lens candidates.
2. Build smooth zoom controller.
3. Add haptic feedback at meaningful optical stops.
4. Add quick zoom stops derived from actual hardware.
5. Timer.
6. Aspect ratio.
7. Grid.
8. Level/horizon using sensors.
9. Volume-key shutter preference.
10. Add Pro base:
   - ISO
   - shutter
   - focus distance
   - WB mode
   - EV
   - AUTO reset.
11. Disable unsupported controls explicitly.
12. Prepare histogram architecture.
13. Avoid excessive camera rebinds/flicker.

## Gate
Never label digital crop as optical 3x/5x.

## Phase completion
After successful implementation:
1. update `docs/status/CURRENT_PHASE.md`;
2. create/update `docs/status/PHASE_05_REPORT.md`;
3. run relevant tests/build/lint;
4. review Git diff;
5. commit with `phase-05: ...`;
6. push to current tracked branch if possible;
7. report exact result;
8. STOP.

Do not read or execute the next phase prompt until the owner explicitly instructs you.
