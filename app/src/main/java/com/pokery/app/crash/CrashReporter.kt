package com.pokery.app.crash

import android.content.Context
import android.os.Build
import com.pokery.app.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Saves the stack trace of any crash to app storage so the next launch can show it.
 * This makes failures diagnosable on a phone with no computer or logcat at hand.
 */
object CrashReporter {

    private const val FILE_NAME = "last_crash.txt"

    fun install(context: Context) {
        val appContext = context.applicationContext ?: context
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { write(appContext, "Uncaught exception on thread \"${thread.name}\"", throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    /** Records an error that was caught, so it's visible even though the app kept running. */
    fun record(context: Context, where: String, throwable: Throwable) {
        runCatching { write(context, where, throwable) }
    }

    fun pending(context: Context): String? =
        file(context).takeIf { it.exists() }?.readText()?.takeIf { it.isNotBlank() }

    fun clear(context: Context) {
        file(context).delete()
    }

    private fun write(context: Context, where: String, throwable: Throwable) {
        val trace = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date())
        val report = buildString {
            appendLine("Pokery ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}, ${BuildConfig.BUILD_TYPE})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
            appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), build ${Build.DISPLAY}")
            appendLine("Time: $time")
            appendLine(where)
            appendLine()
            append(trace)
        }
        file(context).writeText(report)
    }

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)
}
