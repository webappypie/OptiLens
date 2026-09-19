# OptiLens — Phase 24


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


# PHASE 24 — Growth, Store Instrumentation and 1M-Download Product Loop

## Goal
Complete growth infrastructure without harming product quality.

## Tasks
1. Validate analytics for:
   - activation
   - camera-ready
   - successful capture
   - mode usage
   - AI Keep Rate
   - processing failures
   - purchase funnel
   - retention.
2. Add in-app review eligibility only after positive experiences.
3. Add deep links/app links if needed for campaigns.
4. Write store asset specification based on real final UI.
5. Build real before/after sample generation workflow.
6. Prepare localization framework.
7. Add safe experimentation hooks.
8. Add non-sensitive device/performance cohort metadata.
9. Add diagnostics export without photo content by default.
10. Write `docs/status/GROWTH_LAUNCH_PLAN.md`.

## Forbidden
- fake reviews
- fake before/after
- fake countdowns
- forced sharing
- misleading AI claims.

## Completion
Commit, push and STOP for owner review.

## Phase completion
After successful implementation:
1. update `docs/status/CURRENT_PHASE.md`;
2. create/update `docs/status/PHASE_24_REPORT.md`;
3. run relevant tests/build/lint;
4. review Git diff;
5. commit with `phase-24: ...`;
6. push to current tracked branch if possible;
7. report exact result;
8. STOP.

Do not read or execute the next phase prompt until the owner explicitly instructs you.
