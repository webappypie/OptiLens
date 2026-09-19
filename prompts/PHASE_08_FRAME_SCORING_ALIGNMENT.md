# OptiLens — Phase 08


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


# PHASE 08 — Frame Scoring, Rejection and Alignment

## Goal
Produce an aligned stack suitable for computational fusion.

## Tasks
1. Integrate NDK/CMake.
2. Integrate OpenCV where justified.
3. Add efficient YUV/native conversion layer.
4. Frame scores:
   - sharpness
   - exposure/clipping
   - motion
   - focus confidence.
5. Select reference frame.
6. Coarse alignment.
7. Pyramidal refinement with feature/optical-flow method.
8. Alignment confidence.
9. Reject unalignable frames.
10. Create ghost/motion mask foundation.
11. Define safe JNI ownership.
12. Add deterministic native tests.
13. Use sanitizers/debug checks where practical.

## Gate
Do not implement final fusion yet.

## Phase completion
After successful implementation:
1. update `docs/status/CURRENT_PHASE.md`;
2. create/update `docs/status/PHASE_08_REPORT.md`;
3. run relevant tests/build/lint;
4. review Git diff;
5. commit with `phase-08: ...`;
6. push to current tracked branch if possible;
7. report exact result;
8. STOP.

Do not read or execute the next phase prompt until the owner explicitly instructs you.
