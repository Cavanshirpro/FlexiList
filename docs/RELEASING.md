# Build and release automation

The pipeline follows the same separation used by LeoMiniGames and YLSpec: build and test, stage exact artifacts, verify the release contract, then publish.

## CI and downloadable builds

Every pull request and push to `main`/`master` runs `.github/workflows/ci.yml`, which calls `android-build.yml`. The build workflow can also run manually.

Both **Full** and **F-Droid** variants run unit/Robolectric tests, Android lint, and debug/release compilation on JDK 21 with Gradle 9.3.1 and Android SDK 36.1. Reports and APKs are uploaded separately per flavor. Unsigned release builds remain clearly named `-unsigned.apk`; they are build evidence, not installable public release assets.

Firebase and Google Services were unused by the application and have been removed. Both variants build without a `google-services.json`, `.env`, or a checked-in debug keystore. Android's standard debug signing is used for development.

## Canonical version

Edit `release/release.json` and its referenced notes file for each release. Gradle reads `version` and `version_code` directly from this file. Increment the version code for Android upgrades. `tag` must equal `v` followed by `version`.

`signing_cert_sha256` is the existing GitHub release certificate fingerprint. It is checked against each signed APK so an incompatible signing key cannot be published accidentally. Keep the existing key for updates to already-installed copies.

Validate locally:

```sh
python3 tools/release.py metadata
python3 -m unittest discover -s tools/tests -v
./gradlew :app:testFullDebugUnitTest :app:lintFullDebug :app:assembleFullDebug
./gradlew :app:testFdroidDebugUnitTest :app:lintFdroidDebug :app:assembleFdroidDebug
```

## Signing setup

Configure these GitHub repository Actions secrets once:

| Secret | Value |
| --- | --- |
| `KEYSTORE_BASE64` | Base64 encoding of the existing release keystore |
| `STORE_PASSWORD` | Keystore password |
| `KEY_PASSWORD` | Private key password |
| `KEY_ALIAS` | Optional key alias; defaults to `upload` |

The build decodes the keystore only into the runner's temporary directory and removes it on success or failure. Explicit manual publication checks for missing credentials in preflight, before any build starts. A wrong password or certificate mismatch also stops publication. Validation-only runs can inspect unsigned candidates without credentials. No new signing identity is generated.

To build a signed APK locally, supply `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD` and optionally `KEY_ALIAS` in the command environment. Ordinary builds need none of these variables. F-Droid's own build infrastructure can continue compiling unsigned release APKs and signing them independently.

## Validate or publish

- Run **Publish release** on `main` with mode `validate` to run all checks and download the `release-verified` artifact without creating a GitHub Release. If signing credentials are available it validates signed APKs; otherwise it builds clearly labeled unsigned candidates.
- Run it on `main` with mode `publish` to perform the same checks and publish automatically.
- Push the matching `vX.Y.Z` source tag from a commit on `main` to run the complete pipeline. With signing credentials it publishes automatically; without them it validates unsigned candidates and records that no GitHub APK release was created. F-Droid can use the source tag independently.

Each publication uses candidates produced by the same workflow run and commit. It checks package ID, version name/code, signing certificate, the exact file set, SHA-256 hashes, and source SHA. Both flavors must pass. APKs are uploaded to a draft using its numeric release ID; the draft becomes public only after GitHub reports the matching asset names, sizes and digests. An existing public release is never overwritten. A failed upload leaves a recoverable draft.

The public release contains two signed APKs, `SHA256SUMS.txt` and `release-manifest.json`. Only current build outputs are attached; old root/release-folder APKs are excluded. GitHub provides downloadable artifacts and a GitHub Release; this does not submit the application to F-Droid or Google Play.

Automated checks cover JVM/Robolectric behavior and APK inspection. They do not constitute physical-device qualification of background execution, sound or notifications.

## F-Droid inclusion

The existing submission is [fdroiddata!45077](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/45077), sourced from the public, unprotected `Flexilist` branch in `Cavanshirpro/fdroiddata`.

Generate its recipe from the **tagged release source SHA**:

```sh
python3 tools/fdroid_metadata.py --commit FULL_RELEASE_SOURCE_SHA --output metadata/xyz.younglion.flexilist.yml
```

The generator pins a full source commit, selects JDK 21 and the `fdroid` flavor, lets F-Droid sign its own source-built APK, and reads future version names/codes from `release/release.json` at version tags. The checked-in recipe is a source recipe; regenerate it for the final tagged revision before updating the GitLab submission.

Release builds disable Gradle dependency metadata and VCS metadata for F-Droid compatibility and reproducibility. Unused proprietary library/plugin aliases are removed. The store icon is encoded as a valid 512px PNG from the original launcher artwork.

CI installs the pinned tools in `tools/requirements-fdroid.txt`, runs the upstream F-Droid linter against current fdroiddata categories, and validates the store metadata and icon. APK staging checks ZIP integrity before inspecting package, version and certificate. The maintainer explicitly selected F-Droid signing: the recipe omits `Binaries` and `AllowedAPKSigningKeys`, so inclusion does not depend on a GitHub APK release or the developer keystore. The two channels use different certificates and cannot update over each other; switching requires uninstalling the other channel's build (export a backup first). F-Droid maintainers must accept this signing approach and are the only ones who can merge and distribute the submission.
