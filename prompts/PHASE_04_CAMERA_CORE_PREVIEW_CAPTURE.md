# OptiLens — Phase 04


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


# PHASE 04 — Real Camera Preview and Reliable Single Capture

## Goal
Replace placeholder preview with a real production camera foundation.

## Tasks
1. Implement camera permission flow.
2. Bind CameraX Preview lifecycle safely.
3. Implement high-quality ImageCapture.
4. Front/back switching.
5. Tap-to-focus.
6. Pinch zoom.
7. Exposure compensation.
8. Flash/torch behavior.
9. Orientation and EXIF correctness.
10. Save with MediaStore.
11. Show app capture thumbnail.
12. Handle:
   - camera unavailable,
   - disconnect,
   - session failure,
   - background/resume,
   - rotate,
   - lock/unlock.
13. Do not request broad gallery permission.
14. No main-thread image I/O.
15. Deterministic ImageProxy/executor cleanup.
16. Add repeated capture test path.

## Verification
- denied/granted permission,
- capture/save,
- rotate,
- background/resume,
- switch camera,
- repeated captures,
- physical-device validation required for PASS.

## Phase completion
After successful implementation:
1. update `docs/status/CURRENT_PHASE.md`;
2. create/update `docs/status/PHASE_04_REPORT.md`;
3. run relevant tests/build/lint;
4. review Git diff;
5. commit with `phase-04: ...`;
6. push to current tracked branch if possible;
7. report exact result;
8. STOP.

Do not read or execute the next phase prompt until the owner explicitly instructs you.
