# OptiLens — Google Play Store Asset Specification

This document defines the complete store listing metadata, visual asset requirements, screenshot narratives, and Play Store policy compliance guardrails for OptiLens. All visual assets and copy are strictly derived from the real production UI and authentic computational photography capabilities built across Phases 1–24.

---

## 1. Store Listing Copy & Text Metadata

### App Title (Max 30 characters)
`OptiLens: AI Pro Camera & RAW` *(29 characters)*

### Short Description (Max 80 characters)
`Pro computational camera with night burst, 14-bit RAW DNG, and on-device AI tools.` *(79 characters)*

### Full Description (Up to 4,000 characters)
```text
Unlock the full optical potential of your Android camera with OptiLens — the pro camera and computational photography suite that delivers DSLR-grade dynamic range, crisp low-light captures, and intelligent image enhancement entirely on your device.

Unlike generic camera apps that apply heavy, artificial beauty filters or send your private photos to remote cloud servers, OptiLens executes 100% of its multi-frame fusion, computational exposure stacking, and neural enhancement algorithms directly on your phone's hardware.

AUTHENTIC COMPUTATIONAL PHOTOGRAPHY
• Night Mode Multi-Frame Burst: Capture up to 8 exposure brackets aligned with sub-pixel homography to eliminate motion blur and suppress sensor noise in extreme low light.
• AI Super Resolution Zoom: Neural crop enhancement reconstructs lost high-frequency texture and acutance at 2x, 3x, and 4x digital zoom.
• One-Tap AI Enhance: Authentic shadow expansion, dynamic range equalization, and chromatic micro-contrast tuning without oversaturating natural skin tones.

FULL MANUAL PRO CONTROLS & RAW
• 14-bit Sensor RAW DNG: Capture raw Bayer sensor data alongside companion JPEGs for professional Lightroom or Photoshop desktop workflows.
• Manual Exposure & Focus: Stepless ISO (50–12,800), exposure duration (1/10,000s to 32s), and manual focus distance with live green focus peaking.
• Live Exposure Zebra & Real-Time Histogram: Real-time luminance and RGB channel distribution preventing clipped highlights and crushed shadows.
• Custom Color Profiles: Select between Natural (colorimetric accuracy), Balanced (subtle HDR tonemapping), and Vivid (high-dynamic range punch).

ON-DEVICE AI CREATIVE STUDIO
• Motion Deblur: Kernel point-spread deconvolution to sharpen accidental hand-shake or moving subjects.
• Object Eraser: Inpaint distractions, photobombers, and power lines with seamless background blending.
• Reflection Reduction: Suppress distracting glare when shooting through glass windows or museum vitrines.
• Vintage Photo Restoration: Neutralize faded color shifts and repair surface scratches on scanned heritage prints.

PRIVACY & HARDWARE TRANSPARENCY
• 100% On-Device: Your photos and location data never leave your phone. Zero cloud servers, zero analytics tracking on photo contents.
• Hardware Capability Diagnostics: Transparently inspect your camera sensor's physical dimensions, supported frame rates, Camera2 hardware level (LEGACY, LIMITED, FULL, LEVEL_3), and active device quirks.
• Frequency-Capped & Respectful: Zero ads during viewfinder capture or shooting moments.

Experience the next evolution of mobile photography with OptiLens.
```

---

## 2. Graphic Asset Specifications

