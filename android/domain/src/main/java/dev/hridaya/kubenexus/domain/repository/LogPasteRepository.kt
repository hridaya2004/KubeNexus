package dev.hridaya.kubenexus.domain.repository

import dev.hridaya.kubenexus.core.common.result.Result
import dev.hridaya.kubenexus.domain.model.LogPasteDestination

/**
 * A third-party paste service that logs can be uploaded to. There is deliberately one: the
 * user agrees to a specific [destination], so a silent fallback would upload somewhere else.
 */
interface LogPasteRepository {

    val destination: LogPasteDestination

    /** Uploads [content] as given and returns the paste's public URL. */
    suspend fun upload(content: String): Result<String>
}
