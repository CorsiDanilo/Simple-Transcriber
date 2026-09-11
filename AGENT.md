# AGENT.md

## 1. Executable Commands

- Dependency/setup: `git submodule update --init --recursive`.
- Dependency installation: not a separate script; the Gradle wrapper resolves dependencies when a task runs.
- Gradle wrapper: `.\gradlew.bat` on Windows or `./gradlew` in CI/POSIX; the wrapper pins Gradle 9.0.0.
- Development/install: `.\gradlew.bat :app:installDebug` with a connected API 26+ device or emulator.
- Debug APK: `.\gradlew.bat :app:assembleDebug`.
- Release APK: `.\gradlew.bat :app:assembleRelease`.
- Room/KSP debug generation: `.\gradlew.bat :app:kspDebugKotlin`.
- Debug lint: `.\gradlew.bat :app:lintDebug`.
- Full unit-test suite: `.\gradlew.bat :app:testDebugUnitTest`.
- Targeted test class: `.\gradlew.bat :app:testDebugUnitTest --tests "com.anomalyzed.simpletranscriber.updater.UpdateIntegrityTest"`.
- Targeted test method: `.\gradlew.bat :app:testDebugUnitTest --tests "com.anomalyzed.simpletranscriber.updater.UpdateIntegrityTest.normalizeSha256RejectsInvalidDigest"`.
- Instrumentation tests: Not configured; no `app/src/androidTest` sources are present.
- Single-file validation: Not configured; no standalone Kotlin, XML, or C++ validator is defined.
- Standalone type checking: Not configured; Kotlin type checking occurs through the Gradle compile/build tasks.
- Packaging: `.\gradlew.bat :app:assembleRelease`; signing and release upload are CI-only.

## 2. Operational Boundaries

### Always Do

- Inspect neighboring Kotlin, resource, manifest, and native code before introducing a new pattern.
- Initialize and preserve the checked-in native submodule before any native build.
- For bugs, add or run the smallest reproducing unit test before applying the fix.
- Run the narrowest relevant Gradle validation and `git diff --check` after changes.
- Keep the diff limited to files required by the task.

### Ask First

- Add, remove, or upgrade dependencies or change entries in `gradle/libs.versions.toml`.
- Change Room entities, database version/migrations, persistent settings, public behavior, or manifest permissions.
- Update the `whisper.cpp` submodule, native build flags, release workflow, signing, or updater contract.
- Rename or delete shared components, generated outputs, or protected configuration.

### Never Do

- Never commit `local.properties`, API keys, keystores, signing passwords, or other credentials.
- Never disable, delete, weaken, or bypass a failing test or security check.
- Never edit files under `third_party/whisper.cpp` for an app-only task.
- Never manually edit `.gradle/`, `build/`, `.cxx/`, or `app/.cxx/` outputs.
- Never apply mass formatting or unrelated refactoring outside the task scope.

## 3. Repository-Specific Constraints

- CI uses JDK 17 and Android SDK 35; the app targets Java 11 and Kotlin JVM target 11, with minSdk 26.
- `third_party/whisper.cpp` is a recursive Git submodule pinned at commit `9386f239401074690479731c1e41683fbbeac557` (`v1.8.4`); the app CMake file requires that path.
- Native builds are CPU-only with OpenMP, native targets, CMake tests, and CMake examples disabled; supported ABIs are `arm64-v8a`, `armeabi-v7a`, `x86`, and `x86_64`.
- `GEMINI_API_KEY` is read from root `local.properties` and injected into `BuildConfig`; keep the file local and never hardcode the key.
- Room database `watranscriber_db` is schema version 2 and includes the checked-in `MIGRATION_1_2`; schema changes require an explicit migration.
- Release automation triggers on `v*` tags or published/created releases, signs with repository secrets, and expects `transcriber-signed.apk` and `transcriber-debug.apk` plus SHA-256 sidecars.
- Treat `.gradle/`, `build/`, `.cxx/`, `app/.cxx/`, `local.properties`, `.idea/`, and signing-key extensions as generated, local, or protected paths.
