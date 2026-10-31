package com.pokery.app.shell

import android.content.Context
import com.pokery.app.adb.AdbConnection
import com.pokery.app.adb.AdbShell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

enum class Backend(val label: String) { WirelessDebugging("Wireless debugging"), Shizuku("Shizuku") }

/**
 * The one place screens send commands to. Uses the built-in Wireless debugging
 * connection, or Shizuku if someone has that running instead.
 */
object PrivilegedShell : ShellExecutor {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** The backend commands will go to right now, or null if none is connected. */
    val active: StateFlow<Backend?> = combine(AdbShell.state, ShizukuShell.status) { adb, shizuku ->
        when {
            adb.connection == AdbConnection.Connected -> Backend.WirelessDebugging
            shizuku is ShizukuStatus.Ready -> Backend.Shizuku
            else -> null
        }
    }.stateIn(scope, SharingStarted.Eagerly, null)

    fun init(context: Context) {
        AdbShell.init(context)
        ShizukuShell.init(context)
    }

    fun refresh() {
        AdbShell.refresh()
        ShizukuShell.refresh()
    }

    override suspend fun exec(command: String): ShellResult = when {
        AdbShell.isConnected -> AdbShell.exec(command)
        ShizukuShell.isReady -> ShizukuShell.exec(command)
        // Paired before: AdbShell reconnects on demand.
        AdbShell.state.value.paired -> AdbShell.exec(command)
        else -> ShellResult.failure("Not connected. Open the Status tab to set up Wireless debugging.")
    }
}
