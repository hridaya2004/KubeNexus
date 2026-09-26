package dev.hridaya.kubenexus.data.repository

import dev.hridaya.kubenexus.core.common.dispatcher.DispatcherProvider
import dev.hridaya.kubenexus.core.common.result.AppError
import dev.hridaya.kubenexus.core.common.result.Result
import dev.hridaya.kubenexus.data.source.local.LogcatLocalDataSource
import dev.hridaya.kubenexus.domain.model.LogcatEntry
import dev.hridaya.kubenexus.domain.repository.LogcatRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject

class LogcatRepositoryImpl @Inject constructor(
    private val localDataSource: LogcatLocalDataSource,
    private val dispatcherProvider: DispatcherProvider
) : LogcatRepository {

    override fun streamLogs(maxBufferSize: Int): Flow<List<LogcatEntry>> {
        return localDataSource.streamLogs(maxBufferSize)
    }

    override suspend fun dumpLogs(maxLines: Int): Result<List<LogcatEntry>> =
        withContext(dispatcherProvider.io) {
            try {
                val logs = localDataSource.dumpLogs(maxLines)
                Result.Success(logs)
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                Result.Error(AppError.Unknown(e.message ?: "Failed to dump logcat", e))
            }
        }

    override suspend fun clearLogs(): Result<Unit> = withContext(dispatcherProvider.io) {
        try {
            localDataSource.clearLogs()
            Result.Success(Unit)
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            Result.Error(AppError.Unknown(e.message ?: "Failed to clear logcat", e))
        }
    }
}
