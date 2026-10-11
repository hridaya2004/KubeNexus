package dev.hridaya.kubenexus.core.common.util

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import dev.hridaya.kubenexus.core.common.paste.DpasteLogPasteProvider
import dev.hridaya.kubenexus.core.common.paste.LogPasteProvider
import dev.hridaya.kubenexus.core.common.result.AppError
import dev.hridaya.kubenexus.core.common.result.Result
import dev.hridaya.kubenexus.core.security.LogSanitizer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Utility for exporting and sharing pod logs as files or to pluggable pastebin services.
 */
object LogExportHelper {

    /**
     * The paste service used for "Upload to Pastebin". There is deliberately a single one:
     * the user is told which host receives the logs and for how long before agreeing, so a
     * silent fallback to another service would upload somewhere they did not agree to.
     */
    val pasteProvider: LogPasteProvider = DpasteLogPasteProvider()

    /** Exports older than this are deleted before a new one is written. */
    private const val EXPORT_MAX_AGE_MS = 60 * 60 * 1000L

    /**
     * Writes logs to a file in the app's cache directory and shares it as an actual file
     * attachment via Android's share sheet and [FileProvider].
     *
     * The file is written on [ioDispatcher], never the main thread, and earlier exports are
     * pruned so the cache does not accumulate copies of pod logs.
     */
    suspend fun shareAsFile(
        context: Context,
        content: String,
        filename: String = "pod.log",
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    ) {
        if (content.isBlank()) {
            Toast.makeText(context, "No logs to export", Toast.LENGTH_SHORT).show()
            return
        }

        val safeName = safeFileName(filename)
        try {
            val logFile = withContext(ioDispatcher) {
                val logsDir = File(context.cacheDir, "logs").apply { mkdirs() }
                pruneExports(logsDir, System.currentTimeMillis())
                File(logsDir, safeName).apply { writeText(LogSanitizer.sanitize(content), Charsets.UTF_8) }
            }

            val fileUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                logFile,
            )

            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, fileUri)
                putExtra(Intent.EXTRA_SUBJECT, safeName)
                putExtra(Intent.EXTRA_TITLE, safeName)
                clipData = ClipData.newUri(context.contentResolver, safeName, fileUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(sendIntent, "Export Logs ($safeName)").apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Toast.makeText(
                context,
                "Failed to export log file: ${e.localizedMessage ?: e.message}",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    /**
     * Reduces [filename] to a flat, safe name inside the exports directory, so a pod or
     * container name can never introduce a path separator or a hidden or empty name.
     */
    internal fun safeFileName(filename: String): String {
        val cleaned = filename.replace(Regex("[^A-Za-z0-9._-]"), "-").trimStart('.', '-')
        return cleaned.ifEmpty { "logs.log" }.take(120)
    }

    /** Deletes exports older than [EXPORT_MAX_AGE_MS]; a share target may still be reading newer ones. */
    internal fun pruneExports(logsDir: File, nowMs: Long) {
        logsDir.listFiles()?.forEach { file ->
            if (file.isFile && nowMs - file.lastModified() > EXPORT_MAX_AGE_MS) {
                file.delete()
            }
        }
    }

    /**
     * Redacts recognisable credentials from [content] and uploads it with [provider].
     *
     * Only call this after the user has agreed to send the logs to [LogPasteProvider.name].
     */
    suspend fun uploadToPastebin(
        content: String,
        provider: LogPasteProvider = pasteProvider,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
    ): Result<String> = withContext(dispatcher) {
        if (content.isBlank()) {
            return@withContext Result.Error(
                AppError.Validation("Cannot export empty logs"),
            )
        }
        provider.upload(LogSanitizer.sanitize(content))
    }

    /**
     * Copies [text] to the clipboard. Logs are treated as [sensitive] unless the caller says
     * otherwise, so the system clipboard preview does not show them.
     */
    fun copyToClipboard(context: Context, text: String, label: String = "Logs", sensitive: Boolean = true) {
        ClipboardHelper.copy(context, label, text, sensitive)
    }
}
