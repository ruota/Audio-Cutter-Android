# User Guide

AudioCutter is built for trimmed audio exports that sound natural and are ready to share. This guide explains how a typical session flows and how to handle the controls you see once you open the app.

## Typical workflow
1. **Import an audio file**
   - Tap **Import audio** and grant the runtime permission your device requests (Android 13+ uses `READ_MEDIA_AUDIO`, older systems use `READ_EXTERNAL_STORAGE`).
   - Choose a clip from the document picker; a long-press on the same file later lets you re-open it without repeating the selection.
   - After the file loads you see its name, duration, and output format badges.

2. **Define your trim range**
   - Drag the two thumbs on the waveform slider to set the start and end times, or type precise minute:second values in the text fields below the slider.
   - The app clamps values to the actual clip length, preventing invalid ranges.
   - Slider handles automatically snap when you drag near the edges to avoid jumping outside the selection.

3. **Adjust output format, quality and speed**
   - Choose your file format (MP3, AAC/M4A, OGG, FLAC or WAV).
   - Select a quality preset (High, Standard, Compact); MP3/AAC honor bitrate hints, FLAC/WAV use lossless modes.
   - The speed slider stretches or compresses the audio from 0.1x to 4x. Use it for slow-motion previews or boosting energy.

4. **Preview the trimmed segment**
   - Tap **Listen** to hear the current range; the button toggles to **Stop preview** while playback runs.
   - Playback starts at the selected start time and automatically stops once the trimmed duration ends, factoring in the speed adjustment.
   - Playback position and waveform overlay update in real time.

5. **Trim and export**
   - Tap **Trim audio** to run the FFmpegKit processor in the background.
   - A progress overlay appears while the clip is generated. You can still cancel the process by pressing the system back key if needed.
   - Once complete, a result card appears. Tap **Save to device** to launch the system save dialog and pick any folder accessible via Storage Access Framework.
   - Saving reuses the selected format extension and shows a confirmation snackbar when it finishes copying.

## Controls reference
- **Waveform display** renders downsampled PCM from the selected file; if the waveform fails to compute, you still can drag the range controls.
- **Option chips** (Format/Quality) highlight the current selection; they react immediately and show the active codec in the downstream snackbar message.
- **Speed slider** snaps to one decimal place; use multiples of 0.1x for predictable playback.
- **Result snackbar** reuses friendly paths such as `/storage/emulated/0/Android/data/...` to help you locate files in the system file browser.

## Permissions & Monetization
- Network permissions (`INTERNET`, `ACCESS_NETWORK_STATE`) are needed for AdMob banners and interstitials, which show during the trimming result flow.
- The interstitial is displayed once per export; if the ad fails to load, trimming still reports success and the app resets the banner hook.
- Audio permissions are requested on demand. Granting them once keeps access for future launches thanks to persistable URI permissions.

## Troubleshooting
- If trimming fails, the snackbar surfaces the FFmpegKit error. Common fixes include using a supported codec or freeing storage space.
- Waveform generation may take a few seconds; keep the UI visible and wait for the animation to finish.
- Use the included sample clip `autunno.mp3` (root directory) to verify playback on devices without existing music files.
