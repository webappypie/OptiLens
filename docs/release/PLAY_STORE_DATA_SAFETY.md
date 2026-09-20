# Google Play Console Disclosures & Data Safety Guide

This document contains exact question-by-question responses for completing the Google Play Console **App Content**, **Data Safety**, and **Store Listing** requirements for **OptiLens** (Application ID: `com.webappypie.optilens`).

---

## 1. Permissions Justification & Declarations

| Permission | Category | Justification for Play Console Reviewers |
|---|---|---|
| `android.permission.CAMERA` | Runtime Permission | Core camera functionality. Required to stream camera viewfinder frames, perform on-device scene analysis, and capture high-fidelity still images. |
| `com.android.vending.BILLING` | Install-Time Permission | Monetization. Required to connect with the Google Play Billing API for OptiLens Pro in-app purchase and subscription fulfillment. |

> **Storage Permissions Note:** OptiLens requests **zero** broad storage permissions (`READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE`, or `READ_MEDIA_IMAGES`). Images selected for AI enhancement are retrieved strictly via the privacy-preserving Android Photo Picker (`ActivityResultContracts.PickVisualMedia`). Captured photos are written to public collections via scoped `MediaStore` insertions.

---

## 2. Play Console Data Safety Form Responses

### Data Collection & Sharing Overview

- **Does your app collect or share any of the required user data types?**  
  **Yes** (Diagnostics, App Performance, and Purchase Tokens).
- **Is all of the user data collected by your app encrypted in transit?**  
  **Yes** (All network traffic uses TLS 1.3 / HTTPS).
- **Do you provide a way for users to request that their data be deleted?**  
  **Yes** (Users can reset app data locally via Settings or email support for account/analytics deletion).

### Specific Data Type Declarations

#### A. Photos and Videos
- **Data Collected?** **No**.
- **Data Shared?** **No**.
- **Explanation:** User photos and videos are processed **100% on-device** using local ISP, CPU, GPU, and NPU computational photography pipelines. Photos are **never** transmitted to remote servers, cloud AI providers, or third parties.

#### B. Financial Info (Purchases & Transactions)
- **Data Collected?** **Yes** (Purchase history / Entitlement status).
- **Data Shared?** **No**.
- **Purposes:** App functionality, Account management.
- **Collection Mechanism:** Handled directly by Google Play Billing. OptiLens only receives and stores the Google Play purchase token and product ID to unlock Pro features.

#### C. App Info and Performance (Diagnostics & Crash Logs)
- **Crash logs**: **Yes** (Collected via Firebase Crashlytics).
- **Diagnostics / Performance**: **Yes** (ANR rates, startup latency, pipeline stall events).
- **Purposes:** Analytics, Developer communications, Fraud prevention & Security.
- **Ephemeral?** No (stored securely in Firebase for diagnostic aggregation).
- **User opt-out?** Yes (OptiLens provides a global "Share Diagnostic Data" toggle in Settings).

#### D. Device or Other IDs
- **Advertising ID (AAID)**: **Yes** (If user has personalized ads enabled via Google Mobile Ads SDK).
- **Purposes:** Advertising or marketing, Analytics.
- **Ephemeral?** No (governed by Google Mobile Ads SDK privacy sandbox).

---

## 3. Advertising & Monetization Declaration

- **Does your app contain ads?**  
  **Yes**.
- **Placement Policy:**
  - OptiLens strictly complies with the Google Play Families and Intrusive Ads Policy.
  - Ads are displayed **only** on the photo review / enhancement screen.
  - **Zero** ads in the live camera viewfinder.
  - **Zero** ads triggered immediately upon pressing the shutter button.
  - Frequency capped to prevent repeated back-to-back interruptions.
- **Ad IDs:**
  - Development builds use Google sample ad unit IDs (`ca-app-pub-3940256099942544~3347511713`).
  - Production builds load real ad units dynamically via Remote Config.

---

## 4. Google Play Billing Products

| Product ID | Product Type | Description | Grace Period / Pricing |
|---|---|---|---|
| `optilens_pro_lifetime` | One-Time Purchase (In-App) | Lifetime unlock of all Pro features, uncompressed RAW capture, Super Resolution, and ad-free experience. | Tier 3 ($24.99 USD equivalent) |
| `optilens_pro_annual` | Auto-Renewable Subscription | Annual access to all Pro features with 7-day free trial. | Tier 2 ($14.99/year USD equivalent) |

---

## 5. Store Claims, Branding & Realistic Positioning

Google Play strictly prohibits deceptive claims regarding camera capabilities. OptiLens adheres to honest, verifiable marketing copy:

| Deceptive / Disallowed Claim | OptiLens Honest / Verified Claim |
|---|---|
| ❌ "100x AI Super-Zoom reveals hidden details!" | ✅ "High-fidelity AI Super Resolution improves digital crop sharpness without inventing synthetic pixels." |
| ❌ "Replaces night sky with crystal clear 8K moon!" | ✅ "Moon Assist optimizes exposure, shutter speed, and contrast to capture real lunar surface details without synthetic overlays." |
| ❌ "Turns your 12MP sensor into 200MP DSLR quality!" | ✅ "Multi-frame fusion and temporal denoising extract maximum dynamic range and fine texture from your camera sensor." |
| ❌ "Cloud AI enhances every photo instantly!" | ✅ "100% private, on-device computational photography that respects your battery, data plan, and privacy." |

---

## 6. Privacy Policy & Developer Support

- **Privacy Policy URL:** `https://webappypie.github.io/OptiLens/privacy-policy.html`
- **Support Email:** `support@webappypie.com`
- **Developer / Publisher Name:** WebAppyPie Inc.
- **Physical Address:** Recorded in Play Console developer profile.
- **Target Audience:** Everyone (Ages 13 and above). Not targeted primarily at children under 13.
