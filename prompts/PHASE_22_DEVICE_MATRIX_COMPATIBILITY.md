# OptiLens — Phase 22


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


# PHASE 22 — Multi-Device Compatibility and Quirk Tuning

## Goal
Validate real Android fragmentation.

## Tasks
1. Execute device checklist across available physical/cloud devices.
2. Populate device quality matrix.
3. Validate:
   - logical/physical lenses
   - CameraX extensions
   - burst
   - Night
   - Portrait
   - RAW
   - SR
   - Pro
   - lifecycle
   - repeated capture
   - thermal.
4. Add quirks only through central registry.
5. Add Remote Config emergency overrides.
6. Never globally disable a feature because one device is broken.
7. Document untested families honestly.

## Gate
Core camera paths must be validated across representative low/mid/high Android hardware before release.

## Phase completion
After successful implementation:
1. update `docs/status/CURRENT_PHASE.md`;
2. create/update `docs/status/PHASE_22_REPORT.md`;
3. run relevant tests/build/lint;
4. review Git diff;
5. commit with `phase-22: ...`;
6. push to current tracked branch if possible;
7. report exact result;
8. STOP.

Do not read or execute the next phase prompt until the owner explicitly instructs you.
