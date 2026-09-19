# OptiLens — Phase 01


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


# PHASE 01 — Application Architecture Foundation

## Goal
Implement production app architecture without real camera behavior.

## Tasks
1. Establish meaningful module boundaries for:
   - app
   - core/common
   - core/ui/design system
   - core/navigation
   - core/logging
   - core/settings
   - camera API abstractions
   - imaging API abstractions.
2. Configure dependency injection.
3. Create coroutine dispatcher abstraction.
4. Add structured logging abstraction with safe release behavior.
5. Add DataStore settings layer.
6. Add immutable UI state patterns.
7. Add typed error/result model.
8. Add feature-flag interface with local defaults.
9. Add BuildInfo/diagnostics provider.
10. Add test fakes.
11. Create ADR documenting architecture.
12. No real camera implementation yet.

## Verification
- all modules compile,
- no dependency cycles,
- navigation shell works,
- settings persistence unit tests pass,
- release build succeeds.

## Phase completion
After successful implementation:
1. update `docs/status/CURRENT_PHASE.md`;
2. create/update `docs/status/PHASE_01_REPORT.md`;
3. run relevant tests/build/lint;
4. review Git diff;
5. commit with `phase-01: ...`;
6. push to current tracked branch if possible;
7. report exact result;
8. STOP.

Do not read or execute the next phase prompt until the owner explicitly instructs you.
