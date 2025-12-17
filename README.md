# AudioCutter

AudioCutter is an Android-first audio trimmer that lets you import a clip, preview a selection, apply speed or codec tweaks, and export a polished file ready for ringtones, notifications or loops. It combines Jetpack Compose UX, FFmpegKit trimming, and lightweight AdMob monetization so the feature set stays focused and responsive.

### Highlights
- Jetpack Compose + Material 3 for responsive mobile-only layouts with waveform preview, chip controls, and contextual dialogs.
- FFmpegKit-backed trimming + waveform generation with selectable output formats (MP3, AAC/M4A, OGG, FLAC and WAV), quality presets, and configurable speed.
- Integrated AdMob banner & interstitial hooks (keys live in `app/build.gradle`) so you can swap demo IDs with your production unit IDs.
- Easy-to-understand ViewModel state (`AudioCutterViewModel`) powering playback, trimming, waveform loading and user messaging.
- Sample assets such as `autunno.mp3` (for test imports) and `logo.jpeg` for quick marketing/preview needs.

## Getting started
1. **Prerequisites**
   - Java 17 (Coherent with the Gradle JVM defined in `app/build.gradle`).
   - Android SDK 35 (compile + target).
   - Android Studio Flamingo or newer if you want visual editing; otherwise the Gradle CLI via `./gradlew`.
   - A `local.properties` file pointing to your SDK directory (Android Studio normally creates this automatically).

2. **Clone & inspect**
   ```bash
   git clone <repo-url>
   cd audio_cutter
   ```
   The Compose sources live under `app/src/main/java/com/workwavestudio/audiocutter`, resources under `app/src/main/res`, and the trimming logic inside `AudioTrimProcessor`.

3. **Build and run**
   - Run on emulator or device: `./gradlew installDebug` (or open in Android Studio and hit the play button).
   - Assemble a ready-to-install APK: `./gradlew assembleRelease` (release signing uses `keystore.jks`; rotate credentials for your production build).
   - Produce an AAB for Play Store: `./gradlew bundleRelease`.

4. **Testing**
   - Unit tests: `./gradlew test`
   - Instrumented/compose UI tests (requires device/emulator): `./gradlew connectedAndroidTest`

5. **Documentation**
   - `docs/USER_GUIDE.md`: step-by-step instructions for end users and QA team expectations.
   - `docs/DEVELOPER_GUIDE.md`: environment setup, dependency notes, architecture overview, and release reminders.

## App structure
- `AudioCutterViewModel` handles UI state, playback via `MediaPlayer`, trimming requests and waveform generation.
- `AudioTrimProcessor` copies the chosen audio to cache, invokes FFmpegKit for trimming and waveform data, and returns an Android `Uri`.
- Compose UI sits in `app/src/main/java/com/workwavestudio/audiocutter/ui` with the screen, helper cards, custom waveform canvas, and a small Ads interop view (`BannerAd`).
- AdMob keys are injected via `BuildConfig` fields defined inside `app/build.gradle`, which differentiates production vs debug IDs.
- `res/strings.xml`, `colors.xml`, and `themes.xml` drive the Material theming used across cards/buttons/focus states.
- Assets such as `logo.jpeg` and `autunno.mp3` live in the repository root for easy reference.

## Customization notes
- Keep your real AdMob units in `admob.properties` (gitignored). Copy `admob.properties.example`, populate the four keys for production (`BANNER_AD_UNIT_ID`, `INTERSTITIAL_AD_UNIT_ID`) and debug (`BANNER_AD_UNIT_ID_DEBUG`, `INTERSTITIAL_AD_UNIT_ID_DEBUG`), and keep the resulting file private—the build script loads these values automatically.
- To switch to your own AdMob credentials, update `BANNER_AD_UNIT_ID` and `INTERSTITIAL_AD_UNIT_ID` in the `release` and `debug` build types inside `app/build.gradle`.
- The FFmpeg dependency currently pulls `com.moizhassan.ffmpeg:ffmpeg-kit-16kb:6.0.0`; uncomment the local `ffmpeg-kit-full-gpl-6.0-2.LTS.aar` block if you prefer the full GPL build.
- Keep the `keystore.jks` file in a secure place and rotate the passwords before publishing; update the signing config if the keystore moves or the alias changes.

## License
This project is available under the MIT license. See [LICENSE](LICENSE) for details.
