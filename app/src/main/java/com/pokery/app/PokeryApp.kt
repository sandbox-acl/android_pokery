package com.pokery.app

import android.app.Application
import com.pokery.app.shell.PrivilegedShell

class PokeryApp : Application() {
    override fun onCreate() {
        super.onCreate()
        PrivilegedShell.init(this)
    }
}
