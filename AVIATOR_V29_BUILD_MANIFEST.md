# Aviator Predictor V29 Build Manifest

## Target
- Android application ID: `com.aviator.live`
- Version code: `29`
- Version name: `29.0-Welcome-Splash`
- Main activity: `com.aviator.live.MainActivity`
- Android compile SDK: `35`
- Android Gradle Plugin: `8.7.3`
- Gradle: `8.9`
- Java: `17`
- Dependency: `com.google.mlkit:text-recognition:16.0.1`

## Required V29 source archive
Expected filename: `Aviator-Predictor-v29-Welcome-Screen-source.zip`

The source archive currently available in the ChatGPT workspace has not been uploaded into this GitHub branch. Do not build the existing sample app or the older V25 archive and label it V29.

## Expected V29 features to preserve
- Approximately 3-second welcome/splash screen.
- Welcome credit: **DEVELOPED BY — Ebiwari Sunshine Pereyei O.**
- Existing OCR/screen capture, round history, prediction model, and cloud synchronization from the V28-based source.

## Build procedure after the correct source is transferred
1. Extract the V29 source archive into the repository root.
2. Ensure `build.gradle`, `settings.gradle`, `AndroidManifest.xml`, `src/`, and `res/` are present at the expected paths.
3. Run `gradle --no-daemon --stacktrace assembleDebug` on a runner with JDK 17, Gradle 8.9, Android SDK platform 35, and required Android build tools.
4. Verify the APK's application ID and version metadata, inspect the build logs, and install/launch-test it before distributing it.

## Current status
This manifest records the build requirements only. It is not evidence that V29 has compiled or that an APK has been produced or tested.
