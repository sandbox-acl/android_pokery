package com.pokery.app.adb

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

class ShellProtocolTest {

    private val marker = "__POKERY_EXIT_test__"

    @Test
    fun quotesSingleQuotes() {
        assertEquals("'it'\\''s'", ShellProtocol.quote("it's"))
        assertEquals("'a b; c'", ShellProtocol.quote("a b; c"))
    }

    @Test
    fun parsesExitCodeAndOutput() {
        val parsed = ShellProtocol.parse("hello\nworld\n${marker}3\n", marker)
        assertEquals(3, parsed.exitCode)
        assertEquals("hello\nworld\n", parsed.output)
    }

    @Test
    fun missingMarkerMeansUnknownExit() {
        val parsed = ShellProtocol.parse("partial output", marker)
        assertEquals(-1, parsed.exitCode)
        assertEquals("partial output", parsed.output)
    }

    /** Runs the wrapped command through a real POSIX shell, the way adbd does. */
    @Test
    fun wrappedCommandsBehaveLikeAdbShell() {
        fun run(command: String): ShellProtocol.Parsed {
            val p = ProcessBuilder("sh", "-c", ShellProtocol.wrap(command, marker)).redirectErrorStream(true).start()
            p.waitFor(10, TimeUnit.SECONDS)
            return ShellProtocol.parse(p.inputStream.bufferedReader().readText(), marker)
        }
        if (!File("/bin/sh").exists()) return

        assertEquals(ShellProtocol.Parsed(0, "hi\n"), run("echo hi"))
        assertEquals(ShellProtocol.Parsed(7, ""), run("exit 7"))
        assertEquals(ShellProtocol.Parsed(0, "it's \"quoted\"\n"), run("echo \"it's \\\"quoted\\\"\""))
        assertEquals(ShellProtocol.Parsed(1, "err\n"), run("echo err >&2; false"))
        // A syntax error must still report a non-zero exit, not swallow the marker.
        val broken = run("if then")
        assertEquals(true, broken.exitCode != 0)
    }
}
