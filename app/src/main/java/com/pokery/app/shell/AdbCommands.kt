package com.pokery.app.shell

/**
 * Builders for the shell commands the app runs. Each string is exactly what you
 * would type after `adb shell` on a computer.
 */
object AdbCommands {

    private val PACKAGE_NAME = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")
    private val SETTING_KEY = Regex("^[A-Za-z0-9_.]+$")
    private val SETTING_VALUE = Regex("^[A-Za-z0-9_.:-]*$")

    enum class Namespace(val value: String) { SYSTEM("system"), SECURE("secure"), GLOBAL("global") }

    fun isValidPackageName(name: String): Boolean = PACKAGE_NAME.matches(name)

    /** Removes an app for the current user. Its APK stays on the system partition, so it can be restored. */
    fun uninstallForUser(pkg: String, user: Int = 0) = "pm uninstall -k --user $user ${checked(pkg)}"

    /** Brings back an app previously removed with [uninstallForUser]. */
    fun restore(pkg: String, user: Int = 0) = "cmd package install-existing --user $user ${checked(pkg)}"

    fun disable(pkg: String, user: Int = 0) = "pm disable-user --user $user ${checked(pkg)}"

    fun enable(pkg: String, user: Int = 0) = "pm enable --user $user ${checked(pkg)}"

    fun forceStop(pkg: String) = "am force-stop ${checked(pkg)}"

    fun clearData(pkg: String, user: Int = 0) = "pm clear --user $user ${checked(pkg)}"

    fun putSetting(namespace: Namespace, key: String, value: String): String {
        require(SETTING_KEY.matches(key)) { "Invalid setting key: $key" }
        require(SETTING_VALUE.matches(value)) { "Invalid setting value: $value" }
        return "settings put ${namespace.value} $key $value"
    }

    fun getSetting(namespace: Namespace, key: String): String {
        require(SETTING_KEY.matches(key)) { "Invalid setting key: $key" }
        return "settings get ${namespace.value} $key"
    }

    /**
     * Lets people paste commands written for a computer: strips a leading
     * `adb shell` (or `adb -d shell`, etc.) so the rest runs directly.
     */
    fun normalizeUserCommand(input: String): String {
        val trimmed = input.trim()
        val match = Regex("^adb(\\s+-[a-zA-Z]+(\\s+\\S+)?)*\\s+shell\\s+").find(trimmed)
        return if (match != null) trimmed.substring(match.range.last + 1).trim() else trimmed
    }

    private fun checked(pkg: String): String {
        require(isValidPackageName(pkg)) { "Invalid package name: $pkg" }
        return pkg
    }
}
