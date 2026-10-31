package com.pokery.app.ui.screens

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.pokery.app.adb.AdbConnection
import com.pokery.app.adb.AdbShell
import com.pokery.app.adb.PairingService
import com.pokery.app.adb.PairingStatus
import com.pokery.app.shell.Backend
import com.pokery.app.shell.PrivilegedShell
import com.pokery.app.shell.ShizukuShell
import com.pokery.app.shell.ShizukuStatus
import kotlinx.coroutines.launch

private const val SHIZUKU_DOWNLOAD = "https://shizuku.rikka.app/download/"

@Composable
fun StatusScreen(modifier: Modifier = Modifier) {
    val active by PrivilegedShell.active.collectAsState()
    // Bumped on resume so system checks (Wi-Fi, developer options) are re-read.
    var resumes by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumes++ }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenTitle("Pokery")
        OverallCard(active)
        WirelessDebuggingCard(resumes)
        ShizukuCard()
    }
}

@Composable
private fun OverallCard(active: Backend?) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (active != null) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (active != null) "Ready" else "Not connected", style = MaterialTheme.typography.titleMedium)
            Text(
                if (active != null) "Commands run with ADB shell privileges through ${active.label}."
                else "Pokery needs ADB-level access for its tools. Set up Wireless debugging below. " +
                    "It's built in, so no other apps or computer are needed.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WirelessDebuggingCard(resumes: Int) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by AdbShell.state.collectAsState()
    val pairing by AdbShell.pairing.collectAsState()
    var confirmForget by remember { mutableStateOf(false) }
    var showManual by rememberSaveable { mutableStateOf(false) }

    val devOptions = remember(resumes) { AdbShell.isDeveloperOptionsOn(context) }
    val onWifi = remember(resumes) { AdbShell.isOnWifi(context) }
    val canToggle = remember(resumes, state) { AdbShell.canToggleWirelessDebugging(context) }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        startPairing(context)
    }

    fun pair() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startPairing(context)
        }
    }

    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Wireless debugging", style = MaterialTheme.typography.titleMedium)

            if (!AdbShell.supportsWirelessPairing) {
                Text(
                    "Wireless debugging pairing needs Android 11 or newer. On this phone, connect it to a " +
                        "computer once after each reboot and run:\n\nadb tcpip 5555\n\nThen tap Connect.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                ConnectionButtons(state.connection, onConnect = { scope.launch { AdbShell.connect(port = 5555) } })
                state.message?.let { ErrorText(it) }
                return@Column
            }

            val statusText = when {
                state.connection == AdbConnection.Connected -> "Connected."
                state.connection == AdbConnection.Connecting -> "Connecting…"
                state.paired -> "Paired, not connected."
                else -> "Not paired yet."
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.connection == AdbConnection.Connecting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(statusText, style = MaterialTheme.typography.bodyLarge)
            }
            state.message?.let { ErrorText(it) }

            if (!devOptions) {
                Hint("Developer options are off. Open Settings → About phone and tap Build number 7 times.")
            }
            if (!onWifi && state.connection != AdbConnection.Connected) {
                Hint("Wireless debugging only works while connected to Wi-Fi.")
            }

            when {
                state.connection == AdbConnection.Connected -> {
                    if (canToggle) {
                        Hint("After a reboot, Pokery turns Wireless debugging back on and reconnects by itself while you're on Wi-Fi.")
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = AdbShell::disconnect) { Text("Disconnect") }
                        TextButton(onClick = { confirmForget = true }) { Text("Forget pairing") }
                    }
                }
                state.paired -> {
                    if (!canToggle) Hint("Turn Wireless debugging on in Settings, then tap Connect.")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { scope.launch { AdbShell.connect() } },
                            enabled = state.connection == AdbConnection.Disconnected,
                        ) { Text("Connect") }
                        OutlinedButton(onClick = { openWirelessDebugging(context) }) { Text("Open Settings") }
                        TextButton(onClick = { confirmForget = true }) { Text("Forget pairing") }
                    }
                }
                else -> {
                    Text(
                        "One-time setup:\n" +
                            "1. Tap Pair. Pokery opens Developer options.\n" +
                            "2. Turn on Wireless debugging (choose \"Always allow on this network\").\n" +
                            "3. Tap it to open it, then tap \"Pair device with pairing code\".\n" +
                            "4. A Pokery notification appears. Tap \"Enter code\" and type the 6-digit code.\n\n" +
                            "Pokery then connects to the phone itself, with no computer involved.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    PairingProgress(pairing)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val busy = pairing is PairingStatus.Searching || pairing is PairingStatus.WaitingForCode ||
                            pairing is PairingStatus.Pairing
                        Button(onClick = ::pair, enabled = !busy) { Text("Pair") }
                        if (busy) OutlinedButton(onClick = { PairingService.stop(context) }) { Text("Cancel") }
                    }
                }
            }

            TextButton(onClick = { showManual = !showManual }) {
                Text(if (showManual) "Hide manual setup" else "Manual setup")
            }
            if (showManual) ManualSetup()
        }
    }

    if (confirmForget) {
        AlertDialog(
            onDismissRequest = { confirmForget = false },
            title = { Text("Forget pairing?") },
            text = {
                Text(
                    "Pokery deletes its key and disconnects. To use it again you'll need to pair again. " +
                        "You can also remove \"Pokery\" from Wireless debugging → Paired devices."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmForget = false
                    AdbShell.forget()
                }) { Text("Forget") }
            },
            dismissButton = { TextButton(onClick = { confirmForget = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun PairingProgress(pairing: PairingStatus) {
    val text = when (pairing) {
        PairingStatus.Idle -> return
        PairingStatus.Searching -> "Waiting for the pairing code screen in Settings…"
        is PairingStatus.WaitingForCode -> "Pairing code screen found. Enter the code in the Pokery notification."
        PairingStatus.Pairing -> "Pairing…"
        PairingStatus.Done -> "Paired and connected."
        is PairingStatus.Failed -> pairing.message
    }
    if (pairing is PairingStatus.Failed) ErrorText(text) else Hint(text)
}

@Composable
private fun ConnectionButtons(connection: AdbConnection, onConnect: () -> Unit) {
    if (connection == AdbConnection.Connected) {
        OutlinedButton(onClick = AdbShell::disconnect) { Text("Disconnect") }
    } else {
        Button(onClick = onConnect, enabled = connection == AdbConnection.Disconnected) { Text("Connect") }
    }
}

/**
 * For people who'd rather type the port and code themselves, e.g. with Settings and
 * Pokery side by side in split screen.
 */
@Composable
private fun ManualSetup() {
    val scope = rememberCoroutineScope()
    var pairPort by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var connectPort by rememberSaveable { mutableStateOf("") }
    var result by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Hint(
            "In \"Pair device with pairing code\", the port is the number after the colon in " +
                "\"IP address & Port\". Use split screen so the dialog stays open."
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField("Pairing port", pairPort, { pairPort = it }, Modifier.weight(1f))
            NumberField("Code", code, { code = it }, Modifier.weight(1f))
        }
        Button(
            enabled = !busy && pairPort.toIntOrNull() != null && code.length >= 6,
            onClick = {
                busy = true
                scope.launch {
                    val r = AdbShell.pair(pairPort.toInt(), code)
                    result = r.exceptionOrNull()?.message ?: run {
                        val c = AdbShell.connect()
                        c.exceptionOrNull()?.let { "Paired, but couldn't connect: ${it.message}" } ?: "Paired and connected."
                    }
                    busy = false
                }
            },
        ) { Text("Pair") }

        Hint("Already paired? Connect to the port shown on the Wireless debugging screen (or 5555 after `adb tcpip 5555`).")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            NumberField("Port", connectPort, { connectPort = it }, Modifier.weight(1f))
            Button(
                enabled = !busy && connectPort.toIntOrNull() != null,
                onClick = {
                    busy = true
                    scope.launch {
                        result = AdbShell.connect(port = connectPort.toInt()).exceptionOrNull()?.message ?: "Connected."
                        busy = false
                    }
                },
            ) { Text("Connect") }
        }
        result?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() }.take(6)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

@Composable
private fun ShizukuCard() {
    val context = LocalContext.current
    val status by ShizukuShell.status.collectAsState()
    var expanded by rememberSaveable { mutableStateOf(false) }
    val inUse = status is ShizukuStatus.Ready

    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Shizuku (optional)", style = MaterialTheme.typography.titleMedium)
                    Text(shizukuSummary(status), style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { expanded = !expanded }) { Text(if (expanded || inUse) "Hide" else "Show") }
            }
            if (!expanded && !inUse) return@Column
            Text(
                "If you already use Shizuku (for example with root), Pokery can use it instead of Wireless debugging. " +
                    "You don't need it otherwise.",
                style = MaterialTheme.typography.bodyMedium,
            )
            when (status) {
                ShizukuStatus.NotInstalled -> OutlinedButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SHIZUKU_DOWNLOAD)))
                }) { Text("Get Shizuku") }
                ShizukuStatus.NotRunning, ShizukuStatus.Unsupported -> OutlinedButton(onClick = {
                    context.packageManager.getLaunchIntentForPackage(ShizukuShell.SHIZUKU_PACKAGE)
                        ?.let(context::startActivity)
                }) { Text("Open Shizuku") }
                ShizukuStatus.PermissionRequired, ShizukuStatus.PermissionDenied ->
                    Button(onClick = ShizukuShell::requestPermission) { Text("Allow Pokery in Shizuku") }
                else -> Unit
            }
        }
    }
}

private fun shizukuSummary(status: ShizukuStatus) = when (status) {
    ShizukuStatus.NotInstalled -> "Not installed."
    ShizukuStatus.NotRunning -> "Installed, not running."
    ShizukuStatus.Unsupported -> "Too old; needs version 11 or newer."
    ShizukuStatus.PermissionRequired -> "Running. Pokery needs permission."
    ShizukuStatus.PermissionDenied -> "Running. Permission was denied."
    ShizukuStatus.Connecting -> "Connecting…"
    is ShizukuStatus.Ready -> if (status.uid == 0) "Connected with root." else "Connected."
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ErrorText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
}

private fun startPairing(context: Context) {
    PairingService.start(context)
    openWirelessDebugging(context)
}

/** Opens Developer options, scrolled to Wireless debugging where the Settings app supports it. */
private fun openWirelessDebugging(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
        .putExtra(":settings:fragment_args_key", "toggle_adb_wireless")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
