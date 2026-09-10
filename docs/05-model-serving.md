[⬅ Previous](./04-training-pipeline.md) | [🏠 Index](./README.md) | [Next ➡](./06-data-schema.md)

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

    suspend fun summarizeAudio(
        audioBytes: ByteArray,
        mimeType: String,
        language: String,
        onProgress: (String) -> Unit
    ): SummarizationResult = SummarizationResult.Error("Summarization not supported by this engine")

    suspend fun summarizeText(
        text: String,
        language: String,
        onProgress: (String) -> Unit
    ): SummarizationResult = SummarizationResult.Error("Summarization not supported by this engine")

    fun release()
}
```

## Available Engines

### 1. Gemini Cloud Engine (`CloudEngine`)

- **Backend**: Google Generative AI SDK.
- **Strengths**: High accuracy, handles multimodal audio directly, supports direct audio summarization and post-transcription summarization.
- **Smart Model Fallback**: Features automatic transparent failover (`executeWithFallback`). Candidate models (e.g. `gemini-2.5-flash`, `gemini-2.0-flash`, `gemini-1.5-flash`) are prioritized newest-first. In case of quota exhaustion (HTTP 429) or transient errors (500, 503, 404), the engine automatically falls back to the next available model, updates the user in real time, and logs the exact model used.
- **Preamble Filtering**: Strips unwanted conversational greetings or preambles, returning pure Markdown content.
- **Refinement model**: `performsRefinementDuringTranscription()` returns `true`, transcribing and refining in a single pass.

### 2. Whisper.cpp On-Device Engine (`WhisperEngine`)

- **Backend**: Embedded C++ Whisper.cpp engine via JNI submodule (`third_party/whisper.cpp`).
- **Strengths**: High quality offline transcription with broad multilingual support.
- **Model formats**: Supports quantized GGML/GGUF models (Tiny, Base, Small, Medium, Large v3 Turbo).

### 3. LiteRT On-Device Engine (`LiteRTEngine`)

- **Backend**: LiteRT-LM models stored on device.
- **Strengths**: Offline processing, strong privacy, zero API costs.
- **Tradeoff**: Higher RAM, CPU, and storage requirements.
- **Refinement model**: Transcription and refinement are separate local steps.

### 4. AICore Engine (`AICoreEngine`)

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

## OpenAPI Specification (Swagger)

```yaml
openapi: 3.0.3

info:
  title: simple-transcription-app API
  version: 1.0.0
  description: Auto-generated OpenAPI specification for simple-transcription-app

paths:
  {}
```

[⬅ Previous](./04-training-pipeline.md) | [🏠 Index](./README.md) | [Next ➡](./06-data-schema.md)