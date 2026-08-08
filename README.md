# FlexiList 📱✨

[![Android](https://img.shields.io/badge/Platform-Android-green.svg?logo=android)](https://www.android.com)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-purple.svg?logo=kotlin)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-blue.svg?logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![Package](https://img.shields.io/badge/Package-xyz.younglion.flexilist-informational.svg)](https://github.com/)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![Release](https://img.shields.io/badge/Release-v1.0.3-brightgreen.svg)](FlexiList-v1.0.3.apk)

**FlexiList** is an ultra-fast, offline-first, modern, modular, and flexible multi-purpose list management and workout assistant application built with Kotlin, Jetpack Compose, and SQLite-based Room Database.

Unlike ordinary to-do apps, FlexiList combines To-Do Lists, Shopping Lists, Habit Trackers, and an interactive **Workout Planner (Workout Assistant)** into a single, unified experience. Featuring a Material Design 3 compliant, distraction-free dark slate aesthetic, it delivers high performance and intuitive ergonomics.

---

## 🚀 Key Features

### 1. 🏋️‍♂️ Advanced Workout Assistant (Workout Planner)
- **Smart DSL Parsing**: Parses exercise descriptions automatically into visual pills/chips for Set counts, Reps/Timer, Weight (kg), Rest periods (s), and Target Muscle Groups.
- **⚡ Superset Support**: Automatic visual grouping and connection badges for superset exercises.
- **Distraction-Free Full-Screen Player**:
  - 5-second preparation countdown (Prep State).
  - Auto-counting timer for duration exercises; instant "COMPLETE SET" action for rep-based exercises.
  - Cyberpunk-inspired **Muscle Group Visualizer**: Anatomical human graphic that highlights active muscle groups during exercises.
  - Automatic rest timer with "+15s Rest", "Skip Rest", and "Do Later" options.
- **Period Management**:
  - **Daily Programs**: Weekday-specific routines (Mon, Tue, Wed...).
  - **Monthly Programs**: 1–31 day horizontal chip navigation (`LazyRow`).
- **Auto-Sync & Logging**: Upon completing a session, items auto-mark as completed; duration, sets, and lifted volume save directly to local history logs.

### 2. 📝 Multi-Purpose Notebook & List Management
- **Flexible Notebook Models**: Standard, To-Do, Shopping, Habit Tracker, and Workout Planner notebooks.
- **Drag & Drop Reordering & Bulk Operations**: Smooth Compose list reordering and multi-selection mode for batch delete/move.
- **Customizable Priority Levels**: Custom color palettes and level names via `PriorityCustomizationDialog`.
- **Advanced Filtering**: Starring, priority badge indicators, and tag/category filtering.

### 3. 🌐 Multi-Language & Theme Personalization
- **Multi-Language Support**: Built-in support for English 🇬🇧, Turkish 🇹🇷, and Russian 🇷🇺 with real-time switching.
- **Handcrafted Accent Palettes**: *Flexi Cyan, Neon Emerald, Electric Violet, Sunset Crimson, Cyber Gold, Deep Ocean*.
- **Dark / Light Modes**: Eye-friendly slate themes with automatic or manual toggling.
- **Haptic Feedback**: Precise tactile feedback for key actions.

### 4. 🔔 Notifications, Audio & Reminders
- **Audio Feedback**: Countdown tick sounds for the last 3 seconds and completion gong sounds.
- **Local Reminders**: Independent scheduling using `AlarmManager` and `NotificationManager`.
- **Lock Screen & Notification Tracking**: Background service tracking for active workout sessions and reminders.

### 5. ☁️ Cloud Sync & File Sharing
- **SSL-Encrypted Cloud Sync**: Zero-configuration cloud backup & restore powered by `CloudSyncManager` (JSONBlob REST API).
- **Secure Sync Keys**: Auto-generated `FLX-XXXXXX` synchronization codes.
- **Flexi File Import/Export**: Share `.flexi` notebook files directly with native Android `ACTION_VIEW` and `ACTION_SEND` intent handlers.

---

## 🛠️ Architecture & Tech Stack

- **Language**: Kotlin (100%)
- **UI Framework**: Jetpack Compose + Material Design 3
- **Architecture**: MVVM (Model-View-ViewModel) + Clean Architecture
- **Local Database**: Room Persistence Library + KSP
- **Concurrency**: Kotlin Coroutines & Flow
- **Networking & REST API**: Retrofit 2, OkHttp 4, Moshi
- **Navigation**: Type-Safe Navigation Compose
- **Testing**: JUnit 4, Robolectric, Roborazzi (Visual Regression Testing)
- **Application ID**: `xyz.younglion.flexilist`

---

## 📥 Download & Installation (APK)

Download the compiled **v1.0.3** release directly to install on your Android device:

Direct Download: [`FlexiList-v1.0.3.apk`](FlexiList-v1.0.3.apk) or [`releases/FlexiList-v1.0.3.apk`](releases/FlexiList-v1.0.3.apk)

### Installation Steps:
1. Download `FlexiList-v1.0.3.apk` to your Android device.
2. Open the file in your browser or file manager.
3. Allow "Install from Unknown Sources" if prompted.
4. Complete installation and enjoy **FlexiList**!

---

## 💻 Building From Source

### Prerequisites:
- **Android Studio**: Ladybug (2024.2.1+) or newer
- **JDK**: OpenJDK 11 or 17
- **Android SDK**: API level 36 (Min SDK: 24)

### Building:

```bash
# 1. Clone repository
git clone https://github.com/username/flexilist.git

# 2. Navigate to project directory
cd flexilist

# 3. Build Debug APK
./gradlew assembleDebug

# 4. Locate output APK at:
# app/build/outputs/apk/debug/app-debug.apk
```

---

## 📁 Directory Structure

```
FlexiList/
├── app/
│   ├── src/main/
│   │   ├── java/xyz/younglion/flexilist/
│   │   │   ├── data/
│   │   │   │   ├── backup/         # Cloud & File Backup managers
│   │   │   │   ├── db/             # Room DAO & AppDatabase
│   │   │   │   ├── model/          # Entities & Data Models
│   │   │   │   ├── notifications/  # AlarmManager & Notification handlers
│   │   │   │   └── repository/     # FlexiRepository
│   │   │   ├── ui/
│   │   │   │   ├── detail/         # Item detail screens
│   │   │   │   ├── dialogs/        # Theme, Language, Settings & Analytics dialogs
│   │   │   │   ├── items/          # List items screen
│   │   │   │   ├── notebooks/      # Notebooks list screen
│   │   │   │   ├── theme/          # Color, Type, Theme M3 specs
│   │   │   │   └── workout/        # Workout Player & Visualizer
│   │   │   └── util/               # Animations & Network Monitor
│   │   ├── res/                    # Values (en, tr, ru), Drawables, Sounds & XMLs
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts            # Application ID: xyz.younglion.flexilist
├── FlexiList-v1.0.3.apk           # Compiled Release APK (Root)
├── releases/
│   └── FlexiList-v1.0.3.apk        # Compiled Release APK
├── AGENTS.md                       # Progress Report & Technical Specification
├── RELEASE.md                      # Release Notes (v1.0.3)
├── LICENSE                         # GNU General Public License v3.0
├── CONTRIBUTING.md                 # Contribution Guidelines
└── SECURITY.md                     # Security Policy
```

---

## 🤝 Contributing

Contributions are warmly welcomed! Please read [CONTRIBUTING.md](CONTRIBUTING.md) before submitting pull requests.

1. Fork the repository.
2. Create your feature branch (`git checkout -b feature/amazing-feature`).
3. Commit your changes (`git commit -m 'feat: Add amazing feature'`).
4. Push to the branch (`git push origin feature/amazing-feature`).
5. Open a Pull Request.

---

## 📜 License

This project is licensed under the **GNU General Public License v3.0 (GPLv3)**. See [LICENSE](LICENSE) for details.

---

<p align="center">
  <b>FlexiList</b> — Fast, Free & Flexible Multi-Purpose Assistant 🚀
</p>
