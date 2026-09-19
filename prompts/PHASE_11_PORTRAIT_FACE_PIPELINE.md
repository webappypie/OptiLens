# OptiLens — Phase 11


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


# PHASE 11 — Portrait and Face-Aware Processing

## Goal
Natural portrait quality, not aggressive beauty manipulation.

## Tasks
1. Face landmarks.
2. Person/portrait segmentation where useful.
3. Face exposure balancing.
4. Face-aware denoise.
5. Eye/brow/hair detail protection.
6. Mild skin cleanup by default.
7. No default face reshaping.
8. Vendor Bokeh extension path.
9. Software bokeh fallback when confidence is adequate.
10. Multiple faces.
11. Backlit portrait handling.
12. Front-camera mirror/save preference.
13. Diverse-skin-tone regression set.

## Gate
Naturalness over dramatic beauty effect.

## Phase completion
After successful implementation:
1. update `docs/status/CURRENT_PHASE.md`;
2. create/update `docs/status/PHASE_11_REPORT.md`;
3. run relevant tests/build/lint;
4. review Git diff;
5. commit with `phase-11: ...`;
6. push to current tracked branch if possible;
7. report exact result;
8. STOP.

Do not read or execute the next phase prompt until the owner explicitly instructs you.
