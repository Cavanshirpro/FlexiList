# 🚀 FlexiList v1.0.1 Release Notes

**FlexiList - Ultra Fast, Offline-First, Flexible Multi-Purpose List & Workout Assistant**
- **Package Name (Application ID)**: `xyz.younglion.flexilist`
- **Release Version**: `1.0.1`
- **Build Target**: Android 14+ (API 36) / Minimum Android 7.0 (API 24)
- **License**: GNU General Public License v3.0 (GPLv3)

---

## 🌟 What's New in v1.0.1

### 1. 🌐 Multi-Language Support (Localization)
- Added built-in support for 6 languages: **English 🇬🇧**, **Turkish 🇹🇷**, **Russian 🇷🇺**, **German 🇩🇪**, **Spanish 🇪🇸**, and **French 🇫🇷**.
- Real-time language switching directly inside Theme & Language Settings.
- Dynamic runtime localization of muscle groups, priority levels, day indicators, theme palettes, and cloud sync dialogs.
- Complete internationalization of documentation, code comments, and resources for global open-source collaboration.

### 2. 📱 F-Droid & Open-Source Publishing Readiness
- Added `.fdroid.yml` manifest and standard Fastlane metadata (`fastlane/metadata/android/`).
- 100% open-source compliance with no proprietary ad SDKs or closed analytics trackers.
- GitHub Actions workflow (`.github/workflows/android-build.yml`) for automated CI/CD builds and release APK packaging.

### 3. 🏋️‍♂️ Enhanced Workout Planner & Player
- Distraction-Free full-screen workout player with 5-second prep timer.
- Dynamic exercise DSL parsing (Sets, Reps/Timer, Weight, Rest time, Target Muscle Groups).
- **⚡ Superset visual badges** connecting related exercise groups.
- Anatomical **Muscle Group Visualizer** highlighting worked muscles.
- Rest state extension (+15s), skip rest, and skip exercise ("Do Later") logic.

### 4. 📝 Multi-type Notebooks & Task Management
- Notebook types: Standard, To-Do, Shopping, Habit Tracker, and Workout Planner.
- Drag & Drop reordering and bulk operations mode.
- Customizable priority levels with custom color palettes (`PriorityCustomizationDialog`).

### 5. 🎨 Themes & Customization
- Handcrafted theme palettes (*Flexi Cyan, Neon Emerald, Electric Violet, Sunset Crimson, Cyber Gold, Deep Ocean*).
- Dark and Light mode toggles with live preview.
- Haptic feedback integration for touch actions.

### 6. 🔔 Reminders, Notifications & Cloud Sync
- Local reminder scheduling powered by `AlarmManager` and `NotificationManager`.
- Zero-configuration SSL-encrypted cloud backup (`JSONBlob` API) with secure `FLX-XXXXXX` keys.
- Native `.flexi` file import/export support (`ACTION_VIEW` and `ACTION_SEND` intents).

---

## 📦 Download Assets

| Asset Name | Description | Size | Link |
|---|---|---|---|
| **`FlexiList-v1.0.1.apk`** | Compiled Release APK | ~23 MB | [Direct Download](releases/FlexiList-v1.0.1.apk) |
| **`Source Code (zip)`** | Full GitHub Source Code Repository | - | GitHub Repository Root |

---

## 📲 How to Install the APK

1. Download `FlexiList-v1.0.1.apk` onto your Android device.
2. Tap the downloaded APK in your browser or file manager.
3. If prompted, grant permission to "Install from Unknown Sources".
4. Follow the on-screen prompt to complete installation.
5. Launch **FlexiList**!

---

*Thank you for using FlexiList! Feedback and open-source contributions are warmly welcomed.*
