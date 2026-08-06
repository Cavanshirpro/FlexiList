# 🚀 FlexiList v1.0.0 Release Notes

**FlexiList - Ultra Fast, Offline-First, Flexible Multi-Purpose List & Workout Assistant**
- **Package Name (Application ID)**: `xyz.younglion.flexilist`
- **Release Version**: `1.0.0`
- **Build Target**: Android 14+ (API 36) / Minimum Android 7.0 (API 24)
- **License**: GNU General Public License v3.0 (GPLv3)

---

## 🌟 What's New in v1.0.0

### 1. 🏋️‍♂️ Workout Planner & Interactive Player Overlay
- Full-screen **Distraction-Free Workout Player** with 5-second preparation countdown.
- Auto-parsing DSL for exercise descriptions: Set counts, Reps vs. Timer (seconds), Target Weight (kg), Rest periods (seconds), and Target Muscle Groups.
- **⚡ Superset connection tags** for grouped workouts.
- **Muscle Group Visualizer**: Anatomical human graphic that highlights active muscles during exercise.
- Rest state overlay with **+15s Rest Extension**, **Skip Rest**, and **Skip Exercise (Do Later)** logic.
- Automated workout log database persistence with total volume/tonnage and duration stats.
- Period management for Daily (Mon-Sun) and Monthly (1-31) workout chips.
- Smart auto-reset of completed checkmarks when day changes.

### 2. 📝 Multi-type Notebooks & Task Management
- Notebook types: Standard, To-Do, Shopping, Habit Tracker, and Workout Planner.
- Drag & Drop reordering with smooth Compose animations.
- Bulk selection mode (multi-delete, multi-move).
- Starring and priority badge filtering.
- Fully customizable priority levels and color palettes (`PriorityCustomizationDialog`).

### 3. 🎨 Customization & Audio-Visual Polish
- Handcrafted theme palettes: *Flexi Cyan, Neon Emerald, Electric Violet, Sunset Crimson, Cyber Gold, Deep Ocean*.
- Audio feedback: Tick sound for countdowns and Ding sound on set completion.
- Edge-to-Edge display with system dark/light theme integration.
- Haptic feedback support across completed items, stars, and selections.

### 4. 🔔 Local Reminders & Background Service
- Independent alarm scheduling using Android `AlarmManager` and `NotificationManager`.
- Lock screen and notification bar status updates during workouts.

### 5. ☁️ Cloud Sync & File Sharing
- SSL-encrypted zero-configuration cloud backup using `JSONBlob` REST API.
- Unique `FLX-XXXXXX` backup access keys.
- Standalone `.flexi` notebook file export and import via standard Android intent filters (`ACTION_VIEW` / `ACTION_SEND`).

---

## 📦 Download Assets

| Asset Name | Description | Size | Link |
|---|---|---|---|
| **`FlexiList-v1.0.0.apk`** | Compiled Release / Debug APK | ~23 MB | [Direct Download](releases/FlexiList-v1.0.0.apk) |
| **`Source Code (zip)`** | Full GitHub Source Code Repository | - | GitHub Repository Root |

---

## 📲 How to Install the APK

1. Download `FlexiList-v1.0.0.apk` onto your Android device.
2. Tap the downloaded APK in your device's browser or file manager.
3. If prompted, grant permission to "Install from Unknown Sources".
4. Follow the on-screen prompt to complete installation.
5. Launch **FlexiList** and enjoy!

---

*Thank you for using FlexiList! Feedback and open-source contributions are warmly welcomed.*
