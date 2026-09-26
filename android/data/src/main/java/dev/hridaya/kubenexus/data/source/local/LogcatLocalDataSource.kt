package dev.hridaya.kubenexus.data.source.local

import android.os.Process as AndroidProcess
import dev.hridaya.kubenexus.core.common.dispatcher.DispatcherProvider
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
     * Streams this app's own logcat as rolling snapshots of the newest [maxBufferSize] entries.
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
                        if (entry.pid.isNotEmpty() && entry.pid != pid) continue
                        if (entry.level == LogLevel.UNKNOWN &&
                            entry.tag == "System" &&
                            !entry.message.contains(pid)
                        ) {
                            continue
                        }
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
            val entries = mutableListOf<LogcatEntry>()

            try {
                val process = ProcessBuilder(
                    "logcat",
                    "-d",
                    "-v",
                    "threadtime",
                    "-t",
                    maxLines.toString(),
                    "--pid=$pid",
                ).start()
                val reader = BufferedReader(InputStreamReader(process.inputStream))

                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    line?.let {
                        val entry = parseLogLine(it)
                        if (entry.pid.isEmpty() || entry.pid == pid) {
                            entries.add(entry)
                        }
                    }
                }
                process.waitFor()
                process.destroy()
            } catch (_: Exception) {
                try {
                    val process = ProcessBuilder(
                        "logcat",
                        "-d",
                        "-v",
                        "threadtime",
                        "-t",
                        maxLines.toString(),
                    ).start()
                    val reader = BufferedReader(InputStreamReader(process.inputStream))
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        line?.let {
                            val entry = parseLogLine(it)
                            if (entry.pid == pid) {
                                entries.add(entry)
                            }
                        }
                    }
                    process.waitFor()
                    process.destroy()
                } catch (_: Exception) {
                }
            }

            entries
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
