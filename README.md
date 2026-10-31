# Pokery

An Android app that makes the system-wide changes you would normally need a
computer and `adb` for (removing bloatware, changing animation speed, and so
on) without rooting the phone.

## How it works

Android gives ADB-level powers to the `shell` user (uid 2000), the identity
`adb shell` runs as. Pokery contains its own ADB client
([libadb-android](https://github.com/MuntashirAkon/libadb-android)) and pairs
with the phone's **own Wireless debugging** (Android 11+), then connects to
`127.0.0.1`. The phone is debugging itself, so no computer and no other app
is needed. Anything Pokery does is something you could also do from a
computer with `adb shell`.

After the first connection Pokery grants itself `WRITE_SECURE_SETTINGS`
(over that same ADB connection). Android switches Wireless debugging off on
reboot; with that permission Pokery switches it back on and reconnects by
itself whenever you're on Wi-Fi.

[Shizuku](https://shizuku.rikka.app/) is still supported as an optional
alternative, for example for people who run it with root.

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

1. Turn on Developer options (Settings → About phone → tap *Build number*
   7 times).
2. Install Pokery and open it. On the Status tab tap **Pair**:
   - Pokery opens Developer options. Turn on **Wireless debugging** (choose
     *Always allow on this network*) and open it.
   - Tap **Pair device with pairing code**.
   - A Pokery notification appears; tap **Enter code** and type the 6-digit
     code. Pokery pairs and connects.
3. Done. It reconnects automatically, including after reboots, while on Wi-Fi.

Manual setup (type the pairing port and code yourself, e.g. in split
screen) is available under **Manual setup**.

**Android 8–10:** Wireless debugging pairing doesn't exist. After each
reboot, connect to a computer and run `adb tcpip 5555`, then tap Connect.

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
  adb/AdbShell.kt            built-in ADB client: pairing, connection, exec()
  adb/PairingService.kt      foreground service asking for the pairing code in a notification
  adb/AdbKeys.kt             Pokery's ADB RSA key and certificate
  shell/PrivilegedShell.kt   picks the connected backend (Wireless debugging or Shizuku)
  shell/ShizukuShell.kt      optional Shizuku backend
  shell/ShellService.kt      runs inside Shizuku's process, executes `sh -c`
  shell/AdbCommands.kt       command builders, input validation
  apps/                      package listing and debloat actions
  tweaks/                    settings-based tweaks
  ui/screens/                Compose screens
```

`ShellExecutor` is the interface both backends implement.
