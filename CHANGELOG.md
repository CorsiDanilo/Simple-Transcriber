# Changelog

## [1.5.0] - 2026-09-10
### Added
- **AI Audio & Text Summarization**:
  - Direct audio summarization: generate concise, structured bullet-point summaries directly from audio files via Gemini without requiring a full manual transcript read.
  - Post-transcription text summarization: generate summaries from existing transcriptions in one tap.
  - Two-tab dialog ("Transcript" and "Summary") allowing users to view and toggle between transcription and summary effortlessly.
  - Room database schema migration (`MIGRATION_2_3`) with `summary` and `summaryModel` fields, displaying badges and summary views in History.
- **Markdown Rendering**:
  - Full inline and block Markdown rendering in Jetpack Compose for both the result dialog and History items (bold, headers, bullet points, numbered lists, italics, inline code, and blockquotes).
  - Clean output filter that removes conversational preambles (e.g., "Here is the summary...", "Ecco il riassunto...").
- **Smart Gemini Model Fallback with Latest-First Ordering**:
  - Transparent failover mechanism across Gemini models (e.g., `gemini-2.5-flash`, `gemini-2.0-flash`, `gemini-1.5-flash`).
  - Fallback candidates are dynamically sorted chronologically from newest to oldest.
  - Graceful recovery from quota limits (HTTP 429), model unavailability (503/500/404), with real-time UI/notification progress updates and recording of the successful model in the database.
- **Dynamic Contextual Copy in History**:
  - Swipe-to-copy (left-to-right) and multi-selection copy dynamically copy whichever content is actively selected (transcription or summary).
- **Full Localization Parity**:
  - 100% complete string parity between English and Italian across all screens, dialogs, badges, and notification messages.

### Changed
- Streamlined History card interface by removing the redundant copy button in favor of the intuitive swipe-to-copy gesture.

## [1.4.0] - 2026-07-10
### Fixed
- Fixed the in-app updater downloading the debug build variant instead of matching the user's installed build variant (debug or signed/release).

## [1.3.0] - 2026-07-10
### Fixed
- **Duplicate Entries in History**: Resolved a bug where audio transcriptions completed during the share flow (`isShareFlow`) were saved twice in the database, resulting in duplicate history entries (one with engine/model metadata and one without). The redundant manual save in `MainActivity` was removed, leaving the clean, metadata-rich save logic to `TranscriptionService`.

## [1.2.3] - 2026-06-29
### Added
- **App Version Display**: Added the current app version to the Settings screen under the new "About" section.

## [1.2.2] - 2026-06-28
### Added
- Transcription state persistence and new history management UI.

### Fixed
- Resolved edge cases with UI state hijacking and auto-finish in background transcriptions.
- Resolved an issue where tapping the notification after the share flow was unresponsive.
- Fixed the in-app updater not finding new releases due to a repository rename (`simple-transcription-app` to `Simple-Transcriber`).

## [1.2.1] - 2026-06-25
### Fixed
- Background transcription dialog no longer pops up unexpectedly when the app is in the background. A race-condition between the transcription completing and `onStop()` being called could leave the activity alive in a terminal state; both the `lifecycleScope` collector and a new `onStop()` guard now ensure the activity is finished silently on success.
- On **error**, the activity is intentionally kept alive so the user can always return to the app (or tap the notification) to view the error dialog.

### Added
- **Humanized error messages**: technical exception strings are now translated into clear, user-friendly messages (localized in English and Italian). Categories include network issues, invalid/quota-exceeded API key, server unavailability, safety filter blocks, unsupported audio format, missing local model, out-of-memory, and more. Engine-level messages that are already user-friendly are preserved as-is.

## [1.2.0] - 2026-06-09
### Added
- **Full Italian localization**: the app UI, notifications, and dialogs are now fully translated into Italian.
- Language selector in Settings: switch between System Default, English, and Italian at runtime without restarting the app (powered by `AppCompatDelegate.setApplicationLocales`).
- All notification strings (titles, progress messages, action buttons) respect the selected language.

### Changed
- Migrated from a custom DataStore-based `uiLanguage` preference to the standard Android per-app locale API (`AppCompatDelegate`).
- Date formatting in the transcription history now uses the device locale instead of a hardcoded English format.
- Migrated the app theme to `Theme.AppCompat.NoActionBar` for full `AppCompatActivity` compatibility.

### Fixed
- Several UI labels in ModelManagerScreen, TranscriberScreen, and HistoryScreen that were hardcoded in English are now resolved through string resources and appear correctly in Italian when the language is set.

## [1.1.1] - 2026-06-04
### Fixed
- Hardened the APK update flow with release asset integrity checks and signer verification.
- Excluded the Gemini API key datastore from backup and device transfer.
- Marked the download receiver non-exported and tightened update handling.

## [1.1.0] - 2026-05-24
### Added
- Integrated **Whisper.cpp** as a high-quality, fully offline on-device speech-to-text transcription engine.
- Configured models.json with multilingual Whisper models (Tiny, Base, Small, Medium, and Large v3 Turbo) with quantization options (Q5_1, Q8_0, Q5_0).
- Set up `whisper.cpp` codebase as a Git Submodule at `third_party/whisper.cpp` pinned to stable release tag `v1.8.4` to maintain a clean project repository size.
- Updated project documentation with detailed setup, build, and submodule cloning instructions.

### Changed
- Configured `.gitignore` to cleanly exclude Android Studio C++/CMake compilation caches (`.cxx/`, `app/.cxx/`) from version control.
- Restructured `debug` buildType configuration in Gradle to avoid unneeded signing components.

## [1.0.3] - 2026-05-16
### Added
- Multiple concurrent transcriptions now create independent notifications, each tracking its own progress and final state.
- Notification taps reopen the transcription dialog for the selected transcription only.
- Ongoing transcriptions can be cancelled from both the dialog and the notification.
- Completed transcription notifications now show the final text and include a Copy action.

### Changed
- Gemini Cloud transcription now performs transcription and refinement in one multimodal request, avoiding the intermediate raw transcript/refinement flash.
- Automatic update checks are skipped during share-flow transcription and when using local/on-device engines.
- The share-flow background action now dismisses only the Transcriber dialog instead of sending the source app to background.
- User-facing labels and update/history dialogs are now consistently in English.

### Fixed
- Notification startup is gated on notification permission and channel availability, preventing silent background transcription starts.
- Foreground notification handling now keeps separate notification state for parallel transcription jobs.

## [1.0.2] - 2026-05-06
### Added
- Native in-app markdown changelog viewer
- Manual check for updates in Settings
### Fixed
- Missing `Download` directory issue when updating the app
- Misaligned radio buttons in the settings screen

## [1.0.1] - 2026-05-05
### Fixed
- Foreground notification now appears immediately when the transcription dialog is sent to background.
- Notification updates across transcription and refinement states without creating duplicates.

## [1.0.0] - 2026-05-03
### Added
- Initial public release on GitHub.
- GitHub Actions pipeline for signed APK generation.
- In-app update system.
- LiteRT-LM for on-device transcription.
- Gemini Cloud integration for high-quality transcription and refinement.
- Transcription history and settings.
- Background execution: transcriptions continue running seamlessly in the background when navigating back or clicking outside the dialog.
