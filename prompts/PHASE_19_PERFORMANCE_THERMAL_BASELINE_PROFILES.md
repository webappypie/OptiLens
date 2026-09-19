# OptiLens — Phase 19


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


# PHASE 19 — Performance, Thermal, Memory and Baseline Profiles

## Goal
Make feature-complete OptiLens fast and sustainable.

## Tasks
1. Measure startup and camera-ready latency.
2. Add Baseline Profile.
3. Add Startup Profile where useful.
4. Macrobenchmark:
   - launch to camera
   - mode switch
   - review
   - gallery.
5. Frame/jank metrics.
6. Java/native memory profiling.
7. Buffer reuse audit.
8. Thermal status integration.
9. Warm/Hot/Critical processing policies.
10. Battery loop test.
11. Inference delegate benchmarks.
12. Reduce unnecessary camera rebinds.
13. Limit background processing concurrency.
14. Tune frame count/resolution by device tier.
15. Populate quality dashboard.

## Gate
No known unbounded queue or resource leak.

## Phase completion
After successful implementation:
1. update `docs/status/CURRENT_PHASE.md`;
2. create/update `docs/status/PHASE_19_REPORT.md`;
3. run relevant tests/build/lint;
4. review Git diff;
5. commit with `phase-19: ...`;
6. push to current tracked branch if possible;
7. report exact result;
8. STOP.

Do not read or execute the next phase prompt until the owner explicitly instructs you.
