# OptiLens — Phase 06


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


# PHASE 06 — Real-Time Scene and Quality Analysis

## Goal
Build an efficient ImageAnalysis intelligence loop.

## Tasks
1. Configure ImageAnalysis with correct backpressure.
2. Efficient YUV downscale/preprocessing.
3. Implement baseline scene classification:
   - portrait/people
   - pet
   - food
   - document
   - nature
   - low-light/night
   - indoor/outdoor
   - sky/plant
   - wildlife when confidence permits.
4. Add face/landmark detection with approved on-device runtime.
5. Compute:
   - luminance
   - highlight clipping
   - sharpness/focus score
   - gyro camera motion
   - subject motion estimate
   - backlight.
6. Stabilize scene results over time.
7. Throttle AI inference.
8. Feed CaptureStrategyEngine.
9. UI shows only subtle useful hints.
10. Always close ImageProxy.

## Measure
- analysis FPS,
- inference latency,
- main-thread jank,
- memory.

## Phase completion
After successful implementation:
1. update `docs/status/CURRENT_PHASE.md`;
2. create/update `docs/status/PHASE_06_REPORT.md`;
3. run relevant tests/build/lint;
4. review Git diff;
5. commit with `phase-06: ...`;
6. push to current tracked branch if possible;
7. report exact result;
8. STOP.

Do not read or execute the next phase prompt until the owner explicitly instructs you.
