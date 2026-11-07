# SWF Pocket for Android

An Android starter player for local, self-contained SWF animations and games using Ruffle in a WebView. Android 8.0+ (API 26). No Adobe plugin or storage permission required.

## Build

Prerequisites: Android Studio with Android SDK 35, JDK 17+, and Node/npm for fetching the bundled runtime.

1. Run `./tools/bundle-ruffle.sh` with network access. This copies the complete official `@ruffle-rs/ruffle` npm package, including JavaScript, WASM, and license files, into the app assets. The resolved version is recorded in `RUFFLE_VERSION.txt`. For repeatable upgrades, supply an exact npm package spec as its argument and retain the bundled files.
2. Open this folder in Android Studio and let Gradle sync. Use Studio's embedded Gradle JDK 17 or 21.
3. Choose **Build > Build App Bundle(s) / APK(s) > Build APK(s)**, or run `gradle :app:assembleDebug` using Gradle 8.9.
4. Install `app/build/outputs/apk/debug/app-debug.apk` on your Android device.

The runtime is not included in this checkout: the creation environment's proxy was unavailable. There is no Gradle wrapper in this checkout; Android Studio can download the required Gradle distribution. No APK has been compiled or device-tested yet.

## Use

Tap **Open SWF**, choose a file through Android's document picker, and tap the movie to start audio/playback. Pause/resume, restart, fullscreen, and a touch keyboard (arrows, Space, Enter, Z, X) are provided. A hardware keyboard can also be used. The touch keyboard needs validation against real games and WebView versions.

The app validates the SWF signature and streams the selected document through a private HTTPS origin. Ruffle and WASM load from bundled assets, so playback works offline after installation. File names are inserted as text. Flash-to-JavaScript access, outgoing navigation, and external network requests are disabled; the app does not request Android's INTERNET permission. The native bridge only opens the Android document picker. Playback pauses when the activity goes into the background; tap Resume when returning.

## Compatibility and scope

Ruffle emulates Flash; support varies for ActionScript 1/2/3 and individual Flash APIs. This is a starter implementation, not a claim of universal compatibility. Movies needing sibling files, remote resources, obsolete online services, DRM, or unsupported APIs may fail. Folder imports and external resources are not implemented. Large files and demanding games depend on device memory and GPU capabilities. Fullscreen currently shows the movie; the app's touch keyboard stays outside fullscreen. Save persistence and behavior across app restarts require device validation.

## Validation still required

On a real device, test self-contained AS1/2 and AS3 files, compressed CWS/ZWS files, audio after a user gesture, hardware and touch keys, fullscreen/back, pause/resume, rotation, document-provider errors, malformed files, and blocked external requests. Ruffle is third-party software: review and preserve the bundled package's licenses before distribution.
