# 01 — Master Product Requirements Document

## 1. Product
**Working name:** AI Camera Pro  
**Final brand:** TBD  
**Category:** Android camera / computational photography  
**Primary promise:** Capture cleaner, sharper, brighter and more professionally balanced photos automatically, without requiring photography expertise.

### Positioning
The product must be positioned as:
> A smart computational camera that improves the shot automatically.

It must not be positioned around deceptive "100x AI zoom", fake sensor claims, fake megapixel claims, or guaranteed superiority over every OEM camera.

## 2. Product principles
1. **One tap first.** Auto mode is the hero.
2. **Photo fidelity before effects.**
3. **Natural before dramatic.**
4. **Device-aware behavior.**
5. **Fast enough to feel like a camera.**
6. **Privacy by default.**
7. **Graceful degradation, never silent failure.**
8. **Real before/after proof.**
9. **No forced account for core camera use.**
10. **No ads in the live viewfinder or immediately after every shutter.**

## 3. Target users
### Primary
- Android users who want better photos without manual controls.
- Mid-range phone owners who notice noise, blur, weak night shots or soft digital zoom.
- Parents, pet owners, travelers, food shooters and casual creators.

### Secondary
- Enthusiasts who want Pro controls, RAW/DNG, exposure tools and more transparent processing.
- Users who want a private on-device enhancement tool for existing photos.

## 4. Jobs to be done
- "Make this normal photo look cleaner without editing."
- "Get a usable photo in low light."
- "Make a crop/zoom look less soft."
- "Capture a moving child/pet with fewer blurry failures."
- "Make a portrait flattering but natural."
- "Compare the original and enhanced image."
- "Give me manual controls when I ask for them."

## 5. Core modes
### 5.1 AI Auto — default
Automatically determines:
- scene class;
- available light;
- subject motion;
- device shake;
- face presence;
- clipping/highlight risk;
- capture frame count;
- exposure strategy;
- denoise strength;
- HDR/fusion strategy;
- sharpening/detail profile;
- color profile.

User sees only minimal contextual hints such as `Night`, `Pet`, `Backlit`, `Hold steady`.

### 5.2 Portrait
- face detection + landmarks;
- subject/background segmentation where supported;
- natural exposure balancing;
- face-aware denoise and sharpening;
- optional mild skin cleanup;
- no default face reshaping;
- controllable bokeh when device/vendor or segmentation path supports it.

### 5.3 Night
- prefer vendor Night Extension when it produces a better verified result;
- otherwise custom burst capture;
- motion-aware frame selection;
- alignment;
- temporal denoise;
- exposure fusion;
- highlight protection;
- color recovery;
- stable "hold still" feedback.

### 5.4 Pro
- ISO;
- shutter speed;
- exposure compensation;
- focus;
- white balance;
- histogram;
- focus peaking where feasible;
- RAW/DNG on supported hardware;
- JPEG/HEIF companion option;
- lens selection.

### 5.5 More
- Food
- Pet
- Document
- Nature
- Wildlife/Bird
- Moon Assist
- Best Shot
- AI Zoom
- Enhance existing photo
- advanced tools as they become production-ready.

## 6. Capture features
### Must-have V1
- front/back camera;
- physical lens discovery and switching;
- tap to focus;
- pinch zoom;
- exposure slider;
- flash/torch behavior appropriate to mode;
- timer;
- grid;
- aspect ratio;
- level/horizon;
- volume-key shutter option;
- location tagging opt-in only;
- quick gallery thumbnail;
- screen brightness handling without corrupting preview perception;
- haptic shutter response;
- optional shutter sound according to platform/device rules;
- orientation-aware EXIF.

## 7. Computational photography
### 7.1 Smart multi-frame acquisition
Capture 3–12 frames based on:
- light;
- ISO;
- motion;
- gyro stability;
- device throughput;
- memory;
- thermal state;
- selected mode.

