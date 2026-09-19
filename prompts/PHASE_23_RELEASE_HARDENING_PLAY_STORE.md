# OptiLens — Phase 23


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


# PHASE 23 — Release Hardening and Play Store Candidate

## Goal
Produce a genuine release-candidate Android App Bundle.

## Tasks
1. Execute complete release checklist.
2. Update dependencies only if safely tested.
3. Target current required/validated Android API.
4. Build release AAB.
5. Verify signing setup without exposing secrets.
6. Enable/test R8 and resource shrink.
7. Archive mapping/native symbols.
8. Generate dependency/model license notice.
9. Review app size.
10. Remove debug/dead assets.
11. Prepare internal/closed Play testing.
12. Verify:
   - permissions
   - Data Safety
   - ads declaration
   - billing products
   - privacy policy
   - support info
   - accurate store claims.
13. Final release QA report.

## Rule
Do not create a production Git tag unless owner explicitly asks.

## Phase completion
After successful implementation:
1. update `docs/status/CURRENT_PHASE.md`;
2. create/update `docs/status/PHASE_23_REPORT.md`;
3. run relevant tests/build/lint;
4. review Git diff;
5. commit with `phase-23: ...`;
6. push to current tracked branch if possible;
7. report exact result;
8. STOP.

Do not read or execute the next phase prompt until the owner explicitly instructs you.
