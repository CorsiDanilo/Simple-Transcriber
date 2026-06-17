[⬅ Previous](./03-setup.md) | [🏠 Index](./README.md) | [Next ➡](./05-model-serving.md)

# Training Pipeline

This project is a mobile transcription application. As such, training models is outside the scope of the local application runtime.

## Pre-trained Models

Instead of training models from scratch, the application utilizes pre-trained automatic speech recognition (ASR) models:

*   **Google Gemini API (Cloud)**: Calls Google's hosted multimodal Generative AI models.
*   **LiteRT (On-Device)**: Loads local pre-quantized TensorFlow Lite / LiteRT Whisper model configurations (stored in `context.filesDir/models`).
*   **Whisper C++ (`whisper.cpp`)**: Supports running optimized local Whisper weights on mobile hardware configurations.

No training, dataset preprocessing, or model fine-tuning logic is included in this repository.

[⬅ Previous](./03-setup.md) | [🏠 Index](./README.md) | [Next ➡](./05-model-serving.md)