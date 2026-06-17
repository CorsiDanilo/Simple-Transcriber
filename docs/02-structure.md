[⬅ Previous](./01-overview.md) | [🏠 Index](./README.md) | [Next ➡](./03-setup.md)

# 02 - Project Structure

The project follows a standard Android layout with a focus on separating UI, transcription orchestration, engine implementations, and persistence.

## Package Breakdown

### `com.anomalyzed.simpletranscriber`

- **`MainActivity.kt`**: Entry point, Compose container, share-flow handling, notification re-entry handling, and update dialogs.
- **`MainViewModel.kt`**: Settings, history, model catalog, and update-related UI state.
- **`TranscriberViewModel.kt`**: Starts transcription jobs, selects active jobs, refreshes notifications, and sends cancel commands.
- **`TranscriptionManager.kt`**: In-memory state holder for active/completed transcription tasks. It tracks a selected task plus a map of task states keyed by transcription id.
- **`TranscriptionService.kt`**: Foreground service that runs transcription jobs, supports multiple concurrent tasks, owns per-task notifications, and handles notification actions such as Cancel and Copy.

### `com.anomalyzed.simpletranscriber.data`

- **`TranscriptionData.kt`**: Room database, DAO, and entity definitions for transcription history.
- **`PreferenceManager.kt`**: User settings backed by Jetpack DataStore.
- **`ModelRepository.kt`**: Model catalog metadata, model path resolution, model download, deletion, and storage checks.
- **`ModelInfo.kt`**: Model metadata and download status data classes.

### `com.anomalyzed.simpletranscriber.engine`

- **`TranscriptionEngine.kt`**: Engine strategy interface for transcription, optional refinement, capability flags, and release lifecycle.
- **`CloudEngine.kt`**: Gemini Cloud implementation. It performs transcription and refinement in one request.
- **`LiteRTEngine.kt`**: On-device LiteRT-LM implementation with audio preprocessing, segmentation, and local refinement.
- **`AICoreEngine.kt`**: Placeholder for Android system-level AICore support.

### `com.anomalyzed.simpletranscriber.ui`

- **`TranscriberScreen.kt`**: Setup, progress, streaming, success, error, cancel, copy, and background controls.
- **`screens/`**: History, settings, and model manager screens.
- **`updater/`**: Update dialog UI.
- **`theme/`**: Material 3 theme configuration.

### `com.anomalyzed.simpletranscriber.updater`

- **`AppUpdater.kt`**: Checks GitHub releases and parses release metadata.
- **`DownloadReceiver.kt`**: Handles downloaded APK completion.

## Resource Files

- **`assets/`**: Static assets such as the app icon.
- **`res/`**: Android resources including strings, themes, icons, and XML file providers.

[⬅ Previous](./01-overview.md) | [🏠 Index](./README.md) | [Next ➡](./03-setup.md)