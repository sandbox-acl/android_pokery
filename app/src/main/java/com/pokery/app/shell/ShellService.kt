package com.pokery.app.shell

import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.system.exitProcess

/**
 * Runs inside a process started by Shizuku, so it has the `shell` user's privileges
 * (or root's, if Shizuku itself was started with root). Every command here behaves
 * exactly like `adb shell <command>` from a computer.
 */
class ShellService : IShellService.Stub() {

    override fun destroy() {
        exitProcess(0)
    }

    override fun exec(command: String): ShellResult {
        return try {
            val process = ProcessBuilder("sh", "-c", command).start()
            process.outputStream.close()

            val stdout = StringBuilder()
            val stderr = StringBuilder()
            val outReader = thread { readCapped(process.inputStream, stdout) }
            val errReader = thread { readCapped(process.errorStream, stderr) }

            val finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!finished) process.destroyForcibly()
            outReader.join(1_000)
            errReader.join(1_000)

            if (finished) {
                ShellResult(process.exitValue(), stdout.toString(), stderr.toString())
            } else {
                ShellResult(
                    124,
                    stdout.toString(),
                    stderr.append("\n[stopped after ${TIMEOUT_SECONDS}s]").toString(),
                )
            }
        } catch (t: Throwable) {
            ShellResult.failure(t.toString())
        }
    }

    private fun readCapped(stream: java.io.InputStream, sink: StringBuilder) {
        stream.bufferedReader().use { reader ->
            val buffer = CharArray(8_192)
            while (true) {
                val n = reader.read(buffer)
                if (n < 0) break
                synchronized(sink) {
                    // Binder transactions are limited to ~1 MB, so keep results well under that.
                    val room = MAX_CHARS - sink.length
                    if (room > 0) sink.append(buffer, 0, minOf(n, room))
                }
            }
        }
        synchronized(sink) {
            if (sink.length >= MAX_CHARS) sink.append("\n[output truncated]")
        }
    }

    private companion object {
        const val TIMEOUT_SECONDS = 30L
        const val MAX_CHARS = 200_000
    }
}
