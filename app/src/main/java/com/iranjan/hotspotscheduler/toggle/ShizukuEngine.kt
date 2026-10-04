package com.iranjan.hotspotscheduler.toggle

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import com.iranjan.hotspotscheduler.accessibility.AttemptLog
import dagger.hilt.android.qualifiers.ApplicationContext
import rikka.shizuku.Shizuku
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class ShellResult(val exitCode: Int, val output: String) {
    val success: Boolean get() = exitCode == 0
}

/**
 * Decodes the `EXIT:<code>\n<output>` envelope produced by ShellService.
 *
 * The sentinel is only honoured on the FIRST line: command output may legitimately contain
 * a line starting with `EXIT:` (e.g. `echo EXIT:0`), and treating that as the exit code would
 * silently fabricate success.
 */
object ShellProtocol {
    const val EXIT_TIMEOUT = 124
    const val EXIT_UNBOUND = -1
    private const val PREFIX = "EXIT:"

    fun parse(raw: String): ShellResult {
        val newline = raw.indexOf('\n')
        val first = if (newline >= 0) raw.substring(0, newline) else raw
        val rest = if (newline >= 0) raw.substring(newline + 1) else ""
        if (!first.startsWith(PREFIX)) {
            return ShellResult(EXIT_UNBOUND, raw)
        }
        val code = first.removePrefix(PREFIX).trim().toIntOrNull() ?: EXIT_UNBOUND
        return ShellResult(code, rest)
    }
}

@Singleton
class ShizukuEngine @Inject constructor(@ApplicationContext private val context: Context) {

    @Volatile
    private var service: IShellService? = null

    @Volatile
    private var latch = CountDownLatch(1)

    @Volatile
    private var softApSupported: Boolean? = null