### 7.2 Frame quality ranking
Score:
- sharpness;
- subject motion;
- camera motion;
- clipped highlights/shadows;
- face eye-state/expression when permitted by current feature;
- focus confidence;
- noise estimate.

### 7.3 Alignment
Use a tiered strategy:
- gyro/timestamp-assisted initial transform;
- image feature / optical-flow refinement;
- local alignment where useful;
- reject frames exceeding alignment confidence thresholds.

### 7.4 Fusion
- robust weighted temporal average/median family;
- ghost-aware masks;
- exposure-aware fusion;
- noise model weighting;
- preserve high-frequency detail only when supported by aligned evidence.

### 7.5 HDR
- exposure bracketing when capture latency and movement permit;
- scene-motion-aware frame count;
- highlight recovery;
- shadow lift with noise-aware limits;
- global + local tone mapping;
- natural output as default.

### 7.6 Denoise
- temporal denoise first when multi-frame evidence exists;
- AI/single-frame denoise as supplementary/fallback;
- face/skin/texture-aware protection;
- prevent waxy/plastic surfaces.

### 7.7 Detail enhancement
- edge-aware sharpening;
- texture-aware strength;
- halo suppression;
- noise-aware thresholds;
- less sharpening on skin;
- more text-edge protection in Document mode.

### 7.8 White balance and color
- camera metadata first;
- classical AWB correction;
- learned AWB only if benchmarked;
- scene-aware color matrix/tone curve;
- skin-tone protection;
- `Natural`, `Balanced`, `Vivid` profiles; `Balanced` default.

## 8. AI intelligence
### Real-time
- scene classification;
- face/landmark detection;
- shake score;
- blur/focus score;
- low-light detection;
- backlight detection;
- lens-dirty heuristic;
- subject-motion score.

### Post capture
- denoise;
- detail enhancement;
- super resolution;
- portrait protection;
- Best Shot ranking;
- deblur when confidence is adequate;
- optional background/reflection tools in later phases.

## 9. AI Zoom
### Principles
- optical lens switching first;
- digital crop second;
- multi-frame SR when source frames support it;
- single-frame SR fallback;
- initial high-confidence target: 2x enhancement;
- later 4x enhancement;
- avoid marketing synthetic detail as sensor-captured detail.

UI quick stops may include 0.5x / 1x / 2x / 5x and device-specific optical points.

## 10. Before/After
Every processed image should support a comparison experience:
- original / enhanced toggle;
- draggable split slider;
- double-tap zoom;
- synchronized pan;
- visible processing profile;
- "Keep original too" setting;
- never destroy the only original before user intent is clear.

## 11. Best Shot
For burst/motion use cases:
- rank sharpness;
- eye openness where robust;
- motion blur;
- exposure;
- expression score only as a ranking aid;
- allow manual alternate-frame selection;
- no face generation or expression synthesis in MVP.

## 12. Specialized modes
### Pet
Short exposure bias, subject-motion detection, fur detail protection.

### Food
White-balance stability, controlled local contrast, restrained saturation, no fake steam/details.

### Document
Perspective detection, edge detection, dewarp/crop, illumination normalization, high legibility, optional OCR only as a separate user action.

### Wildlife/Bird
Tele lens preference, fast shutter strategy, subject tracking box, burst/Best Shot, detail protection.

### Moon Assist
Moon detection, spot exposure, tele lens preference, tripod/stability detection, frame stacking, contrast/detail recovery. Never insert synthetic moon textures.

## 13. Advanced editing tools
These are part of the long-term complete product and are phase-gated:
- AI background cleanup;
- reflection reduction;
- motion/deblur recovery;
- upscaling;
- photo restoration;
- object tracking.

All destructive/generative transformations must provide preview, undo/revert, and clear distinction from the original capture.

## 14. Screens
1. Splash / warm start
2. Permission onboarding
3. Camera
4. Processing state
5. Capture review / before-after
6. Gallery / app captures
7. Photo detail
8. AI tools
9. Pro controls
10. Settings
11. Theme/appearance
12. Storage & privacy
13. Premium
14. About/support/legal
15. Internal diagnostics (debug builds only)

