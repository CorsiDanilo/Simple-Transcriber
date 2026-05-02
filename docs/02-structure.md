# 02 - Project Structure 🏗️

The project follows a standard Android structure with a focus on logical separation of the transcription logic from the UI.

## Package Breakdown

### `com.anomalyzed.simpletranscriber`

- **`MainActivity.kt`**: The entry point of the application. Handles UI navigation and the main Compose container.
- **`MainViewModel.kt`**: Manages the state of the model catalog and settings.
- **`TranscriberViewModel.kt`**: Manages the state of the active transcription process.
- **`TranscriptionManager.kt`**: A singleton state holder for the UI to observe the background service state.
- **`TranscriptionService.kt`**: A foreground service that manages the lifecycle of a transcription task, ensuring it completes even if the UI is closed.

### `com.anomalyzed.simpletranscriber.data`

- **`AppDatabase.kt`**: Room database configuration.
- **`TranscriptionDao.kt` / `TranscriptionItem.kt`**: Data access object and entity for the transcription history.
- **`PreferenceManager.kt`**: Manages user settings using Jetpack DataStore.
- **`ModelRepository.kt`**: Handles fetching the model catalog from GitHub and downloading LiteRT models.
- **`ModelInfo.kt`**: Data classes for model metadata.

### `com.anomalyzed.simpletranscriber.engine`

This package contains the abstraction for transcription:

- **`TranscriptionEngine.kt`**: Interface defining the core capabilities (transcribe, refine, release).
- **`CloudEngine.kt`**: Implementation using Google's Gemini API.
- **`LiteRTEngine.kt`**: Implementation using on-device LiteRT-LM.
- **`AICoreEngine.kt`**: (Placeholder/Future) Implementation using Google's system-level AICore.

### `com.anomalyzed.simpletranscriber.ui`

- **`screens/`**: Individual Compose screens (Main, History, Settings).
- **`components/`**: Reusable UI components (Dialogs, List items, etc.).
- **`theme/`**: Material 3 theme configuration and color palettes.

### `com.anomalyzed.simpletranscriber.updater`

- **`AppUpdater.kt`**: Logic to check for new releases on GitHub.
- **`DownloadReceiver.kt`**: Handles the installation of the downloaded APK.

## Resource Files

- **`assets/`**: Contains the global `models.json` used by the app if hosted locally.
- **`res/`**: Standard Android resources (icons, strings, XML).
