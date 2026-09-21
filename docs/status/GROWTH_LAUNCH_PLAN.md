# OptiLens — 1M-Download Growth & Launch Plan

This document establishes the product growth architecture, organic viral distribution loops, App Store Optimization (ASO) playbook, experimentation strategy, and non-intrusive monetization funnel designed to scale OptiLens to 1,000,000 active downloads without compromising core product quality or user trust.

---

## 1. The 1M-Download Product Loop

OptiLens relies on an organic, utility-driven product flywheel where superior capture quality drives user satisfaction, social sharing, and high-velocity word-of-mouth adoption:

```
                  ┌────────────────────────────────────────┐
                  │          1. Instant Activation         │
                  │ Camera ready < 500ms; zero friction   │
                  └───────────────────┬────────────────────┘
                                      │
                                      ▼
                  ┌────────────────────────────────────────┐
                  │       2. Delight & Positive Capture     │
                  │ Night mode burst / One-Tap AI Enhance  │
                  └───────────────────┬────────────────────┘
                                      │
                                      ▼
                  ┌────────────────────────────────────────┐
                  │       3. Natural Sharing & Referral    │
                  │ Subtle "Shot on OptiLens" deep links   │
                  └───────────────────┬────────────────────┘
                                      │
                                      ▼
                  ┌────────────────────────────────────────┐
                  │        4. Trust & Review Flywheel      │
                  │ Review prompt only after positive value│
                  └───────────────────┬────────────────────┘
                                      │
                                      ▼
                  ┌────────────────────────────────────────┐
                  │         5. Organic Play Store Rank     │
                  │ >4.6 stars, high ASO search visibility │
                  └───────────────────┬────────────────────┘
                                      │
                                      └─────► (Back to 1: New Organic Users)
```

### A. Activation Loop (First 60 Seconds)
1. **Zero-Friction Permissions**: Onboarding prompts for camera permission with clear contextual justification.
2. **Sub-500ms Viewfinder Initialization**: CameraX preview streams immediately with auto-exposure and auto-focus active.
3. **Instant Gratification**: The first capture delivers clean, balanced exposure with real-time shutter feedback and haptic confirmation.

### B. Natural Sharing & Referral Loop
- **Voluntary Watermark Branding**: When exporting or sharing enhanced photos, users can optionally include a sleek, minimalist `"Shot on OptiLens"` stamp.
- **Deep Link Campaigns**: Shared links resolve to `https://optilens.app/enhance` or `optilens://camera`, routing new users directly to Google Play or immediately opening the relevant camera mode for existing users.
- **Strictly No Forced Sharing**: Users are never required to share or invite contacts to unlock core functionality.

### C. In-App Review Flywheel (`ReviewEligibilityGate`)
Reviews are requested **only** when a user has experienced demonstrable value:
- **Requirement 1**: At least 5 successful photo captures OR at least 3 kept AI Enhancements.
- **Requirement 2**: App installed for at least 48 hours.
- **Requirement 3**: 30-day cooldown between review prompt presentations.
- **Requirement 4**: Absolute suppression for at least 1 hour if a processing error or camera failure occurs.
- **Result**: High conversion to authentic 5-star ratings without deceptive prompts or incentives.

---

## 2. Organic App Store Optimization (ASO)

### A. Multi-Language Tiered Expansion
To unlock global organic search traffic, OptiLens provides native localized strings across primary Android photography markets:
- **Tier 1 (Core Volume)**: English (US/UK/IN), Spanish (LATAM/ES), German (DACH), French (FR/CA), Japanese (JP), Hindi (IN).
- **Localized Keyword Buckets**:
  - *English*: "pro camera", "manual raw camera", "night mode burst", "ai photo enhance", "super resolution zoom".
  - *Spanish*: "cámara profesional", "fotos raw dng", "modo noche", "mejora fotos ia".
  - *German*: "profi kamera", "raw dng aufnahme", "nachtmodus burst", "ki fotoverbesserung".
  - *Japanese*: "プロカメラ", "RAW撮影", "夜景モード", "AI写真補正", "超解像ズーム".
  - *French*: "appareil photo pro", "capture raw dng", "mode nuit", "amélioration photo ia".
  - *Hindi*: "प्रो कैमरा", "नाइट मोड फ़ोटो", "AI फ़ोटो सुधार", "RAW कैमरा".

