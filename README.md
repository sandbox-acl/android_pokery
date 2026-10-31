# Pokery

An Android app that makes the system-wide changes you would normally need a
computer and `adb` for (removing bloatware, changing animation speed, and so
on) without rooting the phone.

## How it works

Android gives ADB-level powers to the `shell` user (uid 2000), the identity
`adb shell` runs as. An ordinary app can't become that user, but
[Shizuku](https://shizuku.rikka.app/) can start a helper process as `shell`,
either over **Wireless debugging** (Android 11+, no computer needed) or with
one `adb` command over USB. Pokery binds a small service
(`ShellService`) into that process and sends its commands there. Anything
Pokery does is something you could also do from a computer with `adb shell`.

## Features

| Tab | What it does | Commands used |
| --- | --- | --- |
| Status | Checks for Shizuku, asks for permission, explains setup | — |
| Debloat | Lists system and user apps, with search and filters. Uninstall for the current user, disable, enable, restore, force stop. Core components are flagged and need a second confirmation | `pm uninstall -k --user 0`, `pm disable-user`, `pm enable`, `cmd package install-existing`, `am force-stop` |
| Tweaks | Window, transition and animator animation scales (presets: all 0.5×, all off, reset). Show taps. Stay awake while charging | `settings put global …`, `settings put system …` |
| Shell | Runs any command as `adb shell` would. Paste full `adb shell …` lines; the prefix is removed | anything |

"Uninstall for user" keeps the APK on the system partition, so a removed
system app can be brought back from the **Removed** filter, or by a factory
reset.

## Setup on the phone

1. Install Shizuku from Google Play or its GitHub releases.
2. Start Shizuku:
   - **Android 11+:** Settings → Developer options → Wireless debugging → on.
     In Shizuku tap *Pairing*, enter the code, then *Start*.
   - **USB:** connect to a computer with platform-tools and run the
     *Start via ADB* command Shizuku shows.
3. Install Pokery, open it and tap **Grant access**.

Shizuku stops on reboot; start it again after restarting the phone. Changes
you made remain.

## Building

Requirements: JDK 17+, Android SDK with platform 35.

```sh
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest      # unit tests
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Every push also builds a debug APK in GitHub Actions; download it from the
run's *Artifacts* section. CI builds are signed with a throwaway debug key, so
uninstall the previous build before installing one from a different source.

## Project layout

```
app/src/main/aidl/.../IShellService.aidl   binder interface for the privileged service
app/src/main/java/com/pokery/app/
  shell/ShellService.kt      runs inside Shizuku's process, executes `sh -c`
  shell/PrivilegedShell.kt   Shizuku connection state, binding, exec()
  shell/AdbCommands.kt       command builders, input validation
  apps/                      package listing and debloat actions
  tweaks/                    settings-based tweaks
  ui/screens/                Compose screens
```

`ShellExecutor` is the seam for adding another backend later, such as a
built-in wireless-debugging ADB client that removes the Shizuku dependency.
