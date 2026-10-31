package com.pokery.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.pokery.app.shell.PrivilegedShell
import com.pokery.app.ui.screens.AppsScreen
import com.pokery.app.ui.screens.ShellScreen
import com.pokery.app.ui.screens.StatusScreen
import com.pokery.app.ui.screens.TweaksScreen
import com.pokery.app.ui.theme.PokeryTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { PokeryTheme { PokeryRoot() } }
    }

    override fun onResume() {
        super.onResume()
        // Shizuku may have been started or stopped while we were in the background.
        PrivilegedShell.refresh()
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    Status("Status", Icons.Outlined.VerifiedUser),
    Apps("Debloat", Icons.Outlined.Apps),
    Tweaks("Tweaks", Icons.Outlined.Tune),
    Shell("Shell", Icons.Outlined.Terminal),
}

@Composable
private fun PokeryRoot() {
    var tab by rememberSaveable { mutableStateOf(Tab.Status) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (tab) {
            Tab.Status -> StatusScreen(modifier)
            Tab.Apps -> AppsScreen(modifier)
            Tab.Tweaks -> TweaksScreen(modifier)
            Tab.Shell -> ShellScreen(modifier)
        }
    }
}
