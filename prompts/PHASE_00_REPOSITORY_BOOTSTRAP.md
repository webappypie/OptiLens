# OptiLens — Phase 00


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


# PHASE 00 — Repository Bootstrap and Toolchain

## Goal
Create a clean, reproducible, modern native Android foundation for OptiLens. Do not implement camera features yet.

## Tasks
1. Inspect whether an Android project already exists. Preserve and adapt valid existing work.
2. Ensure Git is initialized and history is preserved.
3. Record current branch and `origin`.
4. Configure:
   - Kotlin
   - Jetpack Compose
   - Material 3
   - Kotlin DSL
   - version catalog
   - JDK 17
   - modern stable Gradle/AGP pair
   - compile/target SDK suitable for current Android release and Play requirements
   - minSdk 26 unless project docs explicitly say otherwise.
5. Use provisional applicationId `com.webappypie.optilens` unless owner has already locked another one.
6. Add debug/release build types.
7. Add `.gitignore` for:
   - Android build output
   - IDE files
   - native build output
   - keystores
   - credentials
   - secrets.
8. Add basic CI:
   - assemble,
   - unit test,
   - lint.
9. Create only a temporary development screen that shows:
   `OptiLens — Project initialized`
   This is not final product UI.
10. Record actual toolchain versions in `docs/status/TOOLCHAIN.md`.
11. Do not add Firebase, Ads, Billing, AI models, CameraX camera implementation, OpenCV, or NDK feature work yet unless required only to validate toolchain compatibility.

## Required verification
- clean Gradle sync
- assembleDebug
- unit tests task
- lint
- app installs/launches on emulator if available

## Gate
If Git remote is missing, local commit is allowed but push is BLOCKED. Report exact command needed to add the remote.

## Phase completion
After successful implementation:
1. update `docs/status/CURRENT_PHASE.md`;
2. create/update `docs/status/PHASE_00_REPORT.md`;
3. run relevant tests/build/lint;
4. review Git diff;
5. commit with `phase-00: ...`;
6. push to current tracked branch if possible;
7. report exact result;
8. STOP.

Do not read or execute the next phase prompt until the owner explicitly instructs you.
