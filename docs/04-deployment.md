# 04 - Deployment and CI/CD

Transcriber uses GitHub Actions to build signed APK releases when version tags are pushed.

## GitHub Actions Pipeline

The release workflow in `.github/workflows/release.yml` is triggered when a tag starting with `v` is pushed, for example `v1.0.3`.

Typical steps:

1. Checkout the repository.
2. Set up JDK 17.
3. Run the Gradle release build.
4. Sign the APK using repository secrets.
5. Publish the APK and release notes to GitHub Releases.

## Required Repository Secrets

| Secret Name | Description |
| --- | --- |
| `SIGNING_KEY` | Base64-encoded `.jks` keystore content. |
| `ALIAS` | Signing key alias. |
| `KEY_STORE_PASSWORD` | Keystore password. |
| `KEY_PASSWORD` | Key password. |

## Versioning

The Android version is managed in `app/build.gradle.kts`:

- `versionCode`: integer used by Android for upgrade ordering.
- `versionName`: human-readable app version.

For a release:

1. Increment `versionCode`.
2. Increment `versionName`.
3. Update `CHANGELOG.md`.
4. Update documentation and README when behavior changes.
5. Commit changes.
6. Tag the commit, for example `git tag v1.0.3`.
7. Push the branch and tag.

## Current Release

- Version: `1.0.3`
- Version code: `4`
- Tag: `v1.0.3`