### B. Rating Velocity & Conversion Optimization
- Target Google Play rating: **$\ge 4.6$ stars**.
- Play Store screenshot design strictly mirrors genuine application UI with legible value-oriented headlines.
- Regular updates highlighting concrete performance gains (e.g. "20% faster multi-frame alignment on Snapdragon chipsets").

---

## 3. Safe Experimentation & Cohort Telemetry

### A. Client-Side Deterministic A/B Testing (`ExperimentationManager`)
- Assigns anonymous cohorts (`control`, `variant_a`, `variant_b`) using a local install seed hash.
- Zero user tracking, zero advertising IDs, zero persistent cross-app identifiers.
- Dynamically receives server-side kill switches and split parameters via `RemoteConfigRepository` and `FeatureFlags`.

### B. Non-Sensitive Device Cohort Telemetry (`DeviceCohortMetadata`)
- Telemetry categorizes devices by hardware capabilities without personal identifiers:
  - SoC Families: Snapdragon, Tensor, MediaTek, Exynos, Unisoc.
  - RAM Buckets: $\le 3\text{GB}$, $4\text{GB}$, $6\text{GB}$, $8\text{GB}$, $12\text{GB}$, $>12\text{GB}$.
  - Android API Levels: API 26 through API 37.
- Enables granular detection of capture latency regressions or hardware-specific quirks before they impact store ratings.

---

## 4. Respectful, Non-Intrusive Monetization Funnel

Monetization is designed to sustain long-term engineering development without interrupting the creative shooting process:

| Principle | Implementation |
|---|---|
| **Free-Tier Generosity** | Full manual controls, standard JPEG capture, basic computational HDR, and standard AI enhance are 100% free forever. |
| **No Viewfinder Ads** | Zero advertising banner, interstitial, or video ads in the camera viewfinder or during capture moments. |
| **High-Intent Pro Gate** | Pro subscriptions (`optilens_pro_annual`, `optilens_pro_lifetime`) unlock advanced power-user features: uncompressed 14-bit RAW DNG, 4x Pro AI Super-Resolution, and batch AI restoration. |
| **Frequency Capping** | Interstitial ads on free tier enforce a minimum 5-minute cooldown (`adFrequencyIntervalSec = 300`) and require at least 3 captures between impressions. |

---

## 5. Growth Milestones & Target KPIs

```
Launch (Month 1)             Growth (Month 3)            Scale (Month 6)
  50,000 Installs              250,000 Installs            1,000,000 Installs
  D1 Retention: > 80%          D7 Retention: > 45%         D30 Retention: > 28%
  Crash-Free: > 99.8%          Play Rating: > 4.6★         Organic Referral: > 22%
```

1. **Phase A: Soft Launch & Diagnostic Calibration (0–50k downloads)**
   - Monitor camera readiness latency across top 20 global Android handsets.
   - Optimize AI Enhance keep rate ($\ge 85\%$).
   - Confirm zero PII leakage via `DiagnosticsExportManager` audits.

2. **Phase B: Global Organic Scaling (50k–250k downloads)**
   - Roll out localized Play Store store listings in 6 languages.
   - Activate deep link referral loops for photo exports.
   - Leverage `ReviewEligibilityGate` to establish early 4.6+ star foundation.

3. **Phase C: 1M Download Milestone (250k–1M+ downloads)**
   - Continuous algorithm tuning via hardware cohort telemetry.
   - Self-sustaining organic ASO rank in Photography category top charts.
