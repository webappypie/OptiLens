# OptiLens — Phase 03


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


# PHASE 03 — Camera Capability Discovery Engine

## Goal
Build the device-aware camera capability engine before real capture UI.

## Tasks
1. Add stable CameraX 1.6+ line and Camera2 dependencies.
2. Implement `CameraCapabilityProfile`.
3. Enumerate logical and physical cameras.
4. Query and expose:
   - lens facing
   - focal lengths
   - sensor/active array
   - hardware level
   - output sizes/formats
   - zoom range
   - flash
   - AF/AE/AWB
   - OIS/EIS
   - RAW
   - BURST_CAPTURE
   - YUV/PRIVATE reprocessing
   - manual sensor
   - manual post-processing
   - logical multi-camera
   - ultra-high-resolution
   - dynamic range capabilities
   - CameraX extension availability
   - low-light boost availability
   - stream configuration limits
   - latest platform-specific RAW modes where supported.
5. Create stable device performance tier heuristic.
6. Add `DeviceQuirkRegistry`.
7. Cache non-sensitive capability summary.
8. Build debug diagnostics screen/export.
9. Add unit tests using fixtures.

## Gate
Never infer a capability only from manufacturer/model if Android reports it directly.

## Phase completion
After successful implementation:
1. update `docs/status/CURRENT_PHASE.md`;
2. create/update `docs/status/PHASE_03_REPORT.md`;
3. run relevant tests/build/lint;
4. review Git diff;
5. commit with `phase-03: ...`;
6. push to current tracked branch if possible;
7. report exact result;
8. STOP.

Do not read or execute the next phase prompt until the owner explicitly instructs you.
