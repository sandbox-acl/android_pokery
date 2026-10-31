package com.pokery.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pokery.app.shell.PrivilegedShell
import com.pokery.app.tweaks.AnimationScale
import com.pokery.app.tweaks.ChoiceTweak
import com.pokery.app.tweaks.Tweaks
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun TweaksScreen(modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    // Bumped after each change so every row re-reads its current value.
    var revision by remember { mutableIntStateOf(0) }

    fun apply(commands: List<String>, done: String) {
        scope.launch {
            val failure = commands.map { PrivilegedShell.exec(it) }.firstOrNull { !it.isSuccess }
            message = failure?.let { "Failed: ${it.output.ifBlank { "exit code ${it.exitCode}" }}" } ?: done
            revision++
        }
    }

    LaunchedEffect(message) {
        if (message != null) {
            delay(3_000)
            message = null
        }
    }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            ScreenTitle("Tweaks")
            NotConnectedBanner()

            Section("Animation speed") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        apply(Tweaks.animations.map { it.command(0.5f) }, "All animations set to 0.5×")
                    }) { Text("All 0.5×") }
                    OutlinedButton(onClick = {
                        apply(Tweaks.animations.map { it.command(0f) }, "Animations turned off")
                    }) { Text("All off") }
                    OutlinedButton(onClick = {
                        apply(Tweaks.animations.map { it.command(AnimationScale.DEFAULT) }, "Animations reset to 1×")
                    }) { Text("Reset") }
                }
                Tweaks.animations.forEach { scale ->
                    AnimationScaleRow(scale, revision) { value ->
                        apply(listOf(scale.command(value)), "${scale.title}: ${AnimationScale.format(value)}×")
                    }
                }
            }

            Section("Display & input") {
                Tweaks.other.forEach { tweak ->
                    TweakRow(tweak, revision) { value, label -> apply(listOf(tweak.command(value)), "${tweak.title}: $label") }
                }
            }
        }

        message?.let { Snackbar(Modifier.align(Alignment.BottomCenter).padding(16.dp)) { Text(it) } }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun AnimationScaleRow(scale: AnimationScale, revision: Int, onCommit: (Float) -> Unit) {
    val resolver = LocalContext.current.contentResolver
    val current = remember(revision) { AnimationScale.snap(scale.read(resolver)) }
    // Follows the finger while dragging; only written to the system when released.
    var draft by remember(current) { mutableFloatStateOf(current) }

    fun commit(value: Float) {
        val snapped = AnimationScale.snap(value)
        draft = snapped
        if (snapped != current) onCommit(snapped)
    }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(scale.title, style = MaterialTheme.typography.titleSmall)
                Text(scale.description, style = MaterialTheme.typography.bodySmall)
            }
            Text(
                "${AnimationScale.format(draft)}×",
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.End,
                modifier = Modifier.widthIn(min = 64.dp),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { commit(draft - AnimationScale.STEP) },
                enabled = draft > AnimationScale.MIN,
            ) { Icon(Icons.Outlined.Remove, "Decrease ${scale.title}") }
            Slider(
                value = draft,
                onValueChange = { draft = AnimationScale.snap(it) },
                onValueChangeFinished = { commit(draft) },
                valueRange = AnimationScale.MIN..AnimationScale.MAX,
                steps = AnimationScale.sliderSteps,
                modifier = Modifier.weight(1f).semantics { contentDescription = scale.title },
            )
            IconButton(
                onClick = { commit(draft + AnimationScale.STEP) },
                enabled = draft < AnimationScale.MAX,
            ) { Icon(Icons.Outlined.Add, "Increase ${scale.title}") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TweakRow(tweak: ChoiceTweak, revision: Int, onSelect: (value: String, label: String) -> Unit) {
    val resolver = LocalContext.current.contentResolver
    val current = remember(revision) { tweak.read(resolver) ?: tweak.defaultValue }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(tweak.title, style = MaterialTheme.typography.titleSmall)
        Text(tweak.description, style = MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            tweak.choices.forEach { (label, value) ->
                FilterChip(
                    selected = sameValue(current, value),
                    onClick = { onSelect(value, label) },
                    label = { Text(label) },
                )
            }
        }
    }
}

/** Settings store "1", "1.0" and so on interchangeably. */
private fun sameValue(a: String, b: String): Boolean {
    val x = a.toFloatOrNull()
    val y = b.toFloatOrNull()
    return if (x != null && y != null) x == y else a == b
}