    @Volatile
    private var bindBlockedUntilMs = 0L

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = binder?.let { IShellService.Stub.asInterface(it) }
            AttemptLog.add("shizuku shell service connected=${service != null}")
            latch.countDown()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
        }
    }

    companion object {
        /** A single shell call should never take longer than ShellService's own 20s budget. */
        private const val EXEC_TIMEOUT_MS = 30_000L
        private const val BIND_TIMEOUT_SECONDS = 10L

        /** After a failed bind, refuse to retry for this long instead of stalling every call. */
        private const val BIND_BACKOFF_MS = 60_000L
    }

    fun isRunning(): Boolean = try {
        Shizuku.pingBinder()
    } catch (t: Throwable) {
        false
    }

    fun hasPermission(): Boolean = try {
        isRunning() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (t: Throwable) {
        false
    }

    fun isReady(): Boolean = hasPermission()

    private fun awaitService(): IShellService? {
        service?.let { return it }
        synchronized(this) {
            service?.let { return it }
            // Negative-cache a failed bind. Without this, every exec() call re-pays the full
            // bind timeout and the caller can never distinguish "Shizuku is down" from "slow".
            if (bindBlockedUntilMs > System.currentTimeMillis()) return null

            latch = CountDownLatch(1)
            // bindUserService returns void: success is only observable via onServiceConnected,
            // so a failure is either a throw here or the latch never counting down.
            try {
                val args = Shizuku.UserServiceArgs(ComponentName(context, ShellService::class.java))
                    .processNameSuffix("shell")
                    .version(2)
                    .debuggable(false)
                Shizuku.bindUserService(args, connection)
            } catch (t: Throwable) {
                AttemptLog.add("shizuku bind failed: ${t.message}")
                bindBlockedUntilMs = System.currentTimeMillis() + BIND_BACKOFF_MS
                return null
            }
            if (!latch.await(BIND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                AttemptLog.add("shizuku bind timed out after ${BIND_TIMEOUT_SECONDS}s")
                bindBlockedUntilMs = System.currentTimeMillis() + BIND_BACKOFF_MS
                return null
            }
            if (service == null) {
                bindBlockedUntilMs = System.currentTimeMillis() + BIND_BACKOFF_MS
            }
            return service
        }
    }

    suspend fun exec(command: String): ShellResult = withContext(Dispatchers.IO) {
        val shell = awaitService() ?: return@withContext ShellResult(ShellProtocol.EXIT_UNBOUND, "service not bound")
        try {
            // runCommand is a blocking binder call, so withTimeoutOrNull only bounds the wait
            // between suspension points. It still guarantees we do not start an unbounded
            // number of binder calls, which the old code could (up to ~30s each).
            val raw = withTimeoutOrNull(EXEC_TIMEOUT_MS) { shell.runCommand(command) }
                ?: return@withContext ShellResult(
                    ShellProtocol.EXIT_TIMEOUT,
                    "no response from shell service within ${EXEC_TIMEOUT_MS}ms"
                )
            ShellProtocol.parse(raw)
        } catch (t: Throwable) {
            AttemptLog.add("shizuku exec failed: ${t.message}")
            ShellResult(ShellProtocol.EXIT_UNBOUND, t.message ?: "error")
        }
    }

    suspend fun mobileData(on: Boolean): Boolean {
        return exec("svc data " + if (on) "enable" else "disable").success
    }

    suspend fun mobileDataState(): Boolean? {
        val result = exec("settings get global mobile_data")
        return when (result.output.trim()) {
            "1" -> true
            "0" -> false
            else -> null
        }
    }

    suspend fun hotspotCommandSupported(): Boolean {
        softApSupported?.let { return it }
        val supported = exec("cmd wifi help").output.contains("start-softap")
        softApSupported = supported
        AttemptLog.add("shizuku start-softap supported=$supported")
        return supported
    }

    /** Drops the memoised `cmd wifi help` probe so a ROM/Shizuku change can be picked up. */
    fun invalidateCapabilityCache() {
        softApSupported = null
    }

    /**
     * Start the hotspot. Uses cached SSID/passphrase when available so the user's
     * configured network name is preserved; the config from `cmd wifi` is session-only.
     */
    suspend fun setHotspot(
        on: Boolean,
        ssid: String?,
        passphrase: String?,
        openNetwork: Boolean = false
    ): Boolean {
        if (on) {
            val cmd = if (openNetwork) {
                HotspotCommands.startSoftapOpenCmd(ssid ?: HotspotCommands.DEFAULT_SSID)
            } else {
                val c = HotspotCommands.startSoftapCmd(ssid ?: HotspotCommands.DEFAULT_SSID, passphrase ?: "")
                if (c == null) {
                    AttemptLog.add("shizuku hotspot start rejected: invalid passphrase")
                    return false
                }
                c
            }
            val result = exec(cmd)
            val outcome = HotspotCommands.parseStartOutcome(result.output)
            AttemptLog.add("shizuku start-softap exit=${result.exitCode} outcome=$outcome out='${result.output.take(200)}'")
            return when (outcome) {
                HotspotCommands.StartOutcome.STARTED -> true
                HotspotCommands.StartOutcome.FAILED -> false
                HotspotCommands.StartOutcome.UNKNOWN -> {
                    // `cmd wifi start-softap` always exits 0; when callback output is
                    // missing, verify the actual state before believing it.
                    hotspotState()?.on == true
                }
            }
        } else {
            val result = exec(HotspotCommands.stopSoftapCmd())
            AttemptLog.add("shizuku stop-softap exit=${result.exitCode} out='${result.output.take(200)}'")
            if (!result.success) return false
            // `cmd wifi stop-softap` exits 0 even when it fails, and a null probe means the
            // state is UNKNOWN, not "off". Never report success on an unknown state: the
            // data-cap path trusts this result to tell the user the hotspot is down.
            return when (hotspotState()?.on) {
                false -> true
                true -> false
                null -> false
            }
        }
    }

    /** Hotspot state via a grep-filtered dumpsys probe (fits the binder transaction limit). */
    suspend fun hotspotState(): HotspotCommands.StateProbe? {
        val result = exec(HotspotCommands.stateProbeCmd())
        return HotspotCommands.parseStateProbe(result.output)
    }
}
