# FlexiList — Project Summary & Development Progress Report

This file serves as an official technical guide detailing the current architecture, workout system workflow, implemented features, and roadmap for **FlexiList**.

---

## 1. Project Overview

**FlexiList** is an ultra-fast, offline-first, modern, and flexible multi-purpose list management application built using Kotlin, Jetpack Compose, and SQLite-based Room Database architecture. 

Unlike traditional to-do list applications, FlexiList unifies dynamic list types (such as To-Do, Shopping List, Habit Tracker, and Workout Planner) under a single cohesive framework. It offers a Material Design 3 compliant, distraction-free dark slate canvas designed for aesthetic appeal and high usability.

---

## 2. Workout System Technical Details

One of FlexiList's core features is its **Workout Planner**, which transforms flexible exercise lists into an interactive workout assistant.

### Exercise Configuration & Parsing DSL
Each item in a workout list contains inline metadata formatted in a mini Domain Specific Language (DSL) within its description. This data is dynamically parsed into visual UI chips/pills:
- **Set Count**: Number of sets to perform.
- **Exercise Type (Reps / Timer)**: Determines whether an exercise is rep-based (e.g. 10 Reps) or duration-based (e.g. 60 seconds).
- **Weight (kg)**: Target weight for the exercise.
- **Rest Duration (s)**: Rest time between sets.
- **Target Muscle Groups**: Dynamic, color-coded tags for muscle groups (Chest, Back, Shoulders, Arms, Legs, Core, etc.).
- **Superset Support**: Visual grouping with a "⚡ SUPERSET" badge connecting linked exercises.

### Workout Player Overlay
When a workout starts, a full-screen **Distraction-Free** overlay activates:
- **Prep Countdown**: A 5-second preparation timer before beginning the workout.
- **Active Exercise View**:
  - Auto-counting timer for duration-based exercises.
  - Clear "COMPLETE SET" action button for rep-based exercises.
  - Cyberpunk-inspired dynamic **Muscle Group Visualizer** highlighting active muscles on an anatomical body model.
- **Rest State View**:
  - Automatically triggers when a set is completed.
  - "+15s Rest" option to extend break duration.
  - "Skip Rest" button to immediately jump to the next set/exercise.
- **"Do Later" (Skip Exercise) Logic**: Postpones an unready exercise to the end of the current session.
- **Automatic Status Sync**: Upon completing a workout, all corresponding items are automatically marked as completed in the Room database and instantly reflected in the main view.
- **Workout Logging**: Session duration, total completed sets, and total tonnage lifted are saved to local database history logs.

---

## 3. Feature & Progress Tracker

The following checklist tracks development milestones, completed features, and future plans for FlexiList.

### [A] Notebook & Base List Management
- `[+]` Room database architecture and schema setup.
- `[+]` Support for various notebook types (Standard, To-Do, Shopping, Habit, Workout).
- `[+]` Drag & Drop list reordering with smooth animations.
- `[+]` Bulk Selection Mode for batch delete and batch move operations.
- `[+]` Starring & priority level filtering.
- `[+]` Category and Tag management.

### [B] Workout Planner & Exercise Configuration
- `[+]` Parameter parsing engine for exercise description strings.
- `[+]` Custom parameter pills on exercise cards (Sets, Reps/Timer, Rest, Weight).
- `[+]` Color-coded badges for target muscle groups.
- `[+]` Superset visual connection badges and parsing.
- `[+]` Progress bar counter card for Daily workout frequency mode showing active days and streak counts.
- `[+]` Horizontal scrollable (`LazyRow`) chip bar for Monthly workout frequency mode (days 1..31).
- `[+]` Day-of-week filtering system (Mon, Tue, Wed, etc.).

### [C] Workout Player & Execution Flow
- `[+]` Full-screen Distraction-Free workout player UI.
- `[+]` Canvas drawing for Muscle Group Visualizer.
- `[+]` 5-second prep countdown with skip option.
- `[+]` Automatic countdown mechanism for duration-based exercises.
- `[+]` "Complete Set" button for rep-based exercises.
- `[+]` Automatic Rest State activation between sets.
- `[+]` "+15s Rest" extension button.
- `[+]` State persistence (`SharedPreferences`) on app exit or phone reboot, enabling "Resume Workout" capability.
- `[+]` Cleaner day filter bar with direct focus on weekdays.
- `[+]` Dialog scrollability and layout fixes for small screens.
- `[+]` Day-restricted workout activation (only active day program can be started).
- `[+]` "Start" button transitions to disabled "Completed ✓" state upon session completion.
- `[+]` Daily auto-reset of completion checkmarks at midnight.
- `[+]` Floating Action Button (+ FAB) hidden during active workout playback.
- `[+]` Modernized, high-contrast GUI for workout activation card.

