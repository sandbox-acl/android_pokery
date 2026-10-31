package com.pokery.app.adb

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import androidx.core.app.ServiceCompat
import com.pokery.app.MainActivity
import com.pokery.app.R
import io.github.muntashirakon.adb.android.AdbMdns
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Runs while the person is in Settings → Wireless debugging → "Pair device with
 * pairing code". It finds the pairing port over mDNS and asks for the code in a
 * notification reply, so the Settings dialog (which closes if you leave it) stays open.
 */
class PairingService : Service() {

    companion object {
        private const val CHANNEL_ID = "pairing"
        private const val NOTIFICATION_ID = 1
        private const val RESULT_NOTIFICATION_ID = 2
        private const val ACTION_START = "com.pokery.app.pairing.START"
        private const val ACTION_CODE = "com.pokery.app.pairing.CODE"
        private const val ACTION_STOP = "com.pokery.app.pairing.STOP"
        private const val KEY_CODE = "code"
        private const val SEARCH_TIMEOUT_MS = 5 * 60_000L

        fun start(context: Context) {
            val intent = Intent(context, PairingService::class.java).setAction(ACTION_START)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, PairingService::class.java).setAction(ACTION_STOP))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var mdns: AdbMdns? = null
    private var timeout: Job? = null
    @Volatile private var pairingPort = -1

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> finish(PairingStatus.Idle)
            ACTION_CODE -> {
                val code = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_CODE)?.toString()
                if (code.isNullOrBlank()) showWaitingForCode("Enter the 6-digit code.") else pair(code)
            }
            else -> begin()
        }
        return START_NOT_STICKY
    }

    private fun begin() {
        createChannel()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            searchingNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0,
        )
        AdbShell.setPairing(PairingStatus.Searching)
        if (mdns == null) {
            mdns = AdbMdns(this, AdbMdns.SERVICE_TYPE_TLS_PAIRING) { _, port ->
                scope.launch { onPairingPort(port) }
            }.also { it.start() }
        }
        timeout?.cancel()
        timeout = scope.launch {
            delay(SEARCH_TIMEOUT_MS)
            finish(PairingStatus.Failed("Timed out waiting for the pairing code screen."), notify = true)
        }
    }

    private fun onPairingPort(port: Int) {
        val pairing = AdbShell.pairing.value
        if (pairing is PairingStatus.Pairing || pairing is PairingStatus.Done) return
        pairingPort = port
        if (port > 0) {
            showWaitingForCode(null)
        } else {
            // The "Pair device" dialog was closed.
            AdbShell.setPairing(PairingStatus.Searching)
            notify(searchingNotification())
        }
    }

    private fun pair(code: String) {
        val port = pairingPort
        if (port <= 0) {
            notify(searchingNotification("The pairing dialog closed. Open it again to get a new code."))
            return
        }
        AdbShell.setPairing(PairingStatus.Pairing)
        notify(progressNotification("Pairing…"))
        scope.launch {
            val paired = AdbShell.pair(port, code)
            if (paired.isFailure) {
                val message = paired.exceptionOrNull()?.message ?: "Pairing failed."
                AdbShell.setPairing(PairingStatus.Failed(message))
                showWaitingForCode("$message Try again.")
                return@launch
            }
            notify(progressNotification("Paired. Connecting…"))
            val connected = AdbShell.connect()
            if (connected.isSuccess) {
                finish(PairingStatus.Done, notify = true)
            } else {
                finish(
                    PairingStatus.Failed("Paired, but couldn't connect: ${connected.exceptionOrNull()?.message}"),
                    notify = true,
                )
            }
        }
    }

    private fun showWaitingForCode(error: String?) {
        AdbShell.setPairing(PairingStatus.WaitingForCode(pairingPort))
        val reply = PendingIntent.getService(
            this, 1,
            Intent(this, PairingService::class.java).setAction(ACTION_CODE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        val input = RemoteInput.Builder(KEY_CODE).setLabel("Pairing code").build()
        val action = NotificationCompat.Action.Builder(R.drawable.ic_stat_pokery, "Enter code", reply)
            .addRemoteInput(input)
            .setAllowGeneratedReplies(false)
            .build()
        notify(
            base()
                .setContentTitle("Pairing code found")
                .setContentText(error ?: "Tap \"Enter code\" and type the 6-digit code shown in Settings.")
                .setStyle(NotificationCompat.BigTextStyle().bigText(
                    error ?: "Tap \"Enter code\" and type the 6-digit code shown in Settings."
                ))
                .addAction(action)
                .addAction(stopAction())
                .build()
        )
    }

    private fun finish(status: PairingStatus, notify: Boolean = false) {
        AdbShell.setPairing(status)
        timeout?.cancel()
        mdns?.stop()
        mdns = null
        if (notify) {
            val (title, text) = when (status) {
                PairingStatus.Done -> "Pokery is connected" to "You can close Settings and go back to Pokery."
                is PairingStatus.Failed -> "Pairing didn't finish" to status.message
                else -> "Pairing stopped" to ""
            }
            getSystemService(NotificationManager::class.java).notify(
                RESULT_NOTIFICATION_ID,
                base().setContentTitle(title).setContentText(text).setOngoing(false).setAutoCancel(true)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(text)).build(),
            )
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        mdns?.stop()
        scope.cancel()
        super.onDestroy()
    }

    // --- notifications ---------------------------------------------------------------

    private fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Wireless debugging pairing", NotificationManager.IMPORTANCE_HIGH)
            .apply { description = "Asks for the pairing code while Settings is open." }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun base(): NotificationCompat.Builder {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_pokery)
            .setContentIntent(open)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
    }

    private fun stopAction(): NotificationCompat.Action {
        val stop = PendingIntent.getService(
            this, 2,
            Intent(this, PairingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Action.Builder(0, "Cancel", stop).build()
    }

    private fun searchingNotification(text: String? = null) = base()
        .setContentTitle("Waiting for the pairing code")
        .setContentText(text ?: "In Wireless debugging, tap \"Pair device with pairing code\".")
        .setStyle(NotificationCompat.BigTextStyle().bigText(
            text ?: "In Wireless debugging, tap \"Pair device with pairing code\". This notification will ask for the code."
        ))
        .setProgress(0, 0, true)
        .addAction(stopAction())
        .build()

    private fun progressNotification(text: String) = base()
        .setContentTitle(text)
        .setProgress(0, 0, true)
        .build()

    private fun notify(notification: android.app.Notification) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }
}
