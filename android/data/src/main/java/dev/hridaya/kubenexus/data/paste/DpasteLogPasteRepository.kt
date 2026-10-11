package dev.hridaya.kubenexus.data.paste

import dev.hridaya.kubenexus.core.common.result.AppError
import dev.hridaya.kubenexus.core.common.result.Result
import dev.hridaya.kubenexus.domain.model.LogPasteDestination
import dev.hridaya.kubenexus.domain.repository.LogPasteRepository
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject

/**
 * [LogPasteRepository] backed by dpaste.org's public API.
 *
 * See https://docs.dpaste.org/api/. Pastes are public to anyone with the link and expire
 * after [EXPIRES_SECONDS]; dpaste.org only accepts a fixed set of lifetimes, and its default
 * when none is sent is one month.
 */
class DpasteLogPasteRepository @Inject constructor() : LogPasteRepository {

    override val destination = LogPasteDestination(name = "dpaste.org", retention = "7 days")

    override suspend fun upload(content: String): Result<String> {
        if (content.isBlank()) {
            return Result.Error(AppError.Validation("Cannot export empty logs"))
        }

        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(ENDPOINT_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty(
                    "Content-Type",
                    "application/x-www-form-urlencoded; charset=utf-8",
                )
                setRequestProperty("User-Agent", "KubeNexus-Android/1.0")
            }

            val postData = formBody(content).toByteArray(StandardCharsets.UTF_8)
            connection.outputStream.use { os -> os.write(postData) }

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val resultUrl = connection.inputStream.bufferedReader().use { it.readText() }.trim()
                if (resultUrl.startsWith("https://")) {
                    Result.Success(resultUrl)
                } else {
                    Result.Error(AppError.Network("Invalid response URL from dpaste.org"))
                }
            } else {
                Result.Error(AppError.Network("dpaste.org returned status $responseCode"))
            }
        } catch (e: Exception) {
            Result.Error(AppError.Network("dpaste.org upload failed: ${e.localizedMessage ?: e.message}"))
        } finally {
            connection?.disconnect()
        }
    }

    internal companion object {
        const val ENDPOINT_URL = "https://dpaste.org/api/"
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 30_000

        /** 7 days, one of the lifetimes dpaste.org accepts (3600, 604800, 2592000). */
        const val EXPIRES_SECONDS = 604800

        fun formBody(content: String): String = buildString {
            append("format=url")
            append("&lexer=_text")
            append("&expires=").append(EXPIRES_SECONDS)
            append("&content=").append(URLEncoder.encode(content, "UTF-8"))
        }
    }
}
