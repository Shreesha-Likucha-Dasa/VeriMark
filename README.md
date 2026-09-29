# VeriMark

VeriMark is a fully local, offline Android app for reviewing video and audio recordings. Open a recording, play it back, tap a single button to mark important moments with labels, then export a structured PDF report.

Everything runs on-device. There is **no network, cloud, or telemetry code** — the app does not even request the `INTERNET` permission.

## Features

- **Video playback** — Media3 ExoPlayer (`PlayerView`) with exact-frame seeking (`SeekParameters.EXACT`), opening paused by default.
- **Audio playback** — the same marker workflow for MP3, WAV, M4A/AAC, and OGG/Opus audio, with a dedicated audio player (progress, current time, total duration, play/pause, ±10s seek).
- **MARK MOMENT** — a prominent button captures the current playback position, pauses playback, and saves a labeled marker to a local Room database.
- **Timeline** — all markers listed with `MM:SS` timestamps in pill badges; tap a row to seek exactly to that moment.
- **PDF reports** — native `android.graphics.pdf.PdfDocument` generates a report and shares it via `FileProvider`.
- **Projects** — a master list of cases with title, date, media type, and marker count; create, open, and delete projects.
- **Portable project packages** — export/import a self-contained `.verimark` package containing the recording, markers, and project information.
- **Share-to-app** — share a video or audio file into VeriMark from any app; it opens as a new project.
- **Local-first storage** — Room database with all data stored on-device.

## Portable `.verimark` format

A `.verimark` file is a ZIP package containing:

```
project.json
media/
    recording.mp4
```

`project.json` stores a versioned schema (`formatVersion`), the project title, media type, and the marker list. Media bytes are streamed in and out (never fully loaded into memory), so large recordings stay responsive. Imported media is copied into VeriMark's private storage, so the project keeps working even if the original `.verimark` file is deleted.

Legacy metadata-only JSON backups can still be imported.

## Tech stack

- Kotlin
- Jetpack Compose + Material 3
- Media3 ExoPlayer (`media3-exoplayer`, `media3-ui`)
- Room (KSP)
- Android `PdfDocument` + `FileProvider`
- Storage Access Framework (persistable URI permissions)
- Navigation Compose
- `java.util.zip` for portable packages

## Requirements

- Android Studio (Ladybug or newer)
- JDK 17 or 21
- Android SDK 36
- Android Gradle Plugin 8.9.1 / Gradle 8.11.1

## Build

1. Open the project folder in Android Studio.
2. Let Gradle sync download the dependencies.
3. Press **Run** on a device or emulator (API 24+).

Or from the command line:

```bash
./gradlew assembleDebug      # debug APK
./gradlew bundleRelease      # release AAB (signing required for Play)
```

## Privacy

VeriMark does not collect, transmit, or share any data. Cloud backup and device transfer are disabled. See the [Privacy Policy](privacy/index.html).

## License

Example local-only tooling; no license specified.
