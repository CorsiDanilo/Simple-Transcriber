# 04 - Deployment & CI/CD 🚀

Transcriber uses GitHub Actions to automate the release process.

## GitHub Actions Pipeline

The workflow defined in `.github/workflows/release.yml` is triggered when:
- A new **Tag** starting with `v` (e.g., `v1.0.0`) is pushed.
- A **Release** is manually created or published in the GitHub UI.

### Pipeline Steps

1. **Checkout**: Fetches the latest code.
2. **Setup Java**: Configures JDK 17.
3. **Build**: Runs `./gradlew assembleRelease`.
4. **Sign**: Uses the `r0adkll/sign-android-release` action to sign the APK using secrets stored in the repository.
5. **Release**: Uploads the signed APK to the GitHub Release page and generates automated release notes from `CHANGELOG.md`.

## Required Repository Secrets

To make the pipeline work, the following secrets must be added to the GitHub repository (*Settings > Secrets and variables > Actions*):

| Secret Name | Description |
|-------------|-------------|
| `SIGNING_KEY` | The base64 encoded content of your `.jks` keystore file. |
| `ALIAS` | The alias of your signing key. |
| `KEY_STORE_PASSWORD` | The password for the keystore. |
| `KEY_PASSWORD` | The password for the specific key. |

## Versioning

The version name and code are managed in `app/build.gradle.kts`. When releasing a new version:
1. Increment `versionCode` and `versionName`.
2. Update `CHANGELOG.md`.
3. Commit and push.
4. Create a new tag: `git tag v1.0.1 && git push origin v1.0.1`.
