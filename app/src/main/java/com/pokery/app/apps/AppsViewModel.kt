package com.pokery.app.apps

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pokery.app.shell.AdbCommands
import com.pokery.app.shell.PrivilegedShell
import com.pokery.app.shell.ShellExecutor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AppFilter(val label: String) { System("System"), User("Installed"), Disabled("Disabled"), Removed("Removed") }

enum class AppAction(val label: String, val pastTense: String) {
    Remove("Uninstall for this user", "Removed"),
    Disable("Disable", "Disabled"),
    Enable("Enable", "Enabled"),
    Restore("Restore", "Restored"),
    ForceStop("Force stop", "Stopped"),
}

data class AppsUiState(
    val loading: Boolean = true,
    val apps: List<AppEntry> = emptyList(),
    val filter: AppFilter = AppFilter.System,
    val query: String = "",
    val busy: Boolean = false,
    val message: String? = null,
) {
    val visible: List<AppEntry>
        get() = apps.filter { app ->
            when (filter) {
                AppFilter.System -> app.isSystem && app.state != AppState.Removed
                AppFilter.User -> !app.isSystem && app.state != AppState.Removed
                AppFilter.Disabled -> app.state == AppState.Disabled
                AppFilter.Removed -> app.state == AppState.Removed
            }
        }.filter { app ->
            query.isBlank() || app.label.contains(query, ignoreCase = true) ||
                app.packageName.contains(query, ignoreCase = true)
        }
}

class AppsViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val shell: ShellExecutor = PrivilegedShell
    private val _state = MutableStateFlow(AppsUiState())
    val state: StateFlow<AppsUiState> = _state.asStateFlow()

    init { reload() }

    fun reload() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val apps = AppRepository.load(getApplication())
            _state.update { it.copy(loading = false, apps = apps) }
        }
    }

    fun setFilter(filter: AppFilter) = _state.update { it.copy(filter = filter) }
    fun setQuery(query: String) = _state.update { it.copy(query = query) }
    fun consumeMessage() = _state.update { it.copy(message = null) }

    fun actionsFor(app: AppEntry): List<AppAction> = when (app.state) {
        AppState.Enabled -> listOf(AppAction.Remove, AppAction.Disable, AppAction.ForceStop)
        AppState.Disabled -> listOf(AppAction.Enable, AppAction.Remove)
        AppState.Removed -> listOf(AppAction.Restore)
    }

    fun perform(app: AppEntry, action: AppAction) {
        val command = when (action) {
            AppAction.Remove -> AdbCommands.uninstallForUser(app.packageName)
            AppAction.Disable -> AdbCommands.disable(app.packageName)
            AppAction.Enable -> AdbCommands.enable(app.packageName)
            AppAction.Restore -> AdbCommands.restore(app.packageName)
            AppAction.ForceStop -> AdbCommands.forceStop(app.packageName)
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            val result = shell.exec(command)
            val message = if (result.isSuccess) {
                "${action.pastTense} ${app.label}"
            } else {
                "Failed: ${result.output.ifBlank { "exit code ${result.exitCode}" }}"
            }
            _state.update { it.copy(busy = false, message = message) }
            if (result.isSuccess && action != AppAction.ForceStop) reload()
        }
    }
}
