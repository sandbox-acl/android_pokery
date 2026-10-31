package com.pokery.app.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pokery.app.apps.AppAction
import com.pokery.app.apps.AppEntry
import com.pokery.app.apps.AppFilter
import com.pokery.app.apps.AppRepository
import com.pokery.app.apps.AppState
import com.pokery.app.apps.AppsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun AppsScreen(modifier: Modifier = Modifier, vm: AppsViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    var selected by remember { mutableStateOf<AppEntry?>(null) }
    var pending by remember { mutableStateOf<Pair<AppEntry, AppAction>?>(null) }

    LaunchedEffect(state.message) {
        if (state.message != null) {
            delay(3_500)
            vm.consumeMessage()
        }
    }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ScreenTitle("Debloat", Modifier.weight(1f))
                IconButton(onClick = vm::reload) { Icon(Icons.Outlined.Refresh, "Reload") }
            }
            NotConnectedBanner()
            OutlinedTextField(
                value = state.query,
                onValueChange = vm::setQuery,
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                placeholder = { Text("Search name or package") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppFilter.entries.forEach { f ->
                    FilterChip(selected = state.filter == f, onClick = { vm.setFilter(f) }, label = { Text(f.label) })
                }
            }
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())

            if (state.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                val visible = state.visible
                Text(
                    "${visible.size} apps",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                LazyColumn(Modifier.fillMaxSize()) {
                    items(visible, key = { it.packageName }) { app ->
                        AppRow(app) { selected = app }
                        HorizontalDivider()
                    }
                }
            }
        }

        state.message?.let {
            Snackbar(Modifier.align(Alignment.BottomCenter).padding(16.dp)) { Text(it) }
        }
    }

    selected?.let { app ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(app.label) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(app.packageName, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    Text("${if (app.isSystem) "System app" else "User app"} · ${app.state}")
                    Spacer(Modifier.size(8.dp))
                    vm.actionsFor(app).forEach { action ->
                        TextButton(onClick = {
                            selected = null
                            pending = app to action
                        }) { Text(action.label) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { selected = null }) { Text("Close") } },
        )
    }

    pending?.let { (app, action) ->
        val critical = AppRepository.isCritical(app.packageName)
        val destructive = action == AppAction.Remove || action == AppAction.Disable
        AlertDialog(
            onDismissRequest = { pending = null },
            icon = if (critical && destructive) ({ Icon(Icons.Outlined.Warning, null) }) else null,
            title = { Text("${action.label}?") },
            text = {
                Text(
                    buildString {
                        append(app.label).append(" (").append(app.packageName).append(")\n\n")
                        when (action) {
                            AppAction.Remove -> append(
                                "The app is removed for your user. Its data is kept, and system apps " +
                                    "can be brought back from the Removed filter. A factory reset also restores it."
                            )
                            AppAction.Disable -> append("The app stops running and disappears from the launcher until you enable it.")
                            AppAction.Restore -> append("Reinstalls the copy that's still on the system.")
                            AppAction.Enable -> append("Turns the app back on.")
                            AppAction.ForceStop -> append("Stops all of the app's processes.")
                        }
                        if (critical && destructive) {
                            append(
                                "\n\nWarning: this is a core system component. Removing or disabling it can " +
                                    "cause crashes or a boot loop that needs a factory reset."
                            )
                        }
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.perform(app, action)
                    pending = null
                }) { Text(if (critical && destructive) "Do it anyway" else action.label) }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun AppRow(app: AppEntry, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(app.packageName)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                app.packageName,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (AppRepository.isCritical(app.packageName)) {
            Icon(Icons.Outlined.Warning, "Core component", tint = MaterialTheme.colorScheme.error)
        }
        if (app.state != AppState.Enabled) {
            Text(app.state.name, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun AppIcon(packageName: String) {
    val context = LocalContext.current
    var bitmap by remember(packageName) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(packageName) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                context.packageManager.getApplicationIcon(packageName).toBitmap(96, 96)
            }.getOrNull()
        }
    }
    val image = bitmap
    if (image != null) {
        Image(image.asImageBitmap(), contentDescription = null, modifier = Modifier.size(40.dp))
    } else {
        Icon(Icons.Outlined.Android, null, modifier = Modifier.size(40.dp))
    }
}
