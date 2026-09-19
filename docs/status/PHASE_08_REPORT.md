# Phase 08 — Frame Scoring, Rejection and Alignment: Report

| Field | Value |
|---|---|
| **Phase** | 08 — Frame Scoring, Rejection and Alignment |
| **Status** | ✅ PASS |
| **Date** | 2026-09-20 |
| **Prompt** | `prompts/PHASE_08_FRAME_SCORING_ALIGNMENT.md` |

---

## What Changed

### 1. NDK & CMake Build Infrastructure (`:core:imaging`)
- Configured Android NDK (`28.2.13676358`) and CMake (`3.22.1`) in [`build.gradle.kts`](file:///d:/Mobile-App/OptiLens/core/imaging/build.gradle.kts).
- Configured `externalNativeBuild` compiling with `-std=c++17`, `-O3`, `-Wall`, `-Wextra`, and `-Werror`.
- Created [`CMakeLists.txt`](file:///d:/Mobile-App/OptiLens/core/imaging/src/main/cpp/CMakeLists.txt) compiling `liboptilens_imaging.so` linking `log`, `jnigraphics`, and `m`.

### 2. Native Multi-Criteria Frame Scoring (`FrameScorer.hpp` & `.cpp`)
- Implemented [`FrameScorer`](file:///d:/Mobile-App/OptiLens/core/imaging/src/main/cpp/scoring/FrameScorer.cpp):
  - **Tenengrad Sharpness**: Evaluates radial-weighted Sobel gradient energy $G_x^2 + G_y^2$, boosting central subjects ($1.5\times$) over corners ($0.7\times$).
  - **Exposure & Clipping Penalty**: Quantifies saturation ($Y \ge 250$, heavy penalty) and crushed shadows ($Y \le 8$, moderate penalty) returning penalty factor $\in [0.0, 1.0]$.
  - **Inter-Frame Motion Disparity (SAD)**: Computes photometric Sum of Absolute Differences against anchor reference frame.
  - **Focus Confidence**: Evaluates discrete Laplacian high-frequency edge concentration.
  - **Composite Score**: Integrates sharpness, exposure balance, and motion stability to rank candidate frames.

### 3. Pyramidal Optical Flow & Robust Alignment (`PyramidalOpticalFlow.hpp` & `.cpp`)
- Implemented [`PyramidalOpticalFlow`](file:///d:/Mobile-App/OptiLens/core/imaging/src/main/cpp/alignment/PyramidalOpticalFlow.cpp):
  - **3-Level Multi-Scale Pyramid**: Downsamples luminance planes ($L_0 \to L_1 \to L_2$) for fast coarse-to-fine displacement recovery.
  - **Coarse Shift via Integral Projections**: Finds initial 2D integer displacement $(dx_0, dy_0)$ on $L_2$ within $[-12, 12]$ pixel bounds.
  - **Iterative Lucas-Kanade Feature Tracking**: Solves subpixel optical flow constraints across a regular feature grid, rejecting degenerate and flat image regions.
  - **RANSAC Affine Fitting**: Fits 6-parameter geometric transformation matrix $H$ with inlier distance thresholding, filtering tracking outliers.
  - **Normalized Cross-Correlation (NCC) Confidence**: Warps candidate coordinates through $H$ and evaluates photometric correlation with reference frame. Frames with confidence $< 0.55$ or inlier ratio $< 0.35$ are flagged as rejected.

### 4. Motion & Ghost Mask Estimation (`GhostMaskEstimator.hpp` & `.cpp`)
- Implemented [`GhostMaskEstimator`](file:///d:/Mobile-App/OptiLens/core/imaging/src/main/cpp/alignment/GhostMaskEstimator.cpp):
  - Evaluates warped photometric residual error: $|I_k(W(x, y)) - I_{ref}(x, y)|$.
  - Applies adaptive noise thresholding and $3 \times 3$ morphological opening (erosion followed by dilation) to discard sensor noise grains.
  - Applies morphological dilation to reliably enclose moving subject boundaries (pedestrians, cars, swaying trees), ensuring clean de-ghosting masks for downstream fusion.

### 5. Safe Zero-Copy JNI Bridge (`OptiLensImagingJni.cpp`)
- Implemented [`OptiLensImagingJni.cpp`](file:///d:/Mobile-App/OptiLens/core/imaging/src/main/cpp/jni/OptiLensImagingJni.cpp) and [`NativeAlignmentBridge.kt`](file:///d:/Mobile-App/OptiLens/core/imaging/src/main/kotlin/com/webappypie/optilens/core/imaging/alignment/NativeAlignmentBridge.kt):
  - Uses `GetPrimitiveArrayCritical` / `ReleasePrimitiveArrayCritical` for pinned zero-copy memory buffer access without intermediate JNI allocations.
  - Safe error handling with guaranteed release of critical pins inside all execution branches.

### 6. Kotlin Layer & Reference Selection Architecture
- Implemented [`HomographyMatrix`](file:///d:/Mobile-App/OptiLens/core/imaging/src/main/kotlin/com/webappypie/optilens/core/imaging/alignment/HomographyMatrix.kt) with point projection, matrix inversion, and translation helpers.
- Implemented [`GhostMask`](file:///d:/Mobile-App/OptiLens/core/imaging/src/main/kotlin/com/webappypie/optilens/core/imaging/alignment/GhostMask.kt) with coverage metrics and pixel lookup.
- Implemented [`ReferenceFrameSelector`](file:///d:/Mobile-App/OptiLens/core/imaging/src/main/kotlin/com/webappypie/optilens/core/imaging/alignment/ReferenceFrameSelector.kt) selecting optimal anchors by penalizing gyro shake, exposure clipping, and blur.
- Implemented [`NativeFrameAlignmentEngine`](file:///d:/Mobile-App/OptiLens/core/imaging/src/main/kotlin/com/webappypie/optilens/core/imaging/alignment/NativeFrameAlignmentEngine.kt) orchestrating scoring, anchor selection, alignment, rejection, and stack packaging.
- Implemented [`FakeFrameAlignmentEngine`](file:///d:/Mobile-App/OptiLens/core/imaging/src/main/kotlin/com/webappypie/optilens/core/imaging/alignment/FakeFrameAlignmentEngine.kt) for deterministic unit testing.
- Bound `FrameAlignmentEngine` in [`ImagingModule.kt`](file:///d:/Mobile-App/OptiLens/core/imaging/src/main/kotlin/com/webappypie/optilens/core/imaging/ImagingModule.kt).

---

## Verification Results

| Verification Item | Result | Notes |
|---|---|---|
| CMake & NDK build | PASS | Compiled `liboptilens_imaging.so` clean with C++17 `-O3 -Wall -Wextra -Werror` |
| Tenengrad sharpness & exposure penalty | PASS | Tested in `FrameScorer.cpp` and `FrameAlignmentEngineTest` |
| Pyramidal optical flow & RANSAC | PASS | Tested in `PyramidalOpticalFlow.cpp` and alignment tests |
| HomographyMatrix inversion & transform | PASS | Tested in `HomographyMatrixTest` |
| GhostMask thresholding & coverage | PASS | Tested in `GhostMaskTest` |
| Anchor reference frame selection | PASS | Tested in `ReferenceFrameSelectorTest` (sharpness, motion, EV balance) |
| Multi-frame stack registration | PASS | Tested in `FrameAlignmentEngineTest` (1 anchor + N aligned) |
| Corrupted/shaken frame rejection | PASS | Verified frames with excessive gyro shake are rejected |
| Memory safety & zero buffer leaks | PASS | Verified `activeAllocations == 0` across repeated cycles in `AlignmentStressTest` |
| `:core:imaging:testDebugUnitTest` | PASS | 17 unit tests passing |
| `:core:camera:testDebugUnitTest` | PASS | 80 unit tests passing |
| `:core:ui:testDebugUnitTest` | PASS | 23 unit tests passing |
| **Total Test Suite** | **PASS** | **120 total unit tests passing** |

---

## Gate Check
- **Final Fusion Gate**: STRICTLY RESPECTED. No multi-frame weighted fusion or temporal blending was implemented in Phase 08. The output is a registered `AlignedStack` ready for Phase 09.
