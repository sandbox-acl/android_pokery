package com.pokery.app.adb

import android.os.Build
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import java.security.PrivateKey
import java.security.cert.Certificate
import java.util.concurrent.TimeUnit

internal class PokeryAdbManager(private val keys: AdbKeys) : AbsAdbConnectionManager() {
    init {
        api = Build.VERSION.SDK_INT
        setTimeout(15, TimeUnit.SECONDS)
    }

    override fun getPrivateKey(): PrivateKey = keys.privateKey
    override fun getCertificate(): Certificate = keys.certificate

    /** Shown in Settings → Wireless debugging → Paired devices. */
    override fun getDeviceName(): String = "Pokery"
}
