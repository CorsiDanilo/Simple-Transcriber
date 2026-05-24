# 03 - Setup & Installation ⚙️

## Development Environment

To build this project, you will need:

- **Android Studio**: Version **Ladybug (2024.2.1)** or higher.
- **Java**: JDK 17 (embedded with Android Studio).
- **Gradle**: Uses the Kotlin DSL and Version Catalogs (`gradle/libs.versions.toml`).

## Configuration

### 1. Gemini API Key

To use the Cloud transcription and Text Refinement features, you need an API key from [Google AI Studio](https://aistudio.google.com/).

Once you have the key, create or open the `local.properties` file in the root directory and add:

```properties
GEMINI_API_KEY=your_actual_api_key_here
```

The build system automatically reads this value and injects it into the app.

### 2. Signing Configuration (Local)

The project is configured to build a debug version without additional setup. For release builds, the signing is handled by GitHub Actions. If you wish to build a signed release locally, you must modify `app/build.gradle.kts` to point to your `.jks` file.

## Native Dependencies (whisper.cpp)

This project relies on the native `whisper.cpp` library for speech-to-text processing. It is integrated as a **Git Submodule** located in `third_party/whisper.cpp`.

### Fetching or Updating the Submodule

If you are setting up the project for the first time or if the submodule folder is empty:

1. **Initialize and update** the submodule:
   ```bash
   git submodule update --init --recursive
   ```

2. **Update** the submodule to the latest upstream version defined in the repository:
   ```bash
   git submodule update --remote --recursive
   ```

## Building the Project

1. **Import**: Open Android Studio and select "Open" -> choose the project root.
2. **Sync**: Wait for Gradle to download dependencies and sync.
3. **Run**: Select the `app` configuration and press the "Play" button.

### Gradle Tasks of Interest

- `./gradlew assembleDebug`: Build the debug APK.
- `./gradlew assembleRelease`: Build the release APK (requires signing config).
- `./gradlew kspDebugKotlin`: Run the KSP processor (useful for Room debugging).

## Troubleshooting

- **Out of Memory during build**: Ensure you have enough RAM allocated to Gradle in `gradle.properties` (`org.gradle.jvmargs=-Xmx2048m`).
- **LiteRT errors**: Some LiteRT models require a minimum of 4GB of RAM on the device to run efficiently.
