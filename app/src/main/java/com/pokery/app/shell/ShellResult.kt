package com.pokery.app.shell

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class ShellResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
) : Parcelable {
    val isSuccess: Boolean get() = exitCode == 0

    /** Combined human-readable output, the way a terminal would show it. */
    val output: String
        get() = listOf(stdout.trimEnd(), stderr.trimEnd()).filter { it.isNotEmpty() }.joinToString("\n")

    companion object {
        fun failure(message: String) = ShellResult(-1, "", message)
    }
}
