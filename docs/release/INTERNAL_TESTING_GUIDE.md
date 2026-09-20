# OptiLens — Internal and Closed Testing Play Console Guide

This guide details the exact steps for distributing the **OptiLens v1.0.0 Release Candidate** via Google Play Console internal and closed testing tracks.

---

## 1. Prerequisites Checklist

Before uploading the release bundle to Google Play Console:
1. **Google Play Console Account**: An active developer account with owner or release manager permissions for `com.webappypie.optilens`.
2. **Play App Signing**: OptiLens uses Google Play App Signing (Play App Signing manages the app's signing key and validates the uploaded bundle signed by the upload key).
3. **Artifacts Ready**:
   - `app/build/outputs/bundle/release/app-release.aab`
   - `app/build/outputs/mapping/release/mapping.txt` (deobfuscation mapping)

---

## 2. Setting Up the Internal Testing Track

The **Internal Testing Track** provides instant, unreviewed distribution to up to 100 designated internal testers within seconds of upload.

### Step-by-Step Instructions:

1. **Navigate to Play Console**:
   - Open [Google Play Console](https://play.google.com/console).
   - Select the **OptiLens** application (`com.webappypie.optilens`).
2. **Access Internal Testing**:
   - In the left-hand navigation under **Testing**, click **Internal testing**.
3. **Create New Release**:
   - Click **Create new release** in the top-right corner.
4. **Upload App Bundle (`.aab`)**:
   - Drag and drop `app-release.aab` into the **App bundles** drop zone.
   - Verify that Play Console detects:
     - **Version code**: `1`
     - **Version name**: `1.0.0`
     - **Target API**: `37`
     - **Min API**: `26`
5. **Upload Deobfuscation File (`mapping.txt`)**:
   - In the **App bundle summary** card, click the three-dots menu next to the uploaded bundle $\to$ **Upload deobfuscation file (mapping.txt)**.
   - Select `app/build/outputs/mapping/release/mapping.txt`. This ensures all stack traces in Play Console Crashlytics are fully human-readable.
6. **Enter Release Notes**:
   ```
   OptiLens v1.0.0 Release Candidate 1
   - Complete on-device computational photography pipeline.
   - Smart HDR, temporal denoise, night mode, and pro manual capture.
   - Zero cloud uploads: 100% private, edge-processed photography.
   - Multi-device quirk tuning across major Android flagships, mid-tier, and budget devices.
   ```
7. **Save and Review Release**:
   - Click **Save**, then **Review release**.
   - Confirm there are no blocking errors.
   - Click **Start rollout to Internal testing**.

---

## 3. Creating Closed Testing (Alpha Track)

Once internal smoke testing confirms clean cold start, capture, and gallery storage:

1. Under **Testing**, select **Closed testing**.
2. Create an **Alpha** track or select **Closed testing - Alpha**.
3. Under **Testers**, create an email list or Google Group of target beta testers across diverse OEM devices (Samsung, Google Pixel, OnePlus, Xiaomi, Motorola).
4. Promote the release from Internal Testing to Closed Testing:
   - Go to **Internal testing** $\to$ Click **Promote release** $\to$ **Closed testing**.
5. Submit for standard Google Play policy review (typically approved in 24–48 hours).

---

## 4. Verification Checklist for Testers

- [ ] **First Run**: Clean permission prompt for Camera; verify viewfinder starts within 300ms.
- [ ] **Single & Burst Capture**: Capture 1 shot and 1 burst of 10 shots; verify zero dropped frames.
- [ ] **Low Light / Night Mode**: Capture in dim environment; verify steady hold feedback and clean temporal denoise.
- [ ] **Pro Mode**: Test manual ISO and exposure compensation sliders; verify RAW DNG file write if sensor supports it.
- [ ] **Gallery & Scoped Storage**: Ensure captured photos appear instantly in system Gallery (`DCIM/OptiLens`) without broad storage permissions.
- [ ] **Pro Billing Simulation**: In license tester mode, purchase `optilens_pro_lifetime` with the Google test payment method; confirm instant pro feature unlock.
