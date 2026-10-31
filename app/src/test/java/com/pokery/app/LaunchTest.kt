package com.pokery.app

import android.content.pm.PackageInfo
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pokery.app.crash.CrashReportActivity
import com.pokery.app.crash.CrashReporter
import com.pokery.app.shell.PrivilegedShell
import com.pokery.app.shell.ShizukuShell
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

@RunWith(AndroidJUnit4::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [26, 28, 30, 33, 35])
class LaunchTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun everyTabRenders() {
        compose.onNodeWithText("Not connected").assertExists()
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            compose.onNodeWithText("Not paired yet.").assertExists()
        } else {
            compose.onNodeWithText("adb tcpip 5555", substring = true).assertExists()
        }
        listOf("Debloat", "Tweaks", "Shell", "Status").forEach { tab ->
            compose.onNodeWithText(tab, useUnmergedTree = true).performClick()
            compose.waitForIdle()
        }
    }
}

@RunWith(AndroidJUnit4::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [26, 35])
class ShizukuInstalledTest {

    @Test
    fun launchesWhenShizukuIsInstalledButNotRunning() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        shadowOf(app.packageManager).installPackage(PackageInfo().apply {
            packageName = ShizukuShell.SHIZUKU_PACKAGE
        })
        PrivilegedShell.refresh()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }
}

@RunWith(AndroidJUnit4::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [35])
class CrashReportTest {

    @Test
    fun pendingReportOpensCrashScreen() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        CrashReporter.record(app, "test", IllegalStateException("boom"))
        ActivityScenario.launch(MainActivity::class.java).use {
            val next = shadowOf(app).nextStartedActivity
            assertNotNull(next)
            assertEquals(CrashReportActivity::class.java.name, next.component?.className)
        }
        CrashReporter.clear(app)
    }
}
