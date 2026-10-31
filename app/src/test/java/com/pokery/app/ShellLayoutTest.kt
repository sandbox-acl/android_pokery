package com.pokery.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pokery.app.ui.screens.SHELL_INPUT_TAG
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

@RunWith(AndroidJUnit4::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class ShellLayoutTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun inputBarIsFullyVisibleAboveNavigation() {
        compose.onNodeWithText("Shell", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        val input = compose.onNodeWithTag(SHELL_INPUT_TAG)
        input.assertIsDisplayed()
        val inputBottom = input.getBoundsInRoot().bottom
        val navTop = compose.onNodeWithText("Status", useUnmergedTree = true).getBoundsInRoot().top
        assertTrue("input bottom $inputBottom overlaps navigation bar top $navTop", inputBottom <= navTop - 8.dp)
        assertTrue(inputBottom <= compose.onRoot().getBoundsInRoot().bottom)
    }
}
