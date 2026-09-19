# 00 — CODEX MASTER INSTRUCTION

You are the primary implementation agent for this Android computational-photography project. The current project root is authoritative.

## FIRST ACTION — NO CODING
Before touching code:
1. Read `README.md`.
2. Read every file in `docs/` except historical phase reports.
3. Read this file fully.
4. Inspect the repository tree.
5. Inspect Git status, branch and remotes.
6. Identify existing code and do not overwrite unrelated owner work.
7. Read only the phase prompt explicitly requested by the owner.

Do **not** start a phase merely because its prompt exists.

## PRODUCT STANDARD
This is not a demo. Build production-quality native Android software:
- Kotlin + Compose;
- CameraX + Camera2;
- C++/NDK only where useful;
- OpenCV/native image processing;
- LiteRT/MediaPipe/ML Kit where justified;
- on-device-first image processing;
- strict lifecycle/resource handling;
- light + dark UI;
- accessibility;
- adaptive device capability handling.

## NO SHORTCUTS
The following do not count as implementations:
- placeholder UI;
- fake progress;
- hard-coded "AI result";
- mocked capture pretending to be camera output;
- copied sample code without integration;
- empty interfaces with TODOs;
- catch-all exceptions that hide failures;
- permanently disabled feature whose phase says it must work;
- random filter passed off as HDR/denoise/super-resolution;
- fake zoom labels;
- silently switching to lower resolution without recording why.

If a requested feature is impossible on a device, implement capability detection + a correct fallback + user-safe messaging.

## DEPENDENCIES
- Prefer latest compatible **stable** dependencies.
- Do not chase alpha/beta merely because newer.
- Keep versions in `gradle/libs.versions.toml`.
- Record important choices in docs.
- Check licenses.
- Do not add two libraries for the same responsibility without reason.
- Never use RenderScript.
- Never add a cloud image enhancer that violates on-device-first intent without owner instruction.

## IMAGE FIDELITY
Never fabricate photographic facts and present them as captured reality.
No synthetic moon replacement.
No invented plate/text characters.
No identity-changing face generation by default.
Keep before/after support for significant transforms.

## PERFORMANCE
- no heavy work on main thread;
- close `ImageProxy` reliably;
- bound queues;
- use backpressure;
- avoid full-res Bitmap chains;
- reuse native buffers when safe;
- thermal-aware;
- memory-aware;
- cancellation-aware;
- benchmark instead of guessing.

## SECURITY / PRIVACY
- do not commit credentials;
- do not log image bytes;
- do not log recognized private text;
- do not upload images for core features;
- minimize permissions;
- Photo Picker for user-selected external images unless a broader permission is genuinely required.

## GIT
Before phase:
```bash
git status --short
git branch --show-current
git remote -v
```

At the end:
1. run verification;
2. fix failures;
3. update `docs/status/CURRENT_PHASE.md`;
4. create `docs/status/PHASE_XX_REPORT.md`;
5. update any architecture/ADR docs;
6. review `git diff`;
7. commit using `phase-XX: ...`;
8. push the current branch to `origin` if configured;
9. if push fails, keep commit and report exact reason;
10. STOP.

Do not use `git reset --hard`, force push, or discard unrelated changes without explicit permission.

## PHASE ISOLATION
Only implement the current phase.
You may make a tiny prerequisite fix if the current phase cannot compile without it, but:
- document it;
- keep it minimal;
- do not implement next-phase features.

## TESTS
Each phase prompt defines tests. Also run the narrowest relevant build/lint/tests after changes.
Do not report tests as passed unless they were actually executed.

If a physical camera/device is required but not connected:
- complete code/unit/emulator work;
- create an exact physical-device test checklist;
- mark physical validation `PENDING`, not `PASS`;
- a phase whose gate explicitly requires physical validation remains BLOCKED until that validation occurs.

## COMPLETION RESPONSE
Always end with:
- Phase:
- Status: PASS or BLOCKED
- What changed:
- Tests:
- Physical device status:
- Git commit:
- Push:
- Next prompt:
- `STOPPED — waiting for explicit user instruction before the next phase.`

Never continue automatically.
