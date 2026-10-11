package dev.hridaya.kubenexus.data.source.local

import android.os.Process as AndroidProcess
import android.util.Log
import dev.hridaya.kubenexus.core.common.dispatcher.DispatcherProvider
import dev.hridaya.kubenexus.core.common.log.APP_LOG_TAG_PREFIX
import dev.hridaya.kubenexus.domain.model.LogLevel
import dev.hridaya.kubenexus.domain.model.LogcatEntry
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicLong
import java.util.regex.Pattern
import javax.inject.Inject

/**
 * Whether a line from this app's process was written by KubeNexus itself: its own Kotlin and
 * native code (tags starting with [APP_LOG_TAG_PREFIX]), the Go engine (gomobile logs as `GoLog`)
 * and the app's crashes (`AndroidRuntime`). Everything else the framework logs from inside the
 * process, such as view, rendering and input chatter, is left out.
 */
internal fun isKubeNexusLog(tag: String): Boolean =
    tag.startsWith(APP_LOG_TAG_PREFIX) || tag == GO_ENGINE_LOG_TAG || tag == CRASH_LOG_TAG

private const val GO_ENGINE_LOG_TAG = "GoLog"
private const val CRASH_LOG_TAG = "AndroidRuntime"
private const val DUMP_LOG_TAG = "$APP_LOG_TAG_PREFIX/LogcatDump"

interface LogcatLocalDataSource {
    fun streamLogs(maxBufferSize: Int): Flow<List<LogcatEntry>>
    suspend fun dumpLogs(maxLines: Int): List<LogcatEntry>
    suspend fun clearLogs()
}

class DefaultLogcatLocalDataSource @Inject constructor(private val dispatcherProvider: DispatcherProvider) :
    LogcatLocalDataSource {

    private val entryIdSequence = AtomicLong(1L)
    private val threadTimePattern = Pattern.compile(
        """^(\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d{3})\s+(\d+)\s+(\d+)\s+([VDIWEFA])\s+(.+?)(?::\s*|\s+:\s*)(.*)$""",
    )

    /**
     * Streams KubeNexus's own logs (see [isKubeNexusLog]) as rolling snapshots of the newest
     * [maxBufferSize] entries.
     *
     * Snapshots are conflated, since only the latest matters, and sent at most every
     * [EMIT_INTERVAL_MS]; a timer flushes the last lines of a burst even when nothing follows.
     * Cancelling the collector destroys the logcat process straight away, which also unblocks
     * the reader waiting in readLine().
     */
    override fun streamLogs(maxBufferSize: Int): Flow<List<LogcatEntry>> = callbackFlow {
        val pid = AndroidProcess.myPid().toString()
        val buffer = ArrayDeque<LogcatEntry>(maxBufferSize)
        var pendingChanges = false
        val lock = Any()

        fun flush() {
            val snapshot = synchronized(lock) {
                if (!pendingChanges) return
                pendingChanges = false
                buffer.toList()
            }
            trySend(snapshot)
        }

        val process = try {
            ProcessBuilder("logcat", "-v", "threadtime", "--pid=$pid").start()
        } catch (e: Exception) {
            close(e)
            return@callbackFlow
        }

        val reader = launch(dispatcherProvider.io) {
            try {
                BufferedReader(InputStreamReader(process.inputStream)).use { input ->
                    while (isActive) {
                        val line = input.readLine() ?: break
                        val entry = parseLogLine(line)
                        if (entry.pid != pid || !isKubeNexusLog(entry.tag)) continue
                        synchronized(lock) {
                            if (buffer.size >= maxBufferSize) buffer.removeFirst()
                            buffer.addLast(entry)
                            pendingChanges = true
                        }
                    }
                }
            } catch (_: IOException) {
                // The process was destroyed because the collector went away.
            }
            flush()
            channel.close()
        }

        val ticker = launch {
            while (isActive) {
                delay(EMIT_INTERVAL_MS)
                flush()
            }
        }

        awaitClose {
            process.destroy()
            ticker.cancel()
            reader.cancel()
        }
    }.buffer(Channel.CONFLATED).flowOn(dispatcherProvider.io)

    override suspend fun dumpLogs(maxLines: Int): List<LogcatEntry> =
        withContext(dispatcherProvider.io) {
            val pid = AndroidProcess.myPid().toString()
            try {
                // An unsupported --pid is reported by exit code, not an exception.
                readLogcat(pid, maxLines, filterByPid = true)
                    ?: readLogcat(pid, maxLines, filterByPid = false)
                    ?: emptyList()
            } catch (e: IOException) {
                Log.w(DUMP_LOG_TAG, "Could not read logcat: ${e.message}")
                emptyList()
            }
        }

    /** This process's lines from a logcat dump, or null when logcat exits with an error. */
    private fun readLogcat(pid: String, maxLines: Int, filterByPid: Boolean): List<LogcatEntry>? {
        val command = listOf("logcat", "-d", "-v", "threadtime", "-t", maxLines.toString()) +
            if (filterByPid) listOf("--pid=$pid") else emptyList()
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        try {
            val entries = process.inputStream.bufferedReader().useLines { lines ->
                lines.map(::parseLogLine).filter { it.pid == pid && isKubeNexusLog(it.tag) }.toList()
            }
            return if (process.waitFor() == 0) entries else null
        } finally {
            process.destroy()
        }
    }

    override suspend fun clearLogs(): Unit = withContext(dispatcherProvider.io) {
        try {
            val process = ProcessBuilder("logcat", "-c").start()
            process.waitFor()
            process.destroy()
        } catch (_: Exception) {
        }
    }

    private fun parseLogLine(line: String): LogcatEntry {
        val matcher = threadTimePattern.matcher(line)
        return if (matcher.matches()) {
            val timestamp = matcher.group(1).orEmpty()
            val pid = matcher.group(2).orEmpty()
            val tid = matcher.group(3).orEmpty()
            val levelCode = matcher.group(4).orEmpty()
            val tag = matcher.group(5).orEmpty().trim()
            val message = matcher.group(6).orEmpty()
            LogcatEntry(
                id = entryIdSequence.getAndIncrement(),
                timestamp = timestamp,
                pid = pid,
                tid = tid,
                level = LogLevel.fromCode(levelCode),
                tag = tag,
                message = message,
                raw = line,
            )
        } else {
            LogcatEntry(
                id = entryIdSequence.getAndIncrement(),
                timestamp = "",
                pid = "",
                tid = "",
                level = LogLevel.UNKNOWN,
                tag = "",
                message = line,
                raw = line,
            )
        }
    }

    private companion object {
        const val EMIT_INTERVAL_MS = 100L
    }
}
