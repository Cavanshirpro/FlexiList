# 🚀 FlexiList v1.0.3 Release Notes

**FlexiList - Ultra Fast, Offline-First, Flexible Multi-Purpose List & Workout Assistant**
- **Package Name (Application ID)**: `xyz.younglion.flexilist`
- **Release Version**: `1.0.3`
- **Version Code**: `4`
- **Git Tag**: `v1.0.3`
- **Commit SHA**: `df06c779c963480991122d8df077968719012935`
- **Build Target**: Android 16 (API 36) / Minimum Android 7.0 (API 24)
- **License**: GNU General Public License v3.0 (GPLv3)

---

## 🌟 What's New in v1.0.3

### 1. 📱 Full F-Droid Open-Source Compliance & Reproducible Builds
- Configured dedicated `fdroid` build flavor matching F-Droid official inclusion requirements.
- Validated 100% reproducible APK build structure verified against unsigned F-Droid release output.
- Updated `.fdroid.yml` and `metadata/xyz.younglion.flexilist.yml` manifests pointing to exact release commit `df06c779c963480991122d8df077968719012935`.
- Verified allowed APK signing certificate fingerprint (`1f69d377fc891db13ffe8c8744a7e14cd22fffd82df4caaaf704567b14c9dba3`).

### 2. 🌐 Multi-Language Support (Localization)
- Built-in support for **English 🇬🇧**, **Turkish 🇹🇷**, and **Russian 🇷🇺**.
- Real-time language switching directly inside Theme & Language Settings via dynamic context wrapper.
- Full localization of workout parameters, UI dialogs, muscle group visualizer, and settings options.

### 3. 🏋️‍♂️ Workout Planner & Player Overlay
- Distraction-Free full-screen workout player with 5-second prep timer.
- Dynamic exercise DSL parsing (Sets, Reps/Timer, Weight, Rest time, Target Muscle Groups).
- **⚡ Superset visual badges** connecting related exercise groups.
- Anatomical **Muscle Group Visualizer** highlighting worked muscles on dynamic canvas.
- Automatic set completion tracking, rest duration extensions, and history logging.

### 4. 📝 Multi-type Notebooks & Local Storage
- Notebook types: Standard, To-Do, Shopping, Habit Tracker, and Workout Planner.
- Drag & Drop reordering, bulk actions, priority badges, and SQLite Room Database persistence.

---

## 📦 Release Artifact Details

| Property | Value |
|---|---|
| **APK File Name** | `FlexiList-v1.0.3.apk` |
| **Location** | Root directory (`/FlexiList-v1.0.3.apk`) |
| **File Size** | ~13 MB (13,418,296 bytes) |
| **SHA-256 Hash** | `1c5fe1284ad232422650ee86eaac70df1078f7d2653f74b1c8495e7bb79482e1` |
| **Signing Cert SHA-256** | `1f69d377fc891db13ffe8c8744a7e14cd22fffd82df4caaaf704567b14c9dba3` |
| **Reproducibility Status** | **100% PASS** (Matches `app-fdroid-release-unsigned.apk` structure) |

---

## 📲 How to Install the APK

1. Locate `FlexiList-v1.0.3.apk` in the root repository folder or download it from the GitHub Release page.
2. Open the APK file on your Android device (API 24 / Android 7.0 or higher).
3. Confirm installation when prompted by Android Package Installer.
4. Launch **FlexiList** and enjoy your distraction-free workout & list manager!

