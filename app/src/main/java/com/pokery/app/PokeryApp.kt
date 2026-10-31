package com.pokery.app

import android.app.Application
import com.pokery.app.crash.CrashReporter
import com.pokery.app.shell.PrivilegedShell

class PokeryApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
        // Never let connection setup take the whole app down; record the error instead.
        try {
            PrivilegedShell.init(this)
        } catch (t: Throwable) {
            CrashReporter.record(this, "PrivilegedShell.init failed (app kept running)", t)
        }
    }
}
