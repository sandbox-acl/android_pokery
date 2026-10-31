package com.pokery.app.apps

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class AppState { Enabled, Disabled, Removed }

data class AppEntry(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
    val state: AppState,
)

object AppRepository {

    /**
     * Packages that will brick or soft-brick the phone if removed. Pokery still lets you
     * act on them, but asks twice.
     */
    private val critical = setOf(
        "android",
        "com.android.systemui",
        "com.android.settings",
        "com.android.phone",
        "com.android.providers.settings",
        "com.android.providers.telephony",
        "com.android.providers.media",
        "com.android.providers.contacts",
        "com.android.providers.downloads",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.android.shell",
        "com.android.inputmethod.latin",
        "com.google.android.inputmethod.latin",
        "com.android.launcher3",
        "com.google.android.apps.nexuslauncher",
        "com.android.networkstack",
        "com.google.android.networkstack",
        "com.android.bluetooth",
        "com.android.nfc",
        "com.android.server.telecom",
        "com.android.webview",
        "com.google.android.webview",
        "com.google.android.gms",
        "com.google.android.gsf",
        "moe.shizuku.privileged.api",
    )

    fun isCritical(pkg: String): Boolean =
        pkg in critical || pkg.startsWith("com.android.providers.") || pkg.startsWith("com.android.networkstack")

    suspend fun load(context: Context): List<AppEntry> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val flags = PackageManager.MATCH_UNINSTALLED_PACKAGES or
            PackageManager.MATCH_DISABLED_COMPONENTS or
            PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS
        val apps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledApplications(flags)
        }
        apps.asSequence()
            .filter { it.packageName != context.packageName }
            .map { info ->
                AppEntry(
                    packageName = info.packageName,
                    label = runCatching { info.loadLabel(pm).toString() }.getOrDefault(info.packageName),
                    isSystem = info.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0,
                    state = stateOf(pm, info),
                )
            }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
            .toList()
    }

    private fun stateOf(pm: PackageManager, info: ApplicationInfo): AppState {
        if (info.flags and ApplicationInfo.FLAG_INSTALLED == 0) return AppState.Removed
        val setting = runCatching { pm.getApplicationEnabledSetting(info.packageName) }
            .getOrDefault(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
        val disabled = when (setting) {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED -> true
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> false
            else -> !info.enabled
        }
        return if (disabled) AppState.Disabled else AppState.Enabled
    }
}
