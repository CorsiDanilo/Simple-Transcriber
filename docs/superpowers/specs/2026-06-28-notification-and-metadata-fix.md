# Notification & Metadata Implementation Plan

## Goal
1. **Fix Notification Disappearance/Action bugs**: Ensure every transcription job maintains its own notification even if another job starts or if it fails. Remove redundant refresh intents that cause `startForeground` churn.
2. **Add Metadata**: Track the `engineMode` and `modelName` for each transcription, display them in the completion dialog, and show them in the History screen.

## Proposed Changes

### 1. Service Lifecycle & Notifications
- **`TranscriptionService.finishServiceIfIdle`**: Instead of only stopping the foreground service when all jobs are empty, we will **always** call `stopForeground(STOP_FOREGROUND_DETACH)` when a job finishes. This instantly makes the completed job's notification dismissible and prevents the OS from deleting it when `startForeground` is subsequently called for the next job.
- **Redundant Refresh Removal**: 
  - Delete `ACTION_REFRESH_NOTIFICATION` logic in `TranscriptionService`. The Service already natively updates its notification on `onPartialText` and `onProgress` callbacks.
  - Delete `TranscriberViewModel.refreshNotification`.
  - Remove the background collector block in `MainActivity` that spammed `refreshNotification`.

### 2. Data Model & Database Migration
- **`TranscriptionItem`**: Add `val engineMode: String? = null` and `val modelName: String? = null`.
- **`AppDatabase`**: Bump version from 1 to 2. Add `MIGRATION_1_2` to `Room.databaseBuilder` which runs:
  `ALTER TABLE transcriptions ADD COLUMN engineMode TEXT`
  `ALTER TABLE transcriptions ADD COLUMN modelName TEXT`

### 3. State Propagation
- **`TranscriberUiState.Success`**: Add `engineMode` and `modelName`.
- **`FinalState.Success`**: Add `engineMode` and `modelName` so they survive process death.
- **`TranscriptionStateStore`**: Persist the new fields alongside the text.
- **`TranscriptionService`**: Determine `engineModeStr` and `modelNameStr` when starting the job, and pass them into the `Success` states and the `TranscriptionItem` database insertion.

### 4. UI Updates
- **`TranscriberScreen`** (Success state): Add a small pill or text below the transcribed text indicating "Mode: Cloud | Model: gemini-1.5-flash".
- **`HistoryScreen`**: Add a subtitle to each history item showing the mode and model used.

## User Review Required
> [!IMPORTANT]
> A Room Database migration is required. I will add `MIGRATION_1_2` so existing user data is preserved, but please confirm you are okay with a database version bump.
