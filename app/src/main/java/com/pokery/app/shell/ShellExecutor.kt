package com.pokery.app.shell

/**
 * Something that can run commands with ADB-shell privileges. Shizuku is the only
 * backend today; a built-in wireless-debugging client could implement this later.
 */
interface ShellExecutor {
    suspend fun exec(command: String): ShellResult
}
