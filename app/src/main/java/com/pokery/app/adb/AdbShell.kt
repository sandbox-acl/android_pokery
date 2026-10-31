package com.pokery.app.adb

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.provider.Settings
import com.pokery.app.shell.ShellExecutor
import com.pokery.app.shell.ShellResult
import io.github.muntashirakon.adb.AdbPairingRequiredException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

enum class AdbConnection { Disconnected, Connecting, Connected }

data class AdbState(
    /** Pokery's key has been paired with this phone at least once. */
    val paired: Boolean = false,
    val connection: AdbConnection = AdbConnection.Disconnected,
    /** Last problem worth showing, cleared on success. */
    val message: String? = null,
)

/** Progress of the notification-driven pairing flow run by [PairingService]. */
sealed interface PairingStatus {
    data object Idle : PairingStatus
    data object Searching : PairingStatus
    data class WaitingForCode(val port: Int) : PairingStatus
    data object Pairing : PairingStatus
    data class Failed(val message: String) : PairingStatus
    data object Done : PairingStatus
}

/**
 * Built-in ADB client. Pairs with the phone's own Wireless debugging (Android 11+) and
 * runs commands over it, with the same privileges `adb shell` has from a computer.
 */
object AdbShell : ShellExecutor {

    private const val PREFS = "adb"
    private const val KEY_PAIRED = "paired"
    private const val CONNECT_TIMEOUT_MS = 10_000L
    private const val EXEC_TIMEOUT_S = 30L
    private const val MAX_OUTPUT_BYTES = 1_000_000

    /** Wireless debugging pairing needs Android 11; older versions need `adb tcpip` from a computer. */
    val supportsWirelessPairing: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val connectLock = Mutex()
    private val readers = Executors.newCachedThreadPool()

    private val _state = MutableStateFlow(AdbState())
    val state: StateFlow<AdbState> = _state.asStateFlow()

    private val _pairing = MutableStateFlow<PairingStatus>(PairingStatus.Idle)
    val pairing: StateFlow<PairingStatus> = _pairing.asStateFlow()

    private lateinit var appContext: Context
    @Volatile private var manager: PokeryAdbManager? = null

    val isConnected: Boolean get() = manager?.isConnected == true

    fun init(context: Context) {
        appContext = context.applicationContext
        val paired = prefs().getBoolean(KEY_PAIRED, false)
        _state.value = AdbState(paired = paired)
        if (paired) scope.launch { connect(quiet = true) }
    }

    internal fun setPairing(status: PairingStatus) {
        _pairing.value = status
    }

