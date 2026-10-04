package com.iranjan.hotspotscheduler.toggle

import java.util.concurrent.TimeUnit

/**
 * Runs shell commands for the Shizuku engine, in Shizuku's user-service process.
 *
 * Wire format with [ShizukuEngine.exec]: the first line is always `EXIT:<code>`, everything
 * after it is command output.
 */
class ShellService : IShellService.Stub() {

    override fun runCommand(command: String): String {
        var process: Process? = null
        var reader: Thread? = null
        try {
            process = ProcessBuilder("sh", "-c", command)
                .redirectErrorStream(true)
                .start()
            val child = process

            // A StringBuffer (synchronized) is required: the reader thread appends while the
            // binder thread reads. A plain StringBuilder could hand back a torn string or throw.
            val output = StringBuffer()

            reader = Thread {
                try {
                    child.inputStream.bufferedReader().use { br ->
                        val buf = CharArray(8192)
                        while (true) {
                            val n = br.read(buf)
                            if (n < 0) break
                            synchronized(output) {
                                // Cap what we KEEP, but keep draining: abandoning the pipe makes
                                // the child block in write() and the command then "times out"
                                // even though it actually succeeded.
                                val room = MAX_OUTPUT_CHARS - output.length
                                if (room > 0) output.append(buf, 0, minOf(n, room))
                            }
                        }
                    }
                } catch (t: Throwable) {
                    // stream closed by destroyForcibly(), or a decode error: output so far stands
                }
            }
            reader.name = "hs-shell-reader"
            reader.isDaemon = true
            reader.start()

            val finished = child.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!finished) {
                child.destroyForcibly()
                // Join the real reader before touching the buffer, otherwise it is still
                // appending underneath us.
                reader.join(JOIN_TIMEOUT_MS)
                synchronized(output) {
                    return "EXIT:$EXIT_TIMEOUT\n$output\n[timeout after ${TIMEOUT_SECONDS}s]"
                }
            }
            reader.join(JOIN_TIMEOUT_MS)
            val exit = child.exitValue()
            synchronized(output) {
                return "EXIT:$exit\n$output"
            }
        } catch (t: Throwable) {
            runCatching { process?.destroyForcibly() }
            return "EXIT:$EXIT_ERROR\n${t.message ?: "error"}"
        } finally {
            reader?.interrupt()
        }
    }

    override fun exit() {
        System.exit(0)
    }

    companion object {
        /**
         * Binder transactions are capped at ~1 MiB for the whole payload, and a Kotlin String
         * costs 2 bytes per char, so the previous 1 Mi-char cap guaranteed a
         * TransactionTooLargeException that ShizukuEngine swallowed as "command failed".
         * 64 Ki chars (~128 KiB) leaves ample room and is far more than the grep-filtered
         * dumpsys output this app ever asks for.
         */
        private const val MAX_OUTPUT_CHARS = 64 * 1024
        private const val TIMEOUT_SECONDS = 20L
        private const val JOIN_TIMEOUT_MS = 2_000L
        private const val EXIT_TIMEOUT = 124
        private const val EXIT_ERROR = -1
    }
}