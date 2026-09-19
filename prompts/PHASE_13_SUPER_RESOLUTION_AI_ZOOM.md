# OptiLens — Phase 13


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


# PHASE 13 — Super Resolution and AI Zoom

## Goal
Improve cropped/digital zoom honestly.

## Tasks
1. Optical-first routing.
2. Multi-frame SR when source stack is available.
3. Production 2x SR:
   - classical multi-frame baseline
   - compact LiteRT model only if benchmarked better.
4. Single-frame fallback.
5. Artifact/confidence guards.
6. Tile high-resolution inference.
7. Device-tier delegate selection.
8. Optional 4x Pro path only on devices passing quality/performance gate.
9. Mark output as AI Enhanced where appropriate.
10. Never fabricate readable text/identity claims.
11. Benchmark against:
   - bicubic,
   - Lanczos,
   - conventional sharpened upscale.

## Measure
- time,
- memory,
- delegate,
- thermal behavior,
- quality artifacts.

## Phase completion
After successful implementation:
1. update `docs/status/CURRENT_PHASE.md`;
2. create/update `docs/status/PHASE_13_REPORT.md`;
3. run relevant tests/build/lint;
4. review Git diff;
5. commit with `phase-13: ...`;
6. push to current tracked branch if possible;
7. report exact result;
8. STOP.

Do not read or execute the next phase prompt until the owner explicitly instructs you.
