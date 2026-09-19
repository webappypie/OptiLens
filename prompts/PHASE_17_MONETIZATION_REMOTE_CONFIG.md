# OptiLens — Phase 17


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


# PHASE 17 — Firebase Remote Config, Billing and Safe Ads

## Goal
Add business systems without harming camera trust.

## Tasks
1. Firebase integration with owner config.
2. Local Remote Config defaults.
3. Feature flags/kill switches.
4. Device-specific processing overrides.
5. Current stable Google Play Billing.
6. Central EntitlementRepository.
7. Lifetime Pro.
8. Optional AI/Looks packs.
9. Restore/re-query ownership.
10. Pending/error/offline behavior.
11. Google Mobile Ads with test IDs in debug.
12. No ads in viewfinder or near shutter.
13. Conservative frequency cap.
14. AdProvider abstraction.
15. Pro removes ads.
16. Premium screen.
17. Purchase/ad analytics.

## Security
No secrets in source or Remote Config.
No release Pro bypass.

## Phase completion
After successful implementation:
1. update `docs/status/CURRENT_PHASE.md`;
2. create/update `docs/status/PHASE_17_REPORT.md`;
3. run relevant tests/build/lint;
4. review Git diff;
5. commit with `phase-17: ...`;
6. push to current tracked branch if possible;
7. report exact result;
8. STOP.

Do not read or execute the next phase prompt until the owner explicitly instructs you.
