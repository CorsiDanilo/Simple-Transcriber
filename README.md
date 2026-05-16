<p align="center">
  <img src="assets/images/app_icon.png" width="160" height="160" alt="Transcriber Icon">
</p>

# Transcriber

A privacy-focused Android application for high-quality audio transcription and text refinement. Transcriber lets users choose between offline on-device processing with LiteRT-LM and cloud transcription with Gemini.

## Features

- **Hybrid transcription engines**: choose Gemini Cloud for high accuracy or LiteRT-LM for private offline transcription.
- **Single-pass Gemini refinement**: Gemini Cloud now transcribes and refines in one multimodal request, so the app returns the final cleaned text without showing a raw intermediate transcript.
- **Multiple concurrent transcriptions**: each transcription gets its own foreground notification and progress state.
- **Notification controls**: reopen a specific transcription from its notification, cancel an ongoing job, or copy the final transcript directly from the completed notification.
- **Background execution**: send only the Transcriber dialog to background while the source app stays in the foreground.
- **Transcription history**: store, search, copy, and delete previous transcriptions locally with Room.
- **On-device model manager**: download, select, and delete LiteRT-LM models inside the app.
- **In-app updates**: check GitHub releases manually, view markdown changelogs, and install APK updates.
- **English UI labels**: dialogs, history actions, updater text, and notification actions use consistent English wording.

## Tech Stack

| Category | Technology |
| --- | --- |
| UI | Jetpack Compose / Material 3 |
| Cloud AI | Google Generative AI SDK (Gemini) |
| On-device AI | LiteRT / LiteRT-LM |
| Database | Room |
| Preferences | Jetpack DataStore |
| Architecture | MVVM with a foreground transcription service |

## Architecture

```mermaid
graph TD
    UI[Jetpack Compose UI] --> VM[ViewModels]
    VM --> TM[Transcription Manager]
    TM --> Service[Transcription Service]
    Service --> Engines[Engines: Cloud / LiteRT / AICore]
    Service --> DB[(Room Database)]
    Engines --> Gemini[Gemini API]
    Engines --> LiteRT[Local LiteRT-LM Models]
```

- **UI**: Compose screens and dialogs for setup, progress, history, settings, and model management.
- **TranscriptionManager**: shared in-memory state for active and completed transcription jobs.
- **TranscriptionService**: foreground service that runs one or more transcription jobs and owns their notifications.
- **Engines**: strategy implementations for Gemini Cloud, LiteRT-LM, and future AICore support.
- **Data layer**: Room for history and DataStore for preferences.

## Getting Started

### Prerequisites

- Android Studio Ladybug or newer.
- Android SDK 35.
- A device or emulator running Android 8.0 (API 26) or higher.

### Installation

1. Clone the repository:

   ```bash
   git clone https://github.com/CorsiDanilo/simple-transcription-app
   ```

2. Open the project in Android Studio.
3. Add your Gemini API key to `local.properties` if you want cloud features:

   ```properties
   GEMINI_API_KEY=your_api_key_here
   ```

4. Sync Gradle and run the app.

## Usage

1. Choose an engine in Settings: Gemini Cloud, LiteRT-LM, or AICore where available.
2. Share an audio file to Transcriber or start from the app flow.
3. Watch progress in the dialog or notification.
4. Run several transcriptions in parallel when needed; each job has its own notification.
5. Copy the completed text from the dialog, history, or completion notification.

## Release Process

Releases are versioned in `app/build.gradle.kts` and documented in `CHANGELOG.md`. Pushing a tag such as `v1.0.3` triggers the GitHub Actions release pipeline.

## License

This project is licensed under the MIT License.
