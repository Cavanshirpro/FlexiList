# FlexiList

[![Android](https://img.shields.io/badge/Platform-Android-green.svg?logo=android)](https://www.android.com)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-purple.svg?logo=kotlin)](https://kotlinlang.org)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![Release](https://img.shields.io/github/v/release/Cavanshirpro/FlexiList)](https://github.com/Cavanshirpro/FlexiList/releases)

FlexiList is a native Android app for offline lists, habits, workouts and reading. Four workspaces keep these tasks separate while sharing notebooks, priorities, categories, tags and custom fields.

Current source version: **1.1.0**, Android version code **6**. See the [release notes](release/notes/1.1.0.md), [UX contracts](docs/UX.md) and [rendered application journeys](docs/ux/1.1/README.md). Published APK versions are listed separately in GitHub Releases.

## Find, start and capture

- **Search everything:** Find notebooks and entries across workspaces by title, notes, tags and categories. Search stays on the device. Archived notebooks are included only when selected.
- **Today and Starred:** Find due/overdue tasks and unfinished habits, or focus on favorite notebooks and starred entries. Results open the correct notebook or editor.
- **Previewed starters:** Start blank or choose a weekly review, groceries, daily routine, workout or reading queue. Starting entries are shown before Save and created together with the notebook.
- **Batch entry:** Paste one entry per line, up to 100 at once. Duplicates, line order and shared draft details are preserved. Keep the sheet open to continue adding.
- **Recoverable edits:** Drafts survive activity recreation. Saves wait for Room transactions. Consecutive entry deletions share one Undo window; notebook deletion requires a named confirmation.

## Lists and workouts

Lists support completion, pinning, favorites, archive, manual ordering, bulk selection/move/delete, custom priorities, tags, categories, subtasks, dates, reminders and custom fields. Reading lists and collections live in Library; habits use daily completion state.

The workout planner supports repetitions/timers, sets, rest, weight, muscle groups, supersets and daily/weekly/monthly schedules. Its player provides preparation/rest timers, complete-set, skip-rest and do-later actions. Completed work is recorded locally with session history, sets and lifted volume. Water tracking and workout analytics remain available alongside exercises.

Workout progress resumes at saved set boundaries. Timer phase/seconds are not persisted, and the Compose timer is not a foreground service; exact execution while the app is backgrounded is not guaranteed. Reminders use Android notification/alarm facilities and are subject to device permissions.

## Appearance and language

Phone layouts use bottom navigation; wider layouts use a rail and, where space allows, a notebook overview beside its content. Large text uses wrapping/scrolling controls and compact Add buttons. Covers show the notebook's chosen color and actual completion progress.

Light, Dark and System are supported with eight accent choices. The interface and starter content support English, Turkish, Russian, German, Spanish and French. User-authored names and notes are kept as entered.

## Backups and sharing

Local JSON backup/restore, CSV export and `.flexi` sharing are available. Incoming files/shares first show a preview. Confirmation adds notebook copies atomically; full backups may update same-date water records when the imported record is newer. Existing version 1/2 backups remain readable. Imports have a 16 MiB limit.

Optional JSONBlob backups are a manual transfer. Their code provides access to the stored copy; this is not an authenticated account or automatic multi-device synchronization. Ordinary list use does not require a network connection.

## Downloads

Install published APKs from [GitHub Releases](https://github.com/Cavanshirpro/FlexiList/releases). Development APKs and test/lint reports are available in [Actions run artifacts](https://github.com/Cavanshirpro/FlexiList/actions). Unsigned release candidates are build evidence and require signing before installation/publication.

F-Droid builds and signs independently; inclusion is tracked in [fdroiddata!45077](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/45077). A submission is not a guarantee that a version is distributed in the F-Droid client.

## Build and verify

Use JDK **21**, Android SDK **36.1**, and the checked-in Gradle wrapper (**9.3.1**). Minimum device version: Android 7.0 (API 24).

```sh
git clone https://github.com/Cavanshirpro/FlexiList.git
cd FlexiList
./gradlew :app:testFullDebugUnitTest :app:lintFullDebug :app:assembleFullDebug
./gradlew :app:testFdroidDebugUnitTest :app:lintFdroidDebug :app:assembleFdroidDebug
python3 -m unittest discover -s tools/tests -v
```

Debug APKs are written to `app/build/outputs/apk/{full,fdroid}/debug/`. No Google Services configuration, `.env`, or custom debug keystore is required. Add `-PcaptureUx=true` to the native tests to record journey screenshots under `app/build/outputs/ux`.

Automated tests cover Room data integrity, backup compatibility, search policy, starters/batches, drafts, Undo and actual Compose journeys, including narrow/large-text and tablet layouts. Physical-device keyboard/insets, TalkBack, notifications, sound/background timing and live cloud verification remain separate checks.

## Architecture and release automation

Kotlin, Jetpack Compose/Material 3, Navigation Compose, MVVM, Room/KSP and Coroutines/Flow form the app. OkHttp/Moshi support the optional backup transfer. Package ID: `xyz.younglion.flexilist`.

- `ui/workspace`: navigation, search, starters, forms, drafts, reviewed imports and Undo.
- `ui/items`, `ui/detail`, `ui/workout`: notebook contents, entry editor and workout player.
- `data`: Room entities/projections/DAOs, repository, backup formats and reminders.
- `release/release.json`: canonical version/code/tag and release certificate fingerprint.
- `tools`: release validation, staging/publication and F-Droid metadata validation.

CI tests, lints and builds both flavors on pull requests and pushes to `main`. The publish workflow verifies the source revision, package/version, artifact set, checksums and existing developer certificate. Without that key, release candidates stay unsigned. No replacement signing identity is generated. See [the release guide](docs/RELEASING.md) for signing-secret setup and publication commands.

## Contributing and license

Read [CONTRIBUTING.md](CONTRIBUTING.md) and [AGENTS.md](AGENTS.md) before changing architecture. Report security concerns according to [SECURITY.md](SECURITY.md).

FlexiList is licensed under [GNU GPL v3](LICENSE).