    /** Pairs with the Wireless debugging pairing service on [port] using the 6-digit [code]. */
    suspend fun pair(port: Int, code: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val ok = manager().pair("127.0.0.1", port, code.trim())
            check(ok) { "Pairing was rejected. Check the code and try again." }
            prefs().edit().putBoolean(KEY_PAIRED, true).apply()
            _state.update { it.copy(paired = true, message = null) }
        }.recoverCatching { throw IllegalStateException(friendly(it), it) }
    }

    /**
     * Connects to Wireless debugging. Finds the port automatically via mDNS unless [port]
     * is given (for example 5555 after `adb tcpip 5555` on Android 10 and older).
     */
    suspend fun connect(port: Int? = null, quiet: Boolean = false): Result<Unit> = withContext(Dispatchers.IO) {
        connectLock.withLock {
            if (isConnected) return@withLock Result.success(Unit)
            _state.update { it.copy(connection = AdbConnection.Connecting, message = null) }
            if (port == null) enableWirelessDebuggingIfPossible()
            val result = runCatching {
                val mgr = manager()
                val ok = if (port != null) mgr.connect("127.0.0.1", port)
                else mgr.autoConnect(appContext, CONNECT_TIMEOUT_MS)
                check(ok) { "Couldn't connect. Is Wireless debugging on?" }
                if (port != null) {
                    // A manual connection to a port means the key is authorised there.
                    prefs().edit().putBoolean(KEY_PAIRED, true).apply()
                }
            }
            result.onSuccess {
                _state.update { it.copy(paired = true, connection = AdbConnection.Connected, message = null) }
                scope.launch { grantSelfWriteSecureSettings() }
            }.onFailure { error ->
                val unpaired = error is AdbPairingRequiredException
                if (unpaired) prefs().edit().putBoolean(KEY_PAIRED, false).apply()
                _state.update {
                    it.copy(
                        paired = it.paired && !unpaired,
                        connection = AdbConnection.Disconnected,
                        message = if (quiet && !unpaired) null else friendly(error),
                    )
                }
            }
            result.map { }
        }
    }

    /** Re-checks the connection, e.g. when the app returns to the foreground. */
    fun refresh() {
        if (!::appContext.isInitialized) return
        val connected = isConnected
        _state.update {
            it.copy(connection = if (connected) AdbConnection.Connected else
                if (it.connection == AdbConnection.Connecting) AdbConnection.Connecting else AdbConnection.Disconnected)
        }
        if (!connected && _state.value.paired && _state.value.connection != AdbConnection.Connecting) {
            scope.launch { connect(quiet = true) }
        }
    }

    fun disconnect() {
        scope.launch {
            runCatching { manager?.disconnect() }
            _state.update { it.copy(connection = AdbConnection.Disconnected, message = null) }
        }
    }

    /** Disconnects and deletes the key. The phone's paired-devices entry can then be removed too. */
    fun forget() {
        scope.launch {
            runCatching { manager?.close() }
            manager = null
            AdbKeys.delete(appContext)
            prefs().edit().clear().apply()
            _state.value = AdbState()
        }
    }

    override suspend fun exec(command: String): ShellResult = withContext(Dispatchers.IO) {
        if (!isConnected) {
            if (_state.value.paired) connect(quiet = true)
            if (!isConnected) return@withContext ShellResult.failure("Not connected to Wireless debugging.")
        }
        val marker = "__POKERY_EXIT_${UUID.randomUUID().toString().take(8)}__"
        try {
            val stream = manager().openStream("shell:" + ShellProtocol.wrap(command, marker))
            val buffer = ByteArrayOutputStream()
            val reading = readers.submit {
                stream.openInputStream().use { input ->
                    val chunk = ByteArray(8_192)
                    while (true) {
                        val n = input.read(chunk)
                        if (n < 0) break
                        if (buffer.size() < MAX_OUTPUT_BYTES) buffer.write(chunk, 0, n)
                    }
                }
            }
            val timedOut = try {
                reading.get(EXEC_TIMEOUT_S, TimeUnit.SECONDS)
                false
            } catch (_: TimeoutException) {
                true
            } finally {
                runCatching { stream.close() }
            }
            val raw = buffer.toString(Charsets.UTF_8.name())
            val parsed = ShellProtocol.parse(raw, marker)
            var output = parsed.output
            if (buffer.size() >= MAX_OUTPUT_BYTES) output += "\n[output truncated]"
            when {
                timedOut -> ShellResult(124, output, "[stopped after ${EXEC_TIMEOUT_S}s]")
                else -> ShellResult(parsed.exitCode, output, "")
            }
        } catch (t: Throwable) {
            if (!isConnected) _state.update { it.copy(connection = AdbConnection.Disconnected) }
            ShellResult.failure("ADB error: ${friendly(t)}")
        }
    }

    // --- Wireless debugging helpers -------------------------------------------------

    fun isWirelessDebuggingOn(context: Context): Boolean =
        Settings.Global.getInt(context.contentResolver, "adb_wifi_enabled", 0) == 1

    fun isDeveloperOptionsOn(context: Context): Boolean =
        Settings.Global.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1

    fun isOnWifi(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    fun canToggleWirelessDebugging(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    /**
     * After a reboot Android turns Wireless debugging off. Once Pokery holds
     * WRITE_SECURE_SETTINGS (granted to itself on first connection) it can switch it
     * back on, so reconnecting needs no trip to Settings.
     */
    private fun enableWirelessDebuggingIfPossible() {
        val ctx = appContext
        if (!supportsWirelessPairing || !canToggleWirelessDebugging(ctx)) return
        if (isWirelessDebuggingOn(ctx) || !isOnWifi(ctx) || !isDeveloperOptionsOn(ctx)) return
        runCatching {
            Settings.Global.putInt(ctx.contentResolver, "adb_wifi_enabled", 1)
            Thread.sleep(1_500) // give adbd a moment to start advertising its port
        }
    }

    private suspend fun grantSelfWriteSecureSettings() {
        if (canToggleWirelessDebugging(appContext)) return
        exec("pm grant ${appContext.packageName} ${Manifest.permission.WRITE_SECURE_SETTINGS}")
    }

    private fun manager(): PokeryAdbManager =
        manager ?: synchronized(this) {
            manager ?: PokeryAdbManager(AdbKeys.loadOrCreate(appContext)).also { manager = it }
        }

    private fun prefs() = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun friendly(t: Throwable): String = when (t) {
        is AdbPairingRequiredException -> "This phone doesn't recognise Pokery any more. Pair again."
        is java.net.ConnectException -> "Connection refused. Is Wireless debugging on?"
        is InterruptedException -> "Interrupted."
        else -> {
            val msg = t.message ?: t.javaClass.simpleName
            when {
                msg.contains("Timed out while trying to find", true) ||
                    msg.contains("Could not find any valid host", true) ->
                    "Couldn't find Wireless debugging. Check it's on and you're connected to Wi-Fi."
                else -> msg
            }
        }
    }
}
