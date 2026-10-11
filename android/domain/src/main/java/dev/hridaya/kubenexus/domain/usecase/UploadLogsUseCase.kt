package dev.hridaya.kubenexus.domain.usecase

import dev.hridaya.kubenexus.core.common.dispatcher.DispatcherProvider
import dev.hridaya.kubenexus.core.common.result.AppError
import dev.hridaya.kubenexus.core.common.result.Result
import dev.hridaya.kubenexus.core.security.LogSanitizer
import dev.hridaya.kubenexus.domain.model.LogPasteDestination
import dev.hridaya.kubenexus.domain.repository.LogPasteRepository
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Redacts credentials from logs and uploads them; call only once the user has agreed. */
class UploadLogsUseCase @Inject constructor(
    private val repository: LogPasteRepository,
    private val dispatcherProvider: DispatcherProvider,
) {
    val destination: LogPasteDestination
        get() = repository.destination

    suspend operator fun invoke(content: String): Result<String> {
        if (content.isBlank()) return Result.Error(AppError.Validation("Cannot export empty logs"))
        return withContext(dispatcherProvider.io) { repository.upload(LogSanitizer.sanitize(content)) }
    }
}
