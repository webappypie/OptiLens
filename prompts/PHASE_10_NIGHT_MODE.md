# OptiLens — Phase 10


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


# PHASE 10 — Night Mode and Low-Light Boost

## Goal
Build a serious Night pipeline that chooses the best path per device.

## Tasks
1. Detect vendor Night extension.
2. Add selection policy: vendor vs custom.
3. Use preview low-light boost where supported without confusing preview with final image quality.
4. Custom Night:
   - stability detection
   - dynamic exposure plan
   - burst
   - alignment
   - motion masks
   - temporal denoise
   - exposure fusion
   - chroma cleanup
   - highlight protection
   - conservative sharpening.
5. Moving-subject fallback.
6. Thermal degradation.
7. Clean `Hold steady` and processing UX.
8. Never freeze whole app.

## Test
- warm indoor,
- dark street,
- neon signs,
- faces,
- handheld motion,
- stable/tripod scene.

## Phase completion
After successful implementation:
1. update `docs/status/CURRENT_PHASE.md`;
2. create/update `docs/status/PHASE_10_REPORT.md`;
3. run relevant tests/build/lint;
4. review Git diff;
5. commit with `phase-10: ...`;
6. push to current tracked branch if possible;
7. report exact result;
8. STOP.

Do not read or execute the next phase prompt until the owner explicitly instructs you.
