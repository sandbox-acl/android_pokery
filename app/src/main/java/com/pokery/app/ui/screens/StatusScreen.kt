package com.pokery.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pokery.app.shell.PrivilegedShell
import com.pokery.app.shell.ShellStatus

private const val SHIZUKU_DOWNLOAD = "https://shizuku.rikka.app/download/"

@Composable
fun StatusScreen(modifier: Modifier = Modifier) {
    val status by PrivilegedShell.status.collectAsState()
    val context = LocalContext.current

    fun openShizuku() {
        val launch = context.packageManager.getLaunchIntentForPackage(PrivilegedShell.SHIZUKU_PACKAGE)
        context.startActivity(launch ?: Intent(Intent.ACTION_VIEW, Uri.parse(SHIZUKU_DOWNLOAD)))
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenTitle("Pokery")

        val ready = status is ShellStatus.Ready
        Card(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (ready) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.errorContainer,
            ),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(headline(status), style = MaterialTheme.typography.titleMedium)
                Text(detail(status), style = MaterialTheme.typography.bodyMedium)
                when (status) {
                    ShellStatus.NotInstalled -> Button(onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SHIZUKU_DOWNLOAD)))
                    }) { Text("Get Shizuku") }
                    ShellStatus.NotRunning, ShellStatus.Unsupported ->
                        Button(onClick = ::openShizuku) { Text("Open Shizuku") }
                    ShellStatus.PermissionRequired ->
                        Button(onClick = PrivilegedShell::requestPermission) { Text("Grant access") }
                    ShellStatus.PermissionDenied -> {
                        Button(onClick = PrivilegedShell::requestPermission) { Text("Ask again") }
                        OutlinedButton(onClick = ::openShizuku) { Text("Manage in Shizuku") }
                    }
                    else -> Unit
                }
                if (!ready) OutlinedButton(onClick = PrivilegedShell::refresh) { Text("Check again") }
            }
        }

        Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("How this works without root", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Android only gives ADB-level powers to the \"shell\" user that `adb shell` runs as. " +
                        "Shizuku starts a small helper as that user, and Pokery runs its commands through it. " +
                        "Everything here is what you could do yourself from a computer with adb.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text("Setup (once per reboot)", style = MaterialTheme.typography.titleSmall)
                Text(
                    "1. Install Shizuku.\n" +
                        "2. Android 11+: turn on Developer options → Wireless debugging, then in Shizuku tap " +
                        "\"Pair\" and \"Start\". No computer needed.\n" +
                        "   Older Android, or if you prefer USB: connect to a computer and run the " +
                        "\"Start via ADB\" command Shizuku shows you.\n" +
                        "3. Come back here and tap \"Grant access\".\n\n" +
                        "Shizuku stops when the phone restarts, so repeat step 2 after a reboot. " +
                        "Your changes (removed apps, animation speeds) stay in place.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

private fun headline(status: ShellStatus) = when (status) {
    ShellStatus.NotInstalled -> "Shizuku is not installed"
    ShellStatus.NotRunning -> "Shizuku is not running"
    ShellStatus.Unsupported -> "Shizuku is too old"
    ShellStatus.PermissionRequired -> "Pokery needs your permission"
    ShellStatus.PermissionDenied -> "Access was denied"
    ShellStatus.Connecting -> "Connecting…"
    is ShellStatus.Ready -> "Ready"
}

private fun detail(status: ShellStatus) = when (status) {
    ShellStatus.NotInstalled -> "Pokery uses the free Shizuku app to get ADB-level access without root."
    ShellStatus.NotRunning -> "Open Shizuku and start it with Wireless debugging or from a computer."
    ShellStatus.Unsupported -> "Update Shizuku to version 11 or newer."
    ShellStatus.PermissionRequired -> "Allow Pokery to use Shizuku."
    ShellStatus.PermissionDenied -> "Allow Pokery in Shizuku's app list, or ask again."
    ShellStatus.Connecting -> "Starting the shell helper."
    is ShellStatus.Ready ->
        if (status.uid == 0) "Running as root through Shizuku."
        else "Running with ADB shell privileges (uid ${status.uid})."
}
