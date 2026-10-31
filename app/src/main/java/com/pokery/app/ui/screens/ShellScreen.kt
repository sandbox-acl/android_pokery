package com.pokery.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.pokery.app.shell.AdbCommands
import com.pokery.app.shell.PrivilegedShell
import com.pokery.app.shell.ShellResult
import kotlinx.coroutines.launch

const val SHELL_INPUT_TAG = "shell-input"

private data class ShellEntry(val command: String, val result: ShellResult)

@Composable
fun ShellScreen(modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val history = remember { mutableStateListOf<ShellEntry>() }
    var input by rememberSaveable { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(history.size) {
        if (history.isNotEmpty()) listState.animateScrollToItem(history.lastIndex)
    }

    fun run() {
        val command = AdbCommands.normalizeUserCommand(input)
        if (command.isEmpty() || running) return
        running = true
        scope.launch {
            history += ShellEntry(command, PrivilegedShell.exec(command))
            input = ""
            running = false
        }
    }

    Column(modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScreenTitle("Shell", Modifier.weight(1f))
            IconButton(onClick = { history.clear() }) { Icon(Icons.Outlined.DeleteSweep, "Clear") }
        }
        NotConnectedBanner()
        Text(
            "Runs like `adb shell <command>` from a computer. You can paste full adb commands; " +
                "the leading `adb shell` is removed. Commands stop after 30 seconds.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        // The weighted Box takes whatever height is left, so the input row below always fits.
        Box(Modifier.weight(1f).fillMaxWidth()) {
            SelectionContainer {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(history) { entry ->
                        Column(Modifier.fillMaxWidth().padding(8.dp)) {
                            Text("$ ${entry.command}", fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
                            if (entry.result.output.isNotEmpty()) {
                                Text(entry.result.output, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                            }
                            if (!entry.result.isSuccess) {
                                Text(
                                    "exit ${entry.result.exitCode}",
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("pm list packages -d", fontFamily = FontFamily.Monospace) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send, autoCorrectEnabled = false),
                keyboardActions = KeyboardActions(onSend = { run() }),
                modifier = Modifier.weight(1f).testTag(SHELL_INPUT_TAG),
            )
            if (running) {
                CircularProgressIndicator(Modifier.padding(start = 12.dp))
            } else {
                IconButton(onClick = ::run) { Icon(Icons.AutoMirrored.Outlined.Send, "Run") }
            }
        }
    }
}
