package com.iranjan.hotspotscheduler.accessibility

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Persistent diagnostics log.
 *
 * File writes go through a single background thread: add() is called many times per toggle
 * attempt (including ~40 lines for a screen dump) and previously opened, appended and closed
 * the file synchronously, on whatever thread called it.
 */
object AttemptLog {
    private const val MAX_LINES = 400
    private const val MAX_FILE_BYTES = MAX_LINES * 200L

    private val format = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US)
    private val cached = ArrayDeque<String>()
    private val ioExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "hs-attempt-log").apply { isDaemon = true }
    }

    @Volatile
    private var logFile: File? = null

    fun init(context: Context) {
        synchronized(this) {
            if (logFile != null) return
            val file = File(context.filesDir, "diagnostics.log")
            logFile = file
            runCatching {
                if (file.exists()) {
                    file.readLines().takeLast(MAX_LINES).forEach { cached.addLast(it) }
                }
            }
        }
    }

    fun add(message: String) {
        val line = format.format(Date()) + "  " + message
        val file: File
        val overflow: List<String>
        synchronized(this) {
            cached.addLast(line)
            while (cached.size > MAX_LINES) cached.removeFirst()
            file = logFile ?: return
            overflow = if (file.length() > MAX_FILE_BYTES) cached.toList() else emptyList()
        }
        // Outside the lock, and off the caller's thread: add() is called many times per toggle
        // (including ~40 lines for a screen dump) and used to open/append/close the file inline.
        io {
            if (overflow.isNotEmpty()) {
                file.writeText(overflow.joinToString("\n") + "\n")
            } else {
                file.appendText(line + "\n")
            }
        }
    }

    /** Fire-and-forget: used from hot paths that must not block on disk. */
    private fun io(block: () -> Unit) {
        runCatching {
            ioExecutor.execute {
                runCatching { block() }.onFailure { Log.w(TAG, "diagnostics io failed", it) }
            }
        }.onFailure { Log.w(TAG, "diagnostics enqueue failed", it) }
    }

    fun snapshot(): List<String> = synchronized(this) { cached.toList() }

    fun clear() {
        val file = synchronized(this) {
            cached.clear()
            logFile
        }
        io {
            runCatching { file?.writeText("") }
        }
    }

    /** Blocks until queued writes have drained. Test/shutdown use only. */
    fun awaitIdle() {
        ioExecutor.submit { }.get()
    }

    private const val TAG = "HSLog"
}