## 15. Onboarding
Keep onboarding short:
1. value statement;
2. camera permission;
3. optional notification/location only if/when feature requires it;
4. capability optimization;
5. camera opens.

No account wall.

## 16. Themes
- Full light and dark themes for app chrome.
- Camera viewfinder uses neutral photographic controls so theme does not bias image perception.
- Respect system theme by default.
- User may choose Light / Dark / System in settings.
- All surfaces meet contrast targets and remain legible over live imagery.

## 17. Offline and privacy
Core capture and core AI processing must work offline after required bundled/on-demand models exist locally.
Photos remain on device by default.
No photo upload for core enhancement.
No face template/profile database.
Analytics must never include image bytes or sensitive photo-derived content.

## 18. Monetization
No subscription required for the main business model.

### Free
- core camera;
- standard auto enhancement;
- standard Night/Portrait;
- full normal export quality;
- basic AI Zoom;
- ads only in safe non-camera placements.

### One-time Pro/Lifetime
- remove ads;
- Max Quality processing;
- stronger SR options;
- advanced Pro/RAW tools;
- premium looks;
- advanced AI tools depending on release maturity.

### Optional one-time packs
- premium filter/looks pack;
- advanced AI enhancement pack.

Never intentionally degrade normal saved resolution solely to force payment.

## 19. Remote control
Firebase Remote Config may control:
- feature flags;
- model rollout;
- processing quality defaults;
- device deny/allow lists;
- thermal thresholds;
- ad enablement;
- active ad provider;
- ad placement enablement/frequency;
- premium promotional copy;
- experiment assignment.

No secrets in Remote Config.

## 20. Analytics
Events include:
- first_open;
- camera_permission_result;
- camera_ready;
- shutter;
- capture_success/failure;
- processing_started/completed/failed;
- mode_used;
- ai_scene_detected (coarse non-sensitive category only);
- before_after_opened;
- enhanced_saved;
- original_saved;
- ai_keep_rate inputs;
- purchase flow events;
- ad events;
- crash/ANR via platform tooling.

Never log file names, photo content, recognized text, face identity, precise GPS, or sensitive scene details to analytics.

## 21. Product KPIs
- crash-free users/sessions;
- ANR rate;
- camera-ready latency;
- shutter-to-preview latency;
- processing latency per mode/device tier;
- processing failure rate;
- AI Keep Rate;
- D1/D7/D30 retention;
- captures per active user;
- premium conversion;
- rating/review trend.

## 22. Non-functional requirements
- no main-thread image processing;
- deterministic resource cleanup;
- bounded memory;
- thermal adaptation;
- pause/cancel when lifecycle requires;
- no corrupted image on interruption;
- robust MediaStore writes;
- restore UI after process death where appropriate;
- edge-to-edge/adaptive layout;
- foldable/tablet-safe UI even though camera remains phone-first;
- localization-ready strings from day one.

## 23. Success criteria
The product is ready for broad launch only when:
- core capture works reliably across Tier A/B/C test devices;
- custom processing visibly improves selected target scenes without widespread artifacts;
- unsupported capabilities degrade gracefully;
- privacy and Play declarations match actual behavior;
- no debug/pro bypass remains in release;
- release AAB passes automated checks;
- representative physical-device tests pass.

## 24. Roadmap
### MVP engine
Auto, multi-frame, HDR/fusion, denoise, color, Night, Portrait, compare.

### V1 Pro
AI Zoom/SR, Pro/RAW, Best Shot, Pet/Food/Document, better device adaptation.

### Growth
Wildlife/Bird, Moon Assist, advanced cleanup/restoration tools, model improvements, localization, store experiments.

### Scale to 1M
Broaden device profiles, creator comparisons, ASO/localization, quality-driven reviews, feature rollout by Remote Config, data-informed retention improvements.
