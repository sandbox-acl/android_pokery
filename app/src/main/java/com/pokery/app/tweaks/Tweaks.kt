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

object Tweaks {

    private val animationChoices = listOf(
        "Off" to "0", "0.25×" to "0.25", "0.5×" to "0.5", "0.75×" to "0.75",
        "1× (default)" to "1", "1.5×" to "1.5", "2×" to "2",
    )

    val animationKeys = listOf(
        Settings.Global.WINDOW_ANIMATION_SCALE,
        Settings.Global.TRANSITION_ANIMATION_SCALE,
        Settings.Global.ANIMATOR_DURATION_SCALE,
    )

    val animations = listOf(
        ChoiceTweak(
            "Window animation scale",
            "Opening and closing dialogs and windows.",
            Namespace.GLOBAL, Settings.Global.WINDOW_ANIMATION_SCALE, animationChoices, "1",
        ),
        ChoiceTweak(
            "Transition animation scale",
            "Moving between apps and screens.",
            Namespace.GLOBAL, Settings.Global.TRANSITION_ANIMATION_SCALE, animationChoices, "1",
        ),
        ChoiceTweak(
            "Animator duration scale",
            "Animations inside apps. \"Off\" also stops some progress spinners.",
            Namespace.GLOBAL, Settings.Global.ANIMATOR_DURATION_SCALE, animationChoices, "1",
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
