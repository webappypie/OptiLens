# OptiLens — Phase 02


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


# PHASE 02 — Premium Design System and Navigation

## Goal
Build final-quality UI foundations for OptiLens in both Light and Dark themes.

## Tasks
1. Implement brand design tokens:
   - colors,
   - typography,
   - spacing,
   - shape,
   - elevation,
   - icon sizing,
   - camera overlay neutrals.
2. Support:
   - System,
   - Light,
   - Dark.
3. Persist theme preference.
4. Edge-to-edge layout and safe insets.
5. Build reusable:
   - icon button,
   - top bar,
   - segmented control,
   - camera mode chip,
   - primary CTA,
   - bottom sheet,
   - settings row,
   - permission state,
   - loading state,
   - error state,
   - empty state.
6. Create final camera screen layout using a neutral fake preview surface only for layout.
7. Add routes for:
   - Camera
   - Gallery
   - AI Tools
   - Settings
   - Premium.
8. Add accessibility semantics and 48dp+ touch targets.
9. Verify no overflow on small/large screens and increased font size.
10. Avoid excessive gradients, glass effects, neon styling, and clutter.

## Verification
- light/dark screenshots,
- small and large device preview,
- font scale test,
- gesture navigation test,
- no clipped controls.

## Phase completion
After successful implementation:
1. update `docs/status/CURRENT_PHASE.md`;
2. create/update `docs/status/PHASE_02_REPORT.md`;
3. run relevant tests/build/lint;
4. review Git diff;
5. commit with `phase-02: ...`;
6. push to current tracked branch if possible;
7. report exact result;
8. STOP.

Do not read or execute the next phase prompt until the owner explicitly instructs you.
