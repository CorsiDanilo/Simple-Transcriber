[⬅ Previous](./06-data-schema.md) | [🏠 Index](./README.md) | [Next ➡](./08-deployment.md)

# Evaluation & Metrics

This application acts as a client-side transcription tool and does not run continuous model training or evaluation pipelines (such as evaluating Word Error Rate on test sets) at runtime.

## Test Suite

Quality assurance and regression testing are handled via the Kotlin test suite:

### Local Unit Tests
Located in:
*   `app/src/test/java/com/anomalyzed/simpletranscriber/`

These tests verify config loading, string/text normalization, and standard UI ViewModel states. Run them via:
```bash
./gradlew :app:testDebugUnitTest
```

### Instrumented Integration Tests
Located in:
*   `app/src/androidTest/java/com/anomalyzed/simpletranscriber/`

These verify native database (Room) migrations, storage access permissions, and UI Screen rendering on target Android devices or emulators. Run them via:
```bash
./gradlew :app:connectedDebugAndroidTest
```

[⬅ Previous](./06-data-schema.md) | [🏠 Index](./README.md) | [Next ➡](./08-deployment.md)