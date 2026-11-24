# Kinsake for Android

This directory contains the native Kotlin/Jetpack Compose Android application. It is separate from the existing browser app in the repository root.

## Build a debug APK

Requirements: JDK 17 and Android SDK Platform 35.

```bash
cd android
gradle :app:assembleDebug
```

The APK is created at `app/build/outputs/apk/debug/app-debug.apk`.

## Local storage design

- Room/SQLite stores memory metadata.
- Imported photo originals and recorded voice files are copied into the app-private `filesDir/memories/` directory.
- The Android Photo Picker grants access only to the media the user selects.
- The app does not request broad photo-library access and does not declare INTERNET permission.

**Important:** Android removes app-private data when Kinsake is uninstalled or its app data is cleared. The initial MVP does not yet include portable backup/restore. Do not use it as the only copy of important family photos until backup/restore is implemented and tested.
