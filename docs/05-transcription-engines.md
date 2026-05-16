# 05 - Transcription Engines

The application uses a strategy interface so transcription backends can be swapped without changing the UI or service orchestration.

## `TranscriptionEngine` Interface

Every engine implements:

```kotlin
interface TranscriptionEngine {
    suspend fun transcribe(
        audioBytes: ByteArray,
        mimeType: String,
        language: String,
        onProgress: (String) -> Unit,
        onPartialText: (String) -> Unit
    ): TranscriptionResult

    fun isAvailable(): Boolean
    fun displayName(): String
    fun performsRefinementDuringTranscription(): Boolean

    suspend fun refineText(
        text: String,
        language: String,
        onPartialText: (String) -> Unit
    ): String

    fun release()
}
```

## Available Engines

### 1. Gemini Cloud Engine (`CloudEngine`)

- **Backend**: Google Generative AI SDK.
- **Strengths**: High accuracy, handles audio directly, and can return refined text in one multimodal request.
- **Tradeoff**: Requires internet access and a Gemini API key.
- **Refinement model**: `performsRefinementDuringTranscription()` returns `true`, so the service does not run a separate refinement pass.

### 2. LiteRT On-Device Engine (`LiteRTEngine`)

- **Backend**: LiteRT-LM models stored on device.
- **Strengths**: Offline processing, stronger privacy, no cloud API cost.
- **Tradeoff**: Higher RAM, CPU, battery, and model storage requirements.
- **Refinement model**: transcription and refinement are separate local steps.

### 3. AICore Engine (`AICoreEngine`)

- Placeholder for future Android system-level on-device AI support.

## Service Orchestration

`TranscriptionService` owns the lifecycle of transcription jobs. Each started job receives a unique `transcriptionId` and independent notification id.

For each job, the service:

1. Creates an immediate foreground notification.
2. Reads the shared audio URI.
3. Creates the selected engine.
4. Streams progress and partial text to `TranscriptionManager`.
5. Updates only that job's notification.
6. Saves the final text to Room on success.
7. Releases engine resources in `finally`.

## Notifications

Each transcription has its own notification:

- Ongoing notifications show progress and include Cancel.
- Tapping a notification reopens the dialog for that specific transcription.
- Completed notifications show the final transcript, use expanded big text, and include Copy.
- Cancelling one transcription does not cancel other active jobs.

## Resource Management

Parallel jobs are supported, but LiteRT jobs can be expensive because each job may initialize model resources. The service keeps foreground status while any job is active and stops itself when all active jobs finish or are cancelled.