| Asset Type | Dimensions | Aspect Ratio | Format | File Size Limit | Notes |
|---|---|---|---|---|---|
| **App Icon** | 512 × 512 px | 1:1 | 32-bit PNG | < 1,024 KB | Zero alpha, rounded corners rendered dynamically by Google Play |
| **Feature Graphic** | 1024 × 500 px | 2.048:1 | JPEG / 24-bit PNG | < 1,024 KB | Vibrant deep violet (#0D0D1A) background with camera lens aperture hero graphic and tagline |
| **Phone Screenshots** | 1080 × 2400 px | 9:20 (FHD+) | 24-bit PNG / JPEG | < 8 MB / image | Minimum 4, recommended 8 screenshots based on real final UI |
| **7-inch Tablet Screenshots** | 1200 × 1920 px | 10:16 | 24-bit PNG / JPEG | < 8 MB / image | Adaptive two-pane gallery and viewfinder controls layout |
| **10-inch Tablet Screenshots**| 1600 × 2560 px | 10:16 | 24-bit PNG / JPEG | < 8 MB / image | Landscape-optimized split inspector and histogram view |

---

## 3. Real Final UI Screenshot Narrative Sequence

Each screenshot showcases an authentic UI screen and genuine mathematical output produced by OptiLens:

```
┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐
│  SCREENSHOT 1   │ │  SCREENSHOT 2   │ │  SCREENSHOT 3   │ │  SCREENSHOT 4   │
│  Live Viewfinder│ │  Before / After │ │   Night Burst   │ │  Super Res Zoom │
│  & Pro Overlays │ │  Split Slider   │ │  Stack Fusion   │ │   AI Crop 4x    │
└─────────────────┘ └─────────────────┘ └─────────────────┘ └─────────────────┘
┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐
│  SCREENSHOT 5   │ │  SCREENSHOT 6   │ │  SCREENSHOT 7   │ │  SCREENSHOT 8   │
│ 14-Bit RAW DNG  │ │  Fast Media     │ │ AI Tools Studio │ │ 100% On-Device  │
│ Focus Peaking   │ │  Detail Viewer  │ │ Deblur & Eraser │ │ Privacy Guard   │
└─────────────────┘ └─────────────────┘ └─────────────────┘ └─────────────────┘
```

### Screenshot 1: Pro Viewfinder & Real-Time Scopes
- **Header Caption**: *"Pro Manual Camera Viewfinder"*
- **Sub-caption**: *"Live RGB histogram, horizon level, focus peaking, and EV bracketing."*
- **Visual Subject**: Real camera preview screen (`CameraScreen.kt`) with live luminance histogram in top right corner, horizon leveling bar centered, mode carousel set to `PHOTO`, and quick controls for flash, aspect ratio, and grid overlay.

### Screenshot 2: Authentic One-Tap AI Enhance
- **Header Caption**: *"One-Tap AI Image Enhancement"*
- **Sub-caption**: *"Real shadow recovery and micro-contrast clarity with zero synthetic artifacts."*
- **Visual Subject**: Detail viewer (`PhotoDetailScreen.kt`) with interactive split before/after slider (`AiEnhanceSplitSlider.kt`) at 50% position, displaying genuine +4.2 dB SNR improvement and restored shadow texture on a golden-hour portrait.

### Screenshot 3: Night Mode Multi-Frame Burst
- **Header Caption**: *"Night Mode Computational Burst"*
- **Sub-caption**: *"Sub-pixel alignment stacks 8 frames for sharp, noise-free low-light scenes."*
- **Visual Subject**: Night mode viewfinder showing circular exposure countdown indicator, handheld stability meter, and crisp star/cityscape capture without motion blur.

### Screenshot 4: 4x Pro AI Super Resolution Zoom
- **Header Caption**: *"Neural Super-Resolution Zoom"*
- **Sub-caption**: *"Reconstruct high-frequency detail at 2x, 3x, and 4x digital crop factors."*
- **Visual Subject**: Viewfinder zoomed to 4.0x with optical zoom router badge (`OpticalZoomRouter.kt`), highlighting clean edge resolution on distant architectural architectural facade.

### Screenshot 5: 14-Bit RAW Sensor DNG & Focus Peaking
- **Header Caption**: *"14-Bit Uncompressed RAW DNG"*
- **Sub-caption**: *"Full sensor data preservation with manual ISO, shutter, and green focus peaking."*
- **Visual Subject**: Pro mode manual dial active (`IsoShutterDial.kt`), ISO 100, shutter 1/250s, green neon focus peaking contours highlighting in-focus subject edges.

### Screenshot 6: Clean Media Gallery & Quick Actions
- **Header Caption**: *"Instant Gallery & Non-Destructive Editing"*
- **Sub-caption**: *"Browse RAW and JPEG captures with instant lossless export."*
- **Visual Subject**: Media browser (`GalleryScreen.kt`) with segmented RAW/HDR badges, favoriting heart icons, and batch sharing action sheet.

### Screenshot 7: On-Device AI Tools Studio
- **Header Caption**: *"AI Magic Tools Studio"*
- **Sub-caption**: *"Motion Deblur, Object Eraser, Reflection Removal & Photo Restoration."*
- **Visual Subject**: Creative studio dashboard (`AiToolsScreen.kt`) displaying real card tiles for Deblur, Inpainting, Reflection Reduction, and Vintage Restoration.

### Screenshot 8: 100% On-Device Privacy & Sensor Diagnostics
- **Header Caption**: *"100% On-Device Privacy"*
- **Sub-caption**: *"Zero cloud uploads. Inspect full Camera2 hardware specs and active quirks."*
- **Visual Subject**: Diagnostics screen (`CameraDiagnosticsScreen.kt`) displaying device hardware level `FULL`, physical pixel array $4032 \times 3024$, and on-device privacy guarantee shield.

---

## 4. Policy Guardrails & Compliance Checklist

OptiLens strictly adheres to Google Play's Developer Program Policies regarding truthful representation:

- [x] **No Fake Reviews**: The app never prompts for reviews using fake incentive dialogs or deceptive countdowns. In-app reviews are strictly gated by `ReviewEligibilityGate` (minimum 5 real captures or 3 AI keeps, $\ge 2$ days install age, 30-day cooldown).
- [x] **No Fake Before/After**: Store screenshots and before/after comparisons are generated strictly using authentic algorithmic processing (`BeforeAfterSampleGenerator.kt`) with verifiable mathematical SNR and sharpness metrics.
- [x] **No Misleading AI Claims**: No claims of "100x optical clarity" or synthetic moon texture substitution. All zoom capabilities accurately describe computational digital super-resolution.
- [x] **No Forced Sharing**: Growth loops utilize non-intrusive voluntary watermark toggles and standard Android system share sheets.
- [x] **Zero Cloud Transmission**: All image processing executes 100% on-device.
