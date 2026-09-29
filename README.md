# VeriMark

VeriMark is a fully local, offline Android app for insurance and claims investigators. Open a video, play it back, tap a single button to mark incident timestamps with labels, then export a structured PDF report.

Everything runs on-device. There is **no network, cloud, or telemetry code** — the app does not even request the `INTERNET` permission.

## Features

- **Video playback** — Media3 ExoPlayer (`PlayerView`) with exact-frame seeking (`SeekParameters.EXACT`), opening paused by default.
- **Incident markers** — a prominent "MARK INCIDENT" button captures the current playback position, pauses playback, and saves a labeled marker to a local Room database.
- **Timeline** — all markers listed with `MM:SS` timestamps in pill badges; tap a row to seek exactly to that moment.
- **PDF reports** — native `android.graphics.pdf.PdfDocument` generates a report and shares it via `FileProvider`.
- **Projects** — a master list of cases with title, date, and marker count; create, open, and delete projects.
- **JSON backup** — export/import the current project (case + markers) as a JSON file.
- **Share-to-app** — share a video into VeriMark from any app; it opens as a new project.
- **Local-first storage** — Room database with all data stored on-device.

## Tech stack

- Kotlin
- Jetpack Compose + Material 3
- Media3 ExoPlayer (`media3-exoplayer`, `media3-ui`)
- Room (KSP)
- Android `PdfDocument` + `FileProvider`
- Storage Access Framework (persistable URI permissions)
- Navigation Compose

## Requirements

- Android Studio (Ladybug or newer)
- JDK 17 or 21
- Android SDK 35

## Build

1. Open the project folder in Android Studio.
2. Let Gradle sync download the dependencies.
3. Press **Run** on a device or emulator (API 24+).

The Gradle distribution is pinned in `gradle/wrapper/gradle-wrapper.properties` (Gradle 8.9).

## Privacy

VeriMark does not collect, transmit, or share any data. See the [Privacy Policy](privacy/index.html).

## License

Example local-only tooling; no license specified.
