package com.pokery.app.tweaks

import android.content.ContentResolver
import android.provider.Settings
import com.pokery.app.shell.AdbCommands
import com.pokery.app.shell.AdbCommands.Namespace

/** A system setting with a fixed set of choices, changed with `settings put`. */
data class ChoiceTweak(
    val title: String,
    val description: String,
    val namespace: Namespace,
    val key: String,
    val choices: List<Pair<String, String>>, // label to value
    val defaultValue: String,
) {
    fun read(resolver: ContentResolver): String? = runCatching {
        // Reading these settings needs no special permission.
        when (namespace) {
            Namespace.GLOBAL -> Settings.Global.getString(resolver, key)
            Namespace.SECURE -> Settings.Secure.getString(resolver, key)
            Namespace.SYSTEM -> Settings.System.getString(resolver, key)
        }
    }.getOrNull()

    fun command(value: String) = AdbCommands.putSetting(namespace, key, value)
}

/** One of the three global animation scales, adjustable in [STEP] increments. */
data class AnimationScale(val title: String, val description: String, val key: String) {

    fun read(resolver: ContentResolver): Float =
        runCatching { Settings.Global.getFloat(resolver, key, DEFAULT) }.getOrDefault(DEFAULT)

    fun command(scale: Float) = AdbCommands.putSetting(Namespace.GLOBAL, key, format(scale))

    companion object {
        const val MIN = 0f
        const val MAX = 2f
        const val STEP = 0.05f
        const val DEFAULT = 1f

        /** Number of intermediate stops for a Slider covering [MIN]..[MAX]. */
        val sliderSteps: Int = Math.round((MAX - MIN) / STEP) - 1

        /** Rounds to the nearest [STEP] and clamps to the allowed range. */
        fun snap(value: Float): Float {
            val stepped = Math.round(value / STEP) * STEP
            return (Math.round(stepped * 100f) / 100f).coerceIn(MIN, MAX)
        }

        /** "0.95", "1.15", "1", "0.5": the shortest exact form, suitable for `settings put`. */
        fun format(value: Float): String =
            java.math.BigDecimal(snap(value).toString()).stripTrailingZeros().toPlainString()
    }
}

object Tweaks {

    val animations = listOf(
        AnimationScale(
            "Window animation scale",
            "Opening and closing dialogs and windows.",
            Settings.Global.WINDOW_ANIMATION_SCALE,
        ),
        AnimationScale(
            "Transition animation scale",
            "Moving between apps and screens.",
            Settings.Global.TRANSITION_ANIMATION_SCALE,
        ),
        AnimationScale(
            "Animator duration scale",
            "Animations inside apps. 0× also stops some progress spinners.",
            Settings.Global.ANIMATOR_DURATION_SCALE,
        ),
    )

    val other = listOf(
        ChoiceTweak(
            "Show taps",
            "Draws a dot where you touch the screen. Useful for screen recordings.",
            Namespace.SYSTEM, "show_touches", listOf("Off" to "0", "On" to "1"), "0",
        ),
        ChoiceTweak(
            "Stay awake while charging",
            "Keeps the screen on while plugged in.",
            Namespace.GLOBAL, Settings.Global.STAY_ON_WHILE_PLUGGED_IN,
            // Bit flags: 1 = AC, 2 = USB, 4 = wireless.
            listOf("Off" to "0", "On" to "7"), "0",
        ),
    )
}
