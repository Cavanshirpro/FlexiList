# Changelog

All notable changes to **FlexiList** will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.0.1] - 2026-08-07

### Added
- **Complete Multi-Language Localization (i18n)**:
  - English 🇬🇧, Turkish 🇹🇷, Russian 🇷🇺, German 🇩🇪, Spanish 🇪🇸, and French 🇫🇷 translations.
  - Fully localized target muscle group names (*Chest, Back, Shoulders, Arms, Legs, Core/Abs*).
  - Localized workout frequency weekday indicators (*Mon, Tue, Wed...*).
  - Dynamic localized priority names (*None, Low, Medium, High, Level X*).
  - Localized theme accent names and cloud backup/restore dialog instructions.
- **F-Droid Publishing Readiness**:
  - Added `.fdroid.yml` manifest and Fastlane metadata directory structure (`fastlane/metadata/android/`).
  - Verified 100% open-source compliance with no proprietary ad SDKs or closed trackers.
- **GitHub Automation**:
  - Added GitHub Actions workflow (`.github/workflows/android-build.yml`) for automated CI/CD build and tag releases.
- **Enhanced UI & Accessibility**:
  - Tactile haptic feedback integration for task actions.
  - Smooth horizontal scrolling for exercise chips and day filter navigation bars.

### Fixed
- Fixed layout scrolling and clipping issues on small screen devices in `WorkoutPlayerScreen` and dialogs.
- Fixed string resource duplication conflicts in `strings.xml`.
