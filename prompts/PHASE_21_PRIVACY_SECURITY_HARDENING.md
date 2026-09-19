# OptiLens — Phase 21


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


# PHASE 21 — Privacy, Analytics and Security Hardening

## Goal
Audit the whole app before broad device testing/release.

## Tasks
1. Permission inventory.
2. Merged manifest audit.
3. Exported component audit.
4. Analytics payload audit.
5. Crashlytics configuration.
6. Production log redaction.
7. Cache/temp cleanup.
8. Safe content URI handling.
9. Network security config.
10. Third-party SDK data inventory.
11. Draft Play Data Safety mapping.
12. Search for debug/pro bypasses.
13. Dependency vulnerability/license review.
14. User-facing privacy/settings page.
15. Verify core photo bytes are not uploaded.

## Deliverable
Create `docs/status/PRIVACY_SECURITY_AUDIT.md`.

## Phase completion
After successful implementation:
1. update `docs/status/CURRENT_PHASE.md`;
2. create/update `docs/status/PHASE_21_REPORT.md`;
3. run relevant tests/build/lint;
4. review Git diff;
5. commit with `phase-21: ...`;
6. push to current tracked branch if possible;
7. report exact result;
8. STOP.

Do not read or execute the next phase prompt until the owner explicitly instructs you.
