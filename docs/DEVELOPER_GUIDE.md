# Developer Guide

This guide captures the configuration, architecture, and build practices for the AudioCutter Android app. Follow this document before you invest time modifying core features or publishing a new build.

## Environment setup
1. **Toolchain**
   - JDK 17 (Gradle and Compose assume a Java 17 toolchain; configure `org.gradle.java.home` in `gradle.properties` if necessary).
   - Android SDK 35 with Build Tools 35.0.0+.
   - Android Studio Flamingo or newer for Compose previews, safe args, and layout inspection.
2. **Project files**
   - `local.properties` must point to your SDK install (Android Studio writes this automatically).
   - `autunno.mp3` at the repo root can be used while debugging the import flow without extra downloads.
   - `keystore.jks` contains the current signing keys; replace this file and the passwords in `app/build.gradle` before publishing to any third party.
   - Copy `admob.properties.example` to `admob.properties`, populate the four `ca-app-pub` values (production + debug), and never commit the generated file; `app/build.gradle` loads the private IDs automatically.

## Building & testing
- Command-line builds:
  - `./gradlew assembleDebug` or open the project in Android Studio for fast iteration.
  - `./gradlew assembleRelease` builds a signed APK with shrinkResources/minification enabled.
  - `./gradlew bundleRelease` produces an AAB for Play Store delivery.
- Testing:
  - Run unit tests with `./gradlew test`.
  - Run instrumentation/compose tests with `./gradlew connectedAndroidTest` on a connected device or emulator.
- Use `./gradlew lint` or `./gradlew lintDebug` / `lintRelease` to inspect warnings; lint failures are disabled for release builds to prevent build abortion.

## Architecture overview
- **Jetpack Compose UI** is located under `app/src/main/java/com/workwavestudio/audiocutter/ui`. The `AudioCutterScreen` composable orchestrates cards, controls, waveform rendering, permissions, and AdMob hooks.
- **State management** lives entirely in `AudioCutterViewModel`. Observers subscribe via `viewModel()` and `derivedStateOf` to react to trimming, playback, and snackbar messages.
- **Media processing** is encapsulated in `AudioTrimProcessor`. It copies the selected URI into cache, constructs FFmpegKit commands for trimming or waveform extraction, and returns a `Uri` pointing to the generated file.
- **Ads**:
  - `BannerAd` uses Compose–to–View interoperability.
  - Interstitial ads are loaded at screen startup and shown when trimming completes, with callbacks that reload new ads.
  - `BuildConfig.BANNER_AD_UNIT_ID` and `INTERSTITIAL_AD_UNIT_ID` (defined per build type) contain the AdMob IDs.
- **Waveform rendering** uses a custom `Canvas` inside `WaveformView`, drawing evenly spaced bars, selection overlays, and current playhead lines.

## Dependencies
- Compose BOM `androidx.compose:compose-bom:2023.08.00`.
- `com.moizhassan.ffmpeg:ffmpeg-kit-16kb:6.0.0` provides the runtime trimming and waveform extraction. Uncomment the `implementation files("libs/ffmpeg-kit-full-gpl-6.0-2.LTS.aar")` line and remove the Maven dependency if you need the full GPL build with more codecs.
- `com.google.android.gms:play-services-ads:22.6.0` + `com.google.accompanist:accompanist-flowlayout:0.28.0`.
- Google Play Services metadata is declared in `AndroidManifest.xml` as `APPLICATION_ID` + AdMob `meta-data`.

## Release checklist
1. Verify `keystore.jks` location and passwords inside `app/build.gradle`. Replace placeholder strings (`Pizza0011!`, `key0`) before a public release.
2. Replace debug AdMob IDs (`ca-app-pub-3940...`) with production units in the `debug` block if you want to test the production experience early; keep sandbox IDs separate.
3. Run `./gradlew clean assembleRelease` to confirm shrinkResources/minifyConfig.
4. Inspect the generated APK/AAB in `app/build/outputs` before uploading.
5. Document release notes in `CHANGELOG.md` (not part of this repo yet; add if needed).

## Working with samples & assets
- Sample audio (`autunno.mp3`) and `logo.jpeg` are for QA or marketing; use them to verify import and sharing flows.
- Compose theme files live in `app/src/main/java/com/workwavestudio/audiocutter/ui/theme`; edit `Color.kt`, `Type.kt`, or `Theme.kt` when refreshing the palette.
- Replace the AdMob app ID (`ca-app-pub-230603......`) inside `AndroidManifest.xml` with your own if the app moves to a different AdMob account.

## Next steps
- Use `git status` regularly and keep workspace clean; the project ignores `.iml`, `.gradle`, `build/`, `local.properties`, and keystore artifacts.
- Reference `docs/USER_GUIDE.md` when verifying flows on physical devices.
