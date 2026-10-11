package dev.hridaya.kubenexus.domain.usecase

import dev.hridaya.kubenexus.core.common.dispatcher.DispatcherProvider
import dev.hridaya.kubenexus.core.common.result.AppError
import dev.hridaya.kubenexus.core.common.result.Result
import dev.hridaya.kubenexus.domain.model.LogPasteDestination
import dev.hridaya.kubenexus.domain.repository.LogPasteRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UploadLogsUseCaseTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = RecordingRepository()
    private val useCase = UploadLogsUseCase(
        repository,
        object : DispatcherProvider {
            override val main: CoroutineDispatcher = dispatcher
            override val io: CoroutineDispatcher = dispatcher
            override val default: CoroutineDispatcher = dispatcher
            override val unconfined: CoroutineDispatcher = dispatcher
        },
    )

    @Test
    fun `blank logs are refused without contacting the service`() = runTest(dispatcher) {
        val result = useCase("   ")

        assertTrue((result as Result.Error).error is AppError.Validation)
        assertNull(repository.uploaded)
    }

    @Test
    fun `credentials are redacted before they leave the device`() = runTest(dispatcher) {
        val result = useCase("2026-08-27 connecting with password=hunter2 as Bearer abc.def")

        assertEquals("https://paste.test/12345", (result as Result.Success).data)
        val sent = repository.uploaded.orEmpty()
        assertFalse(sent.contains("hunter2"))
        assertFalse(sent.contains("abc.def"))
    }

    private class RecordingRepository : LogPasteRepository {
        override val destination = LogPasteDestination(name = "paste.test", retention = "1 day")
        var uploaded: String? = null

        override suspend fun upload(content: String): Result<String> {
            uploaded = content
            return Result.Success("https://paste.test/12345")
        }
    }
}
