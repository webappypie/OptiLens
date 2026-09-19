OptiLens is a premium Android computational photography camera app designed to produce cleaner, sharper, brighter, and more professional-looking photos automatically.

The app combines modern Android camera APIs, multi-frame computational photography, device-aware image processing, and on-device AI while keeping the user experience simple: point, shoot, and let OptiLens optimize the result.

## Product Vision

OptiLens should feel like a camera first, not an AI demo or photo-filter app.

> Capture better-looking photos automatically with intelligent, device-aware computational photography.

OptiLens does not rely on misleading "100x AI zoom", fake megapixel claims, synthetic moon replacement, or aggressive beauty effects.

## Brand

- **Product Name:** OptiLens
- **Tagline:** Enhanced by AI
- **Category:** AI Computational Camera
- **Platform:** Android
- **Provisional application ID:** `com.webappypie.optilens`

## Core Technology

- Kotlin
- Jetpack Compose
- CameraX
- Camera2
- CameraX Extensions
- C++ / Android NDK
- OpenCV
- LiteRT
- MediaPipe
- Google ML Kit
- GPU / NPU acceleration where supported
- Coroutines / Flow
- DataStore
- MediaStore
- Android Photo Picker
- Firebase Remote Config
- Firebase Analytics
- Firebase Crashlytics
- Google Play Billing
- Google Mobile Ads

## Core Features

### Camera
- AI Auto Camera
- Multi-frame capture
- Smart HDR
- Night Mode
- Portrait Mode
- Pro Mode
- Physical lens switching
- Tap focus
- Exposure control
- Smart zoom
- Grid and level
- Timer
- RAW / DNG where supported

### Computational Photography
- Frame scoring and rejection
- Motion-aware alignment
- Temporal denoise
- Multi-frame fusion
- Exposure fusion
- Highlight/shadow recovery
- Auto white balance and color correction
- Local tone mapping
- Noise-aware sharpening
- Texture and face protection

### AI
- Scene detection
- Face landmarks
- Subject-motion detection
- Low-light detection
- Backlight detection
- AI Denoise
- AI Detail Enhancement
- AI Super Resolution
- AI Zoom
- Best Shot
- Deblur
- Background Cleanup
- Reflection Reduction
- Photo Upscaling
- Photo Restoration

### Specialized Modes
- Pet
- Food
- Document
- Nature
- Wildlife / Bird
- Moon Assist
- Best Shot

### Review
- One-Tap AI Enhance
- Original / Enhanced toggle
- Before / After slider
- Synchronized zoom and pan
- Preserve original option

## Product Principles

1. One tap first.
2. Natural output before dramatic output.
3. Real image quality before marketing effects.
4. Device-aware processing.
5. Graceful fallbacks on unsupported hardware.
6. No fake computational photography.
7. No blocking image processing on the main thread.
8. No unnecessary permissions.
9. Core photo processing remains on-device.
10. Camera experience stays clean and ad-free.

## UI / UX Direction

OptiLens should feel:
- Premium
- Clean
- Minimal
- Professional
- Photographic
- Fast
- Trustworthy

Both Light and Dark themes must be fully polished.

Avoid cluttered camera controls, excessive gradients, neon AI styling, fake loading percentages, and intrusive processing screens.

## Monetization

### Free
- Core camera
- Standard AI Auto
- Standard Night
- Portrait
- Standard enhancement
- Basic AI Zoom
- Full normal export quality

### Pro Lifetime
- Remove ads
- Max Quality processing
- Advanced Super Resolution
- Advanced Pro / RAW tools
- Premium processing profiles
- Advanced AI tools

Ads must never appear inside the live viewfinder, near the shutter button, or immediately after every photo.

## Privacy

- No account required for core camera use
- No photo upload for normal enhancement
- No face identification database
- No image content sent to analytics
- No OCR content sent to analytics
- No precise-location analytics
- Use MediaStore for captures
- Use Android Photo Picker for user-selected existing photos whenever possible

## Development Workflow


Then execute phase prompts in numeric order.

## Phase Gate Rule

Codex must never automatically continue to the next phase.

At the end of every phase it must:

1. Finish the requested scope
2. Run tests
3. Fix failures
4. Update documentation
5. Update phase status
6. Review Git diff
7. Commit changes
8. Push to GitHub when remote access is available
9. Report results
10. STOP

Required final message:

```text
STOPPED — waiting for explicit user instruction before the next phase.
```

The next phase begins only when the owner explicitly requests it.

## Definition of Done

A feature is not complete merely because UI exists.

A feature is complete only when:
- real functionality works
- unsupported devices have a fallback
- lifecycle behavior works
- resources are released correctly
- errors are handled
- tests pass
- required physical-device validation is performed
- performance is acceptable
- documentation is updated
- Git commit is created
- Git push succeeds when available

Mocks, placeholders, fake AI outputs, TODO implementations, hard-coded demos, or visually simulated features do not count as completed work.

## Git Commit Convention

```text
phase-04: implement real camera preview and capture
phase-09: add multi-frame HDR fusion pipeline
phase-13: implement AI super-resolution zoom
```

Do not force-push or destroy unrelated user work.

## Current Development Sequence

```text
Phase 00 — Repository Bootstrap
Phase 01 — Foundation Architecture
Phase 02 — Design System and Navigation
Phase 03 — Camera Capability Engine
Phase 04 — Camera Preview and Capture
Phase 05 — Camera Controls and Pro Base
Phase 06 — Scene and Quality Analysis
Phase 07 — Multi-Frame Acquisition
Phase 08 — Frame Scoring and Alignment
Phase 09 — Fusion, HDR, Denoise and Color
Phase 10 — Night Mode
Phase 11 — Portrait Pipeline
Phase 12 — AI Enhance and Before/After
Phase 13 — Super Resolution and AI Zoom
Phase 14 — Pro Mode and RAW/DNG
Phase 15 — Gallery and Existing Photos
Phase 16 — Specialized Modes and Best Shot
Phase 17 — Billing, Ads and Remote Config
Phase 18 — Advanced AI Tools
Phase 19 — Performance and Thermal Optimization
Phase 20 — Moon, Wildlife and Object Tracking
Phase 21 — Privacy and Security Hardening
Phase 22 — Multi-Device Compatibility
Phase 23 — Play Store Release Hardening
Phase 24 — Growth and Store Instrumentation
```

## Quality Standard

Priority order:

```text
Reliability
→ Image Quality
→ Performance
→ Compatibility
→ UX
→ Advanced Features
→ Monetization
→ Growth
```

The key product test:

> Does the user consistently prefer the OptiLens result while the photo still looks natural and trustworthy?

If not, improve the imaging pipeline before adding more features.

---

git remote add origin https://github.com/webappypie/OptiLens.git
git branch -M main
git push -u origin main

**OptiLens — Enhanced by AI**
