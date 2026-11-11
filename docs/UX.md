# Workspace UX

FlexiList opens into four equal workspaces: Lists (Normal, ToDo, Checklist), Habits, Workouts, and Library (Reading List, Collection). The groups come from existing notebook types; there is no database migration. Unknown imported types remain visible under Lists. The app remains native, offline-first, and account-free.

## Navigation and editing contracts

- Compact screens use bottom navigation. At font scales above 1.3 it becomes two rows so labels remain readable. From 600 dp, navigation uses a rail. From 840 dp, normal-size text supports a notebook overview beside the selected notebook; large text keeps a single content pane.
- Each workspace retains its navigation stack, notebook overview query, category/archive choice, and scroll position. Each notebook retains its item filters. Opening a notebook or entry uses its IDs and explicit loading, missing, and failure views rather than displaying another notebook's rows.
- Notebook creation/editing is a full page: name and type first, optional configuration under More options. Workout schedules and custom priority configuration use separate values. Editing preserves independently changed pin, favorite, archive, completion, and order fields.
- Quick entry and the full entry editor share an ID-scoped draft. The full draft uses `SavedStateHandle`; form values use Compose saveable state. Rotation/recreation does not silently insert a row. A save succeeds only after the Room transaction commits. A recreated form reattaches to the same in-flight save in its retained ViewModel, preventing duplicate rows during activity recreation. This is not a cross-process idempotency guarantee if Android kills the process at the commit/acknowledgement boundary.
- Deleting entries offers Undo for five seconds, extended by Android's accessibility timeout recommendation. Undo restores the original IDs, order and fields, and refuses to overwrite another record or recreate a deleted parent. Notebook deletion requires a confirmation naming the notebook and its current entry count.

## Data transfer and appearance

Incoming `.flexi`, JSON backups, Android shares, and optional cloud downloads first show a preview. Parsing, staging and cancellation do not write to Room. Confirmation adds notebook copies atomically; full backups may update water records for the same date when the imported record is newer. Pending review data stays in private cache, outside the saved-state Bundle. Imports have a 16 MiB limit; an expired or invalid file offers a recoverable failure.

JSONBlob is an optional, manual backup transfer. Its code provides access to the stored copy. The settings explain this before upload/download; the app does not promise account authentication, end-to-end encryption, or automatic multi-device synchronization. Local JSON/CSV export and `.flexi` sharing remain available. Shared notebook contents travel in a granted file URI rather than a potentially oversized Android intent text payload.

Appearance supports Light, Dark and System. An explicit legacy light/dark preference migrates to the equivalent mode; new installs follow System. All eight accent choices remain, with readable light-mode foregrounds and semantic surfaces. New interface strings and counts cover English, Turkish, Russian, German, Spanish and French. Workout weekday/muscle labels are translated for display without rewriting stored configuration keys or user-authored names.

## Verification

Run the same native journeys and optionally record their actual rendered screens:

```sh
./gradlew :app:testFdroidDebugUnitTest \
  --tests xyz.younglion.flexilist.WorkspaceFlowTest \
  --tests xyz.younglion.flexilist.WorkspaceControllerTest \
  -PcaptureUx=true --no-configuration-cache
```

Images are written to `app/build/outputs/ux`. Tests exercise long notebook names, all four workspaces, quick-to-full draft transfer, activity recreation, actual database saves, task completion, System/Light/Dark changes, 320 dp with 200% font scaling, 900 dp master/detail layout, import review/cancel/confirm, visible Undo, and workout/player/water/analytics navigation. Controller tests cover draft serialization, legacy type/theme/selection compatibility, exact deletion restoration, missing parents, concurrent edits, interrupted saves, import limits, malformed backup handling, and granted share-file contents for short, Unicode and long notebook names. Existing data/backup/workout tests continue to run for both flavors.

Full checks:

```sh
./gradlew :app:testFullDebugUnitTest :app:testFdroidDebugUnitTest \
  :app:lintFullDebug :app:lintFdroidDebug \
  :app:assembleFullDebug :app:assembleFdroidDebug \
  :app:assembleFullRelease :app:assembleFdroidRelease --no-configuration-cache
python3 -m unittest discover -s tools/tests -v
```

Robolectric/Roborazzi evidence is native UI rendering, not a physical-device certification. Real keyboard/inset behavior, TalkBack traversal, Android notification permission/settings, background workout timing, and live JSONBlob upload/download still require device/service verification. The release signing contract and F-Droid recipe are unchanged by this UX work; a locally generated candidate is not a publication with the developer's release certificate.

Selected rendered evidence is kept in [ux/](ux/).

## 1.1.0: find, start and capture

The second UX pass adds three complete journeys on top of the four-area shell:

- **Find:** Search everything from the overview toolbar or notebook menu. Names, notes, tags and categories remain local. Today includes open tasks due by the end of the device's current day and undated unfinished habits; future-dated habits wait until their due day. Starred includes starred entries and favorite notebooks. Workspace filters narrow results; archive inclusion is explicit. Selecting an entry opens its ID-scoped editor in the correct workspace. Queries survive activity recreation. Results are displayed in pages of 30 and computed off the main thread. The search corpus is collected only while the search screen is open and released when it closes.
- **Start:** Choose Blank or a previewed localized notebook starter. Weekly review, groceries, daily routine and workout starters contain the visible entries; Reading queue starts empty. A name/type change remains editable. No starter writes before Save, and the notebook plus entries commit in one Room transaction. Editing an existing notebook never reseeds it.
- **Capture:** Quick entry supports one entry or one entry per line, with a 100-entry limit and optional Keep open. Blank lines are ignored; intentional duplicates remain. Draft details apply to every line. Lines append in their entered order, and single entries append to the manual order as well. Reattaching to an in-flight save does not insert a second batch.

Overview cards use the notebook's chosen cover color, real completion counts, restrained progress and shared typography/shapes. One aggregate Room projection replaces the per-card item subscriptions and avoids loading full entry bodies for counts. Category chips scroll in their own row. Favorites and ordering remain available. Program controls, search, filters and entries share one lazy vertical scroller, so a large workout header cannot reduce the exercise list to zero height.

Consecutive entry deletions now merge into the current Undo snapshot and restart its accessible timeout. An expired older token cannot clear the newer batch. The original IDs, order, custom fields and parent checks remain intact.

`Experience11Test` covers search scope, Unicode/literal queries, stored metadata exclusion, archive/favorite policy, date boundaries, atomic starters/batches, duplicate/order preservation, parent/size failures, Undo accumulation and aggregate counts. `WorkspaceFlowTest` adds actual starter → batch → recreation → continued entry → search → editor, Today/archive/cross-workspace navigation, and 320 dp/200% workout reachability. V01/V03/V04/V07/V08 have native rendered evidence; physical-device keyboard, TalkBack and service verification remain explicit gaps. Public-site hero/footer gates are N/A to this native task shell. No decorative motion or new runtime dependency was added.
