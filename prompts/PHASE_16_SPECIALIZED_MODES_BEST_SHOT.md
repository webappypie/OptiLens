# OptiLens — Phase 16


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


# PHASE 16 — Best Shot, Pet, Food, Document and Lens Intelligence

## Goal
Use the existing capture engine to provide genuinely different scene modes.

## Best Shot
- burst
- sharpness ranking
- motion ranking
- exposure score
- eye-open score when confidence is strong
- show alternates
- manual override
- no expression synthesis.

## Pet
- faster shutter bias
- subject motion
- fur detail protection.

## Food
- stable WB
- restrained saturation
- controlled local contrast.

## Document
- page detection
- perspective correction
- illumination normalization
- readable sharpening
- grayscale/B&W options
- OCR only as a separate action.

## Lens dirty
- conservative detector
- repeated-confidence threshold
- dismissible prompt.

## Gate
Each mode must change capture/processing strategy, not only label/filter.

## Phase completion
After successful implementation:
1. update `docs/status/CURRENT_PHASE.md`;
2. create/update `docs/status/PHASE_16_REPORT.md`;
3. run relevant tests/build/lint;
4. review Git diff;
5. commit with `phase-16: ...`;
6. push to current tracked branch if possible;
7. report exact result;
8. STOP.

Do not read or execute the next phase prompt until the owner explicitly instructs you.
