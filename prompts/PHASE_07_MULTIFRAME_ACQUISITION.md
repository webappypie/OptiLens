# OptiLens — Phase 07


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


# PHASE 07 — Burst and Multi-Frame Acquisition

## Goal
Acquire synchronized multi-frame sequences with metadata.

## Tasks
1. Implement Camera2 burst path when supported.
2. Create `FramePacket` containing:
   - image/native buffer handle
   - timestamp
   - exposure time
   - ISO
   - focus metadata
   - AE/AWB metadata
   - gyro window.
3. Dynamic frame count from CaptureStrategyEngine.
4. Bounded buffer pool.
5. Cancellation and timeout.
6. Lifecycle-safe capture session.
7. Fallback to single capture when burst unavailable/fails.
8. Capture-latency instrumentation.
9. Debug metadata inspector.
10. Stress repeated bursts.

## Gate
Physical device required. No leaked images/buffers.

## Phase completion
After successful implementation:
1. update `docs/status/CURRENT_PHASE.md`;
2. create/update `docs/status/PHASE_07_REPORT.md`;
3. run relevant tests/build/lint;
4. review Git diff;
5. commit with `phase-07: ...`;
6. push to current tracked branch if possible;
7. report exact result;
8. STOP.

Do not read or execute the next phase prompt until the owner explicitly instructs you.
