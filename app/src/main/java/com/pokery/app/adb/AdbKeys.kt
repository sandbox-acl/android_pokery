package com.pokery.app.adb

import android.content.Context
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * The RSA key that identifies Pokery to the phone's ADB daemon, like the
 * `~/.android/adbkey` a computer uses. Pairing authorises this key; it is kept in
 * app-private storage and never leaves the device.
 */
class AdbKeys private constructor(val privateKey: PrivateKey, val certificate: Certificate) {

    companion object {
        private const val DIR = "adb"
        private const val KEY_FILE = "adbkey.pk8"
        private const val CERT_FILE = "adbkey.crt"

        @Synchronized
        fun loadOrCreate(context: Context): AdbKeys {
            val dir = File(context.filesDir, DIR).apply { mkdirs() }
            val keyFile = File(dir, KEY_FILE)
            val certFile = File(dir, CERT_FILE)
            if (keyFile.exists() && certFile.exists()) {
                runCatching { return load(keyFile, certFile) }
            }
            return create().also { keys ->
                keyFile.writeBytes(keys.privateKey.encoded)
                certFile.writeBytes(keys.certificate.encoded)
            }
        }

        /** Deletes the key so the next pairing starts fresh. */
        @Synchronized
        fun delete(context: Context) {
            File(context.filesDir, DIR).deleteRecursively()
        }

        private fun load(keyFile: File, certFile: File): AdbKeys {
            val key = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(keyFile.readBytes()))
            val cert = certFile.inputStream().use { CertificateFactory.getInstance("X.509").generateCertificate(it) }
            return AdbKeys(key, cert)
        }

        private fun create(): AdbKeys {
            val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
            val subject = X500Name("CN=Pokery")
            val now = System.currentTimeMillis()
            val holder = JcaX509v3CertificateBuilder(
                subject,
                BigInteger.valueOf(now),
                Date(now - TimeUnit.DAYS.toMillis(1)),
                Date(now + TimeUnit.DAYS.toMillis(365L * 30)),
                subject,
                pair.public,
            ).build(JcaContentSignerBuilder("SHA256withRSA").build(pair.private))
            return AdbKeys(pair.private, JcaX509CertificateConverter().getCertificate(holder))
        }
    }
}
