# 05 - Transcription Engines ⚙️🧠

The heart of the application is its multi-engine transcription strategy.

## `TranscriptionEngine` Interface

Every engine must implement the `TranscriptionEngine` interface:

```kotlin
interface TranscriptionEngine {
    suspend fun transcribe(
        audioBytes: ByteArray,
        mimeType: String,
        language: String,
        onProgress: (String) -> Unit,
        onPartialText: (String) -> Unit
    ): TranscriptionResult

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
- **Backend**: Google Generative AI (Gemini Flash/Pro).
- **Pros**: extremely high accuracy, handles long audio well, provides state-of-the-art text refinement.
- **Cons**: Requires internet connection and an API key.

### 2. LiteRT On-Device Engine (`LiteRTEngine`)
- **Backend**: LiteRT (TensorFlow Lite) with Large Language Model optimization.
- **Pros**: 100% private, works offline, no API costs.
- **Cons**: Higher battery/RAM usage, accuracy depends on the model size (2B vs 4B).

## The Refinement Pass

After the initial transcription, the app can run a "Refinement" pass. This is currently implemented using the Gemini API. It takes the raw (often messy) transcript and fixes:
- Punctuation and capitalization.
- Common transcription errors.
- Grammatical structure.

This pass is streaming, meaning the user sees the text "evolving" into the final corrected version in real-time.

## Resource Management

Transcription is a heavy task. The app uses:
- **Foreground Service**: To prevent the OS from killing the process.
- **Foreground Notification**: Updates progress and appears immediately when the dialog is sent to background.
- **Wakelocks**: (via Service) To keep the CPU active during processing.
- **Explicit Release**: The `release()` method is called in a `finally` block to ensure native memory used by LiteRT is freed immediately.