### [D] Audio, System Notifications & Automation
- `[+]` Audio feedback with last 3-second tick sound and completion gong sound.
- `[+]` System notification bar integration for background and lock-screen tracking.
- `[+]` Local notification and reminder system powered by `AlarmManager` and `NotificationManager`.
- `[+]` Automatic background DB mark-as-completed for all session items upon workout completion.

### [E] Analytics, Statistics & Logs
- `[+]` Detailed JSON workout session report saving (duration, completed sets, volume lifted).
- `[+]` Workout history log screen with report deletion support.
- `[+]` Analytical charts and muscle development visual mapping.
- `[+]` Weekly and monthly total tonnage graphs.
- `[+]` `NotebookStatisticsDialog` featuring Donut Gauge completion charts and priority distribution bars.

### [F] GUI Polish, Bug Fixes & Optimizations
- `[+]` Corrected "Next Set" preview info during rest state.
- `[+]` Edge-to-Edge display support with Android 12+ dynamic color compatibility.
- `[+]` Multi-language support (English `en`, Turkish `tr`, Russian `ru`) with dynamic runtime switching via `attachBaseContext` and `recreate()`.
- `[+]` `ThemeCustomizationDialog`: Dark/Light theme toggle and accent palette options (*Flexi Cyan, Neon Emerald, Electric Violet, Sunset Crimson, Cyber Gold, Deep Ocean*).
- `[+]` `PriorityCustomizationDialog`: Dynamic priority creation, deletion, custom titles, and color pickers.
- `[+]` Color-accented priority cards with left vertical indicator bars and priority badges.
- `[+]` Horizontal scroll (`LazyRow`/`FlowRow`) integration across exercise parameter pills and day chips to prevent overflow on narrow screens.
- `[+]` Full-screen `WorkoutPlayerScreen` vertical scrollability for small screen heights.
- `[+]` Automatic `.flexi` file import handling via `ACTION_VIEW` & `ACTION_SEND` intent filters.
- `[+]` Tactile haptic feedback (`LocalHapticFeedback`) for item completion, starring, and selection.
- `[+]` Resolved all compilation and deprecation warnings.
- `[ ]` Smooth animation optimization for moving completed items to bottom.
- `[ ]` Background service WakeLock stability enhancements.

### [G] Cloud Backup & Sync
- `[+]` Zero-configuration, user-friendly cloud backup architecture.
- `[+]` SSL-secured global REST API integration via `CloudSyncManager` (using OkHttp & JSONBlob).
- `[+]` High-security auto-generated sync codes (`FLX-XXXXXX`).
- `[+]` One-tap download & restore with local SQLite database merge.
- `[+]` Configured `INTERNET` permissions in AndroidManifest.xml.
- `[+]` Progress indicators and toast notifications for backup/restore states.

---

*Note: This progress report is updated alongside major release milestones.*


## Maintenance update: 1.0.4

- Canonical Android version metadata is in `release/release.json`; Gradle reads it directly.
- `ci.yml` calls the reusable `android-build.yml` for Full/F-Droid tests, lint and APK artifacts. `publish-release.yml` verifies signed candidates and publishes on matching version tags or explicit manual publish runs. See `docs/RELEASING.md` for signing secrets and artifact validation.
- Backup schema 2 preserves source notebook IDs for log remapping, item order and timestamps. Imports are atomic; legacy backups remain readable. Import creates copies rather than performing identity-based multi-device synchronization.
- Daily completion reset applies to workout/habit notebooks and operates independently of UI filters. Normal tasks remain completed.
- Water increments use Room transactions. Workout logs and item completion updates are saved in one transaction. Partial sessions only complete exercises whose entire set sequence was performed.
- Workout persistence includes queue ordering, completed steps and start time. Timer seconds and preparation/rest phase are not persisted; reopening resumes at set boundaries. The Compose timer is not a foreground service and does not guarantee exact background execution.
- Local `.flexi` imports preserve optional fields and run file reading off the main thread. Notifications use a single workout channel and check Android notification permission.
- Verification commands and remaining device-test limits are documented in `docs/RELEASING.md`.


## Publish and F-Droid follow-up

- Publish runs check signing-secret availability in preflight; validation mode can inspect unsigned candidates. Public publication continues to require the existing release certificate.
- `tools/fdroid_metadata.py` generates a canonical F-Droid recipe pinned to a full release source SHA; `tools/check_fdroid.py` runs the official linter and validates store assets. Update the GitLab recipe from the published revision, not an in-progress branch.
- Release APKs omit dependency and VCS metadata. Staging checks ZIP integrity so corrupt binaries cannot be uploaded as APKs.
