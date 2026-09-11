# Smart Grinder Android App

Companion app for the ESP32-C3 firmware in this repo - connects over BLE,
shows live weight/state, and manages dosing-cup auto-start profiles. See
`docs/DEVELOPMENT-LOG.md` ("Major pivot" entry) for the full BLE protocol
and architecture writeup - the wire format is duplicated by hand in
`app/src/main/java/com/agung/smartgrinder/ble/BleProtocol.kt` and must
stay in sync with `include/ble.h`/`src/ble.cpp` on the firmware side.

## Opening the project

Open this `android-app/` folder (not the repo root) in Android Studio -
it'll detect the Gradle project and sync automatically, generating the
Gradle wrapper jar/scripts on first sync if they're missing.

## Building from the command line

```
cd android-app
./gradlew assembleDebug
```

The debug APK lands in `app/build/outputs/apk/debug/`.

## Status

Not yet built or run - written and reviewed for logical correctness, but
this environment has no Android SDK/emulator to compile or test against.
First real build/run needs to happen in Android Studio; expect some
iteration (dependency versions, API-level quirks) on the first attempt.
