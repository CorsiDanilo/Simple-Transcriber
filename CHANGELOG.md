# Changelog

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
