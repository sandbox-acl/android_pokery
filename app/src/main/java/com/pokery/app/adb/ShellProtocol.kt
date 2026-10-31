package com.pokery.app.adb

/**
 * The plain `shell:` ADB service doesn't report exit codes, so each command is wrapped
 * to print a unique marker followed by `$?` when it finishes.
 */
object ShellProtocol {

    data class Parsed(val exitCode: Int, val output: String)

    /** Single-quotes [s] for POSIX sh, so it reaches `sh -c` as one literal argument. */
    fun quote(s: String): String = "'" + s.replace("'", "'\\''") + "'"

    fun wrap(command: String, marker: String): String =
        "sh -c ${quote(command)} 2>&1; echo \"$marker\$?\""

    /**
     * Splits raw stream output into the command's output and its exit code. Without a
     * marker (killed, timed out, connection lost), the exit code is -1.
     */
    fun parse(raw: String, marker: String): Parsed {
        val at = raw.lastIndexOf(marker)
        if (at < 0) return Parsed(-1, raw)
        val code = raw.substring(at + marker.length).trim().takeWhile { it.isDigit() }.toIntOrNull() ?: -1
        return Parsed(code, raw.substring(0, at))
    }
}
