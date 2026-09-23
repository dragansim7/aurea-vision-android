# Aurea Vision (Android)

Native Kotlin + Jetpack Compose rewrite of
[aurea-image-prompt-engine](https://github.com/dragansim7/aurea-image-prompt-engine).

## GitHub Actions APK

Builds on every push to `main` and on **Actions → Build APK → Run workflow**.

1. Open https://github.com/dragansim7/aurea-vision-android/actions
2. Select **Build APK** → **Run workflow**
3. Wait a few minutes
4. Open the run → **Artifacts** → download `aurea-vision-debug`
5. Sideload: `adb install -r app-debug.apk`

Debug-signed. Not for Play Store.

## What it does

- Pick a JPEG / PNG / WebP / GIF from the device
- Measure luminance, contrast, edge detail, frame weight, and a 6-color palette on-device
- Draft a prompt in paragraph, tag, or template form
- Apply Photoreal / Cinematic / Editorial / Product / Concept / Minimal / Fantasy / Luxury presets
- Edit, copy, share, and export `.txt` / `.md`
- Keep readings in local Room history (thumbs only)

## What is stubbed

The web app can call a server-side vision model. This APK does **not**.
The draft is built from measured light, frame, surface, palette, and the selected preset.

## Local build

Requires JDK 17+ and Android SDK (compileSdk 37).

```bash
echo "sdk.dir=/ABS/PATH/TO/Android/Sdk" > local.properties
gradle :app:assembleDebug
```

- applicationId: `com.dragansim.aureavision`
- minSdk 26 / targetSdk 37
