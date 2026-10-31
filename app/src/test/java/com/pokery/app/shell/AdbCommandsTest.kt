package com.pokery.app.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AdbCommandsTest {

    @Test
    fun packageCommandsMatchAdbSyntax() {
        assertEquals("pm uninstall -k --user 0 com.facebook.katana", AdbCommands.uninstallForUser("com.facebook.katana"))
        assertEquals("cmd package install-existing --user 0 com.facebook.katana", AdbCommands.restore("com.facebook.katana"))
        assertEquals("pm disable-user --user 0 com.example.app", AdbCommands.disable("com.example.app"))
        assertEquals("pm enable --user 0 com.example.app", AdbCommands.enable("com.example.app"))
        assertEquals("am force-stop com.example.app", AdbCommands.forceStop("com.example.app"))
    }

    @Test
    fun settingsCommands() {
        assertEquals(
            "settings put global window_animation_scale 0.5",
            AdbCommands.putSetting(AdbCommands.Namespace.GLOBAL, "window_animation_scale", "0.5"),
        )
        assertEquals("settings get system show_touches", AdbCommands.getSetting(AdbCommands.Namespace.SYSTEM, "show_touches"))
    }

    @Test
    fun rejectsShellInjection() {
        assertFalse(AdbCommands.isValidPackageName("com.example; reboot"))
        assertFalse(AdbCommands.isValidPackageName("android"))
        assertTrue(AdbCommands.isValidPackageName("com.google.android.youtube"))
        assertThrows(IllegalArgumentException::class.java) { AdbCommands.uninstallForUser("x.y && rm -rf /sdcard") }
        assertThrows(IllegalArgumentException::class.java) {
            AdbCommands.putSetting(AdbCommands.Namespace.GLOBAL, "window_animation_scale", "1; reboot")
        }
    }

    @Test
    fun stripsAdbShellPrefix() {
        assertEquals("pm list packages", AdbCommands.normalizeUserCommand("adb shell pm list packages"))
        assertEquals("pm list packages", AdbCommands.normalizeUserCommand("  adb -d shell pm list packages "))
        assertEquals("settings get global x", AdbCommands.normalizeUserCommand("adb -s R58M123 shell settings get global x"))
        assertEquals("pm list packages", AdbCommands.normalizeUserCommand("pm list packages"))
    }
}
