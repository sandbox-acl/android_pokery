package com.pokery.app.shell

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import com.pokery.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku

/** Connection state of the Shizuku-backed shell, as shown on the Status screen. */
sealed interface ShellStatus {
    /** Shizuku app is not installed. */
    data object NotInstalled : ShellStatus
    /** Shizuku is installed but its server is not running. */
    data object NotRunning : ShellStatus
    /** Shizuku server is too old (pre v11) to support user services. */
    data object Unsupported : ShellStatus
    data object PermissionRequired : ShellStatus
    data object PermissionDenied : ShellStatus
    data object Connecting : ShellStatus
    /** [uid] is 2000 for adb/shell or 0 for root. */
    data class Ready(val uid: Int) : ShellStatus
}

object PrivilegedShell : ShellExecutor {

    const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    private const val PERMISSION_REQUEST_CODE = 4242

    private val _status = MutableStateFlow<ShellStatus>(ShellStatus.NotRunning)
    val status: StateFlow<ShellStatus> = _status.asStateFlow()

    @Volatile private var service: IShellService? = null
    @Volatile private var bound = false
    private lateinit var appContext: Context

    private val serviceArgs by lazy {
        Shizuku.UserServiceArgs(ComponentName(appContext.packageName, ShellService::class.java.name))
            .daemon(false)
            .processNameSuffix("shell")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            if (binder != null && binder.pingBinder()) {
                service = IShellService.Stub.asInterface(binder)
                _status.value = ShellStatus.Ready(runCatching { Shizuku.getUid() }.getOrDefault(2000))
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            bound = false
            refresh()
        }
    }

    fun init(context: Context) {
        appContext = context.applicationContext
        Shizuku.addBinderReceivedListenerSticky { refresh() }
        Shizuku.addBinderDeadListener {
            service = null
            bound = false
            refresh()
        }
        Shizuku.addRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode != PERMISSION_REQUEST_CODE) return@addRequestPermissionResultListener
            if (grantResult == PackageManager.PERMISSION_GRANTED) refresh()
            else _status.value = ShellStatus.PermissionDenied
        }
        refresh()
    }

    /** Re-evaluates Shizuku's state and binds the shell service when possible. */
    fun refresh() {
        _status.value = when {
            !isShizukuInstalled() -> ShellStatus.NotInstalled
            !Shizuku.pingBinder() -> ShellStatus.NotRunning
            Shizuku.isPreV11() -> ShellStatus.Unsupported
            Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED ->
                if (Shizuku.shouldShowRequestPermissionRationale()) ShellStatus.PermissionDenied
                else ShellStatus.PermissionRequired
            service != null -> ShellStatus.Ready(runCatching { Shizuku.getUid() }.getOrDefault(2000))
            else -> {
                bind()
                ShellStatus.Connecting
            }
        }
    }

    fun requestPermission() {
        if (Shizuku.pingBinder()) Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
    }

    private fun bind() {
        if (bound) return
        bound = true
        runCatching { Shizuku.bindUserService(serviceArgs, connection) }
            .onFailure { bound = false }
    }

    private fun isShizukuInstalled(): Boolean = try {
        appContext.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        // Shizuku may also be bundled by other managers (e.g. Sui); a live binder still counts.
        Shizuku.pingBinder()
    }

    override suspend fun exec(command: String): ShellResult = withContext(Dispatchers.IO) {
        val svc = service ?: run {
            refresh()
            withTimeoutOrNull(5_000) { status.first { it is ShellStatus.Ready } }
            service
        } ?: return@withContext ShellResult.failure(
            "Not connected to Shizuku. Open the Status tab to set it up."
        )
        try {
            svc.exec(command)
        } catch (t: Throwable) {
            ShellResult.failure("Shell service error: $t")
        }
    }
}
