package com.pokery.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pokery.app.shell.PrivilegedShell

@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.headlineSmall,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

/** Shown on feature screens while the privileged shell isn't available. */
@Composable
fun NotConnectedBanner(modifier: Modifier = Modifier) {
    val active by PrivilegedShell.active.collectAsState()
    if (active != null) return
    Card(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column {
                Text("Not connected", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Actions here need ADB-level access. Set it up in the Status tab.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
