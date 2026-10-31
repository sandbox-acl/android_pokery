package com.pokery.app.tweaks

import org.junit.Assert.assertEquals
import org.junit.Test

class AnimationScaleTest {

    @Test
    fun snapsToNearestFiveHundredths() {
        assertEquals(0.95f, AnimationScale.snap(0.96f), 0f)
        assertEquals(1.15f, AnimationScale.snap(1.1374f), 0f)
        assertEquals(0.3f, AnimationScale.snap(0.1f + 0.2f), 0f)
    }

    @Test
    fun clampsToRange() {
        assertEquals(0f, AnimationScale.snap(-0.3f), 0f)
        assertEquals(2f, AnimationScale.snap(7f), 0f)
    }

    @Test
    fun formatsForSettingsPut() {
        assertEquals("0.95", AnimationScale.format(0.95f))
        assertEquals("1.15", AnimationScale.format(1.15f))
        assertEquals("1", AnimationScale.format(1f))
        assertEquals("0.5", AnimationScale.format(0.5f))
        assertEquals("0", AnimationScale.format(0f))
        assertEquals("0.05", AnimationScale.format(0.05f))
    }

    @Test
    fun everySliderStopIsAStep() {
        // 0, 0.05, ..., 2.0 is 41 positions: the two ends plus 39 intermediate steps.
        assertEquals(39, AnimationScale.sliderSteps)
    }

    @Test
    fun buildsSettingsCommand() {
        val window = Tweaks.animations.first()
        assertEquals("settings put global window_animation_scale 0.95", window.command(0.95f))
    }
}
