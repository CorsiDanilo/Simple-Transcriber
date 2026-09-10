[🏠 Index](./README.md) | [Next ➡](./02-structure.md)

# 01 - Project Overview

## Introduction

Transcriber is an Android application for converting audio into readable text while letting users choose where processing happens. Gemini Cloud provides high-accuracy online transcription, while LiteRT-LM supports private on-device workflows.

## Core Philosophy

1. **User Choice**: Users decide whether audio is processed locally or in the cloud.
2. **Reliable Background Work**: Long-running jobs continue through a foreground service with visible notifications.
3. **Practical Controls**: Users can reopen, cancel, and copy transcription results from the dialog or notification.
4. **Maintainable Architecture**: Engines, persistence, UI state, and notifications are separated so new backends can be added without rewriting the app.

## Key Use Cases

- **Meeting Notes**: Transcribe recordings and store the final text in local history.
- **Privacy-Sensitive Content**: Use LiteRT-LM to keep audio on the device.
- **Fast Dictation**: Share audio messages from other apps to Transcriber for quick reading.
- **Parallel Processing**: Start more than one transcription and track each one through its own notification.

## Current Capabilities

- Gemini Cloud transcribes and refines in a single multimodal request.
- Audio and text summarization: direct audio summarization and post-transcription summarization via Gemini with a two-tab review interface.
- Smart Gemini model fallback: automatic failover across models sorted chronologically newest-first for high availability during rate limits.
- Native Markdown rendering: headers, lists, code, and styling rendered cleanly in Jetpack Compose without conversational preambles.
- LiteRT-LM and Whisper.cpp for private, fully offline on-device transcription.
- Multiple active transcription jobs tracked independently with individual notifications.
- Transcription history with tabbed transcript/summary view and dynamic swipe-to-copy.
- Completed notifications showing the final transcript with direct Copy action.

## Future Roadmap

- [ ] Real-time streaming from microphone input.
- [ ] Speaker diarization.
- [ ] Expanded on-device model support, including production AICore integration.
- [ ] Export options for PDF, TXT, and Markdown.

[🏠 Index](./README.md) | [Next ➡](./02-structure.md)