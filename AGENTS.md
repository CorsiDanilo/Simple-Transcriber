# Instructions: Simple Transcription App - Android Offline Audio Transcriber (whisper.cpp)

## Scope & Operational Boundaries
- Never commit `local.properties`, API keys (`GEMINI_API_KEY`), keystores, or passwords.
- Submodule `third_party/whisper.cpp` must remain pinned to commit `9386f23` (v1.8.4) unless an intentional upgrade is requested.
- Supported Android ABIs: `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`.

## Architecture & Data Flow
- Directory responsibilities:
  - `app/src/main/cpp/`: Native CMake configuration and JNI wrapper connecting Kotlin to GGML runtime.
  - `third_party/whisper.cpp/`: Pinned Git submodule containing the core C++ whisper engine.
  - `app/src/main/java/.../engine/`: Audio decoding pipeline (converting MP3, M4A, OGG, WAV to 16kHz PCM) and segmentation.
  - `app/src/main/java/.../whisper/`: Native bridge and Whisper inference runner.
  - `app/src/main/java/.../data/`: Room database (`watranscriber_db`), history entities, and DataStore preferences.
  - `app/src/main/java/.../ui/`: Jetpack Compose UI (Transcriber, History, Model Downloader, Settings).
  - `TranscriptionService.kt`: Foreground service executing long-running transcription jobs with Android notification progress and wake lock.
  - `models.json`: Pinned catalog of GGML models with download URLs, sizes, and validation hashes.

```
[Audio File (Voice Note / Recording)]
                 |
                 v
+------------------------------------+
|        TranscriptionService        | (Android Foreground Service + WakeLock)
+----------------+-------------------+
                 |
                 v
+------------------------------------+
|          engine/ (Decoder)         | (Extracts & resamples audio to 16kHz PCM)
+----------------+-------------------+
                 |
                 v
+------------------------------------+
|       whisper/ (JNI Bridge)        | (Invokes native C++ libwhisper.so)
+----------------+-------------------+
                 |
                 v
+------------------------------------+
|       data/ (Room Database)        | (Stores result in watranscriber_db v2)
+----------------+-------------------+
                 |
                 v
+------------------------------------+
|          ui/ (Compose)             | (Displays transcript, timestamps, export)
+------------------------------------+
```

- Invariant: Audio processing and transcription must run within `TranscriptionService` using background coroutine dispatchers to prevent OS process kills.

## Interfaces & Contracts
- **Audio Processing Contract**:
  - Input: Any supported audio container (WAV, MP3, M4A, AAC, Opus, Ogg).
  - Internal format: Strictly 16kHz mono 16-bit Float/PCM array passed to native Whisper context.
- **Model Definition Schema (`models.json`)**:
  - JSON objects containing `id`, `name`, `sizeBytes`, `downloadUrl`, and `sha256`.

## Domain States, Enums & Decisions
- **Transcription Job State**:
  - `IDLE` -> `PREPARING` -> `DECODING` -> `TRANSCRIBING (0-100%)` -> `COMPLETED` -> `FAILED`.
- **Database Schema**:
  - Room database: `watranscriber_db`, version 2. Changes require manual migration step (`MIGRATION_X_Y`).

## Critical Business Logic & Invariants
- **Gemini API Key Injection**: `local.properties` provides `GEMINI_API_KEY` injected into `BuildConfig` for optional AI summarization features; must never be hardcoded in repository sources.
- **Submodule Initialization**: Native builds fail without `git submodule update --init --recursive`.
- **Native CPU Optimization**: OpenMP and native NEON/AVX optimizations are configured per target ABI inside `app/src/main/cpp/CMakeLists.txt`.

## Persistence & Storage
- SQLite via Room: `watranscriber_db` (v2) storing transcription history, segment timestamps, and audio file metadata.
- Model Storage: Downloaded `.bin` GGML model weights stored in app-private external storage.

## Error Handling
- Native JNI errors surface as Kotlin domain exceptions (`TranscriptionException`).
- Audio decoding corruptions or unsupported codecs caught during preparation, triggering explicit user notifications without crashing the background service.

## Verified Commands
- Init Submodule: `git submodule update --init --recursive`
- Gradle Wrapper Check: `.\gradlew.bat --version`
- Local Debug Build: `.\gradlew.bat :app:assembleDebug`
- Local Release Build: `.\gradlew.bat :app:assembleRelease`
- KSP Room Code Gen: `.\gradlew.bat :app:kspDebugKotlin`
- Android Lint: `.\gradlew.bat :app:lintDebug`
- Unit Test Suite: `.\gradlew.bat :app:testDebugUnitTest`
- Targeted Test: `.\gradlew.bat :app:testDebugUnitTest --tests "com.anomalyzed.simpletranscriber.updater.UpdateIntegrityTest"`
- Format / Autofix: Not configured

## Conventions & Documentation
- Ensure any database alterations include migration paths in `data/AppDatabase.kt`.
- Maintain release notes in `CHANGELOG.md`.
