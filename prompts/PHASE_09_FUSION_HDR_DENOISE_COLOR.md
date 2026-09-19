# OptiLens — Phase 09


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


# PHASE 09 — Multi-Frame Fusion, HDR, Denoise, Tone and Color

## Goal
Deliver first real OptiLens computational-photography output.

## Tasks
1. Robust temporal fusion.
2. Outlier rejection.
3. Ghost-aware reference fallback regions.
4. Temporal denoise.
5. Exposure-aware HDR fusion.
6. Highlight roll-off.
7. Shadow recovery.
8. Global/local tone mapping.
9. Auto white balance correction.
10. Color correction.
11. Balanced default look.
12. Natural and Vivid optional profiles.
13. Noise-aware detail enhancement.
14. Face/skin/texture protection hooks.
15. Encode output with metadata.
16. Record stage timings.
17. Build before/after contact-sheet utility and reference image corpus.

## Quality checks
- foliage,
- skin,
- text,
- low light,
- bright signs,
- repeating patterns,
- saturated colors.

## Gate
Must visibly/technically work on at least one supported physical device while preserving reliable single-frame fallback.

## Phase completion
After successful implementation:
1. update `docs/status/CURRENT_PHASE.md`;
2. create/update `docs/status/PHASE_09_REPORT.md`;
3. run relevant tests/build/lint;
4. review Git diff;
5. commit with `phase-09: ...`;
6. push to current tracked branch if possible;
7. report exact result;
8. STOP.

Do not read or execute the next phase prompt until the owner explicitly instructs you.
