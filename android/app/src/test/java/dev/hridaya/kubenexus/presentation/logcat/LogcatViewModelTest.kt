package dev.hridaya.kubenexus.presentation.logcat

import dev.hridaya.kubenexus.core.common.dispatcher.DispatcherProvider
import dev.hridaya.kubenexus.core.common.result.Result
import dev.hridaya.kubenexus.domain.model.LogLevel
import dev.hridaya.kubenexus.domain.model.LogcatEntry
import dev.hridaya.kubenexus.domain.repository.LogcatRepository
import dev.hridaya.kubenexus.domain.usecase.ClearLogcatUseCase
import dev.hridaya.kubenexus.domain.usecase.DumpLogcatUseCase
import dev.hridaya.kubenexus.domain.usecase.GetLogcatStreamUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LogcatViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testDispatcherProvider = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
        override val unconfined: CoroutineDispatcher = testDispatcher
    }

    private val sampleLogs = listOf(
        LogcatEntry(
            id = 1L,
            timestamp = "08-17 11:34:12.123",
            pid = "1234",
            tid = "1234",
            level = LogLevel.DEBUG,
            tag = "KubeNexus",
            message = "Initializing native client bridge",
            raw = "08-17 11:34:12.123 1234 1234 D KubeNexus: Initializing native client bridge",
        ),
        LogcatEntry(
            id = 2L,
            timestamp = "08-17 11:34:12.150",
            pid = "1234",
            tid = "1235",
            level = LogLevel.INFO,
            tag = "PodRepository",
            message = "Cluster connected successfully",
            raw = "08-17 11:34:12.150 1234 1235 I PodRepository: Cluster connected successfully",
        ),
        LogcatEntry(
            id = 3L,
            timestamp = "08-17 11:34:12.200",
            pid = "1234",
            tid = "1236",
            level = LogLevel.ERROR,
            tag = "NativeBridge",
            message = "Failed to parse pod manifest",
            raw = "08-17 11:34:12.200 1234 1236 E NativeBridge: Failed to parse pod manifest",
        ),
    )

    private val fakeRepository = object : LogcatRepository {
        var clearCalled = false

        /** Lines logged after the stream started. */
        val newLines = MutableSharedFlow<LogcatEntry>(extraBufferCapacity = 16)

        // Like the real stream, each collection keeps its own rolling buffer and re-sends the
        // whole of it with every new line; lines from before a clear are gone for a new one.
        override fun streamLogs(maxBufferSize: Int): Flow<List<LogcatEntry>> = flow {
            val buffer = if (clearCalled) mutableListOf() else sampleLogs.toMutableList()
            emit(buffer.toList())
            newLines.collect { line ->
                buffer += line
                emit(buffer.toList())
            }
        }

        override suspend fun dumpLogs(maxLines: Int): Result<List<LogcatEntry>> {
            return Result.Success(sampleLogs)
        }

        override suspend fun clearLogs(): Result<Unit> {
            clearCalled = true
            return Result.Success(Unit)
        }
    }

    private lateinit var viewModel: LogcatViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = LogcatViewModel(
            getLogcatStreamUseCase = GetLogcatStreamUseCase(fakeRepository),
            dumpLogcatUseCase = DumpLogcatUseCase(fakeRepository),
            clearLogcatUseCase = ClearLogcatUseCase(fakeRepository),
            dispatcherProvider = testDispatcherProvider,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state loads streaming logs and calculates counts`() = runTest(testDispatcher) {
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(3, state.logs.size)
        assertEquals(3, state.filteredLogs.size)
        assertEquals(1, state.levelCounts[LogLevel.DEBUG])
        assertEquals(1, state.levelCounts[LogLevel.INFO])
        assertEquals(1, state.levelCounts[LogLevel.ERROR])
        assertFalse(state.isLoading)
    }

    @Test
    fun `filtering by search query returns matching entries`() = runTest(testDispatcher) {
        advanceUntilIdle()

        viewModel.onAction(LogcatUiAction.UpdateSearchQuery("PodRepository"))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.filteredLogs.size)
        assertEquals("PodRepository", state.filteredLogs.first().tag)
    }

    @Test
    fun `filtering by log level returns entries at or above priority`() = runTest(testDispatcher) {
        advanceUntilIdle()

        viewModel.onAction(LogcatUiAction.SelectLogLevel(LogLevel.ERROR))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.filteredLogs.size)
        assertEquals(LogLevel.ERROR, state.filteredLogs.first().level)
    }

    @Test
    fun `clearing logs resets state and invokes use case`() = runTest(testDispatcher) {
        advanceUntilIdle()

        viewModel.onAction(LogcatUiAction.ClearLogs)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.logs.isEmpty())
        assertTrue(state.filteredLogs.isEmpty())
        assertTrue(fakeRepository.clearCalled)
    }

    // The next line logged after a clear must not bring the cleared ones back.
    @Test
    fun `cleared logs stay cleared when new lines arrive`() = runTest(testDispatcher) {
        advanceUntilIdle()
        viewModel.onAction(LogcatUiAction.ClearLogs)
        advanceUntilIdle()

        val next = sampleLogs.first().copy(id = 99L, message = "after clear", raw = "after clear")
        fakeRepository.newLines.emit(next)
        advanceUntilIdle()

        assertEquals(listOf(99L), viewModel.uiState.value.logs.map { it.id })
    }

    @Test
    fun `toggle autoScroll and pause updates state`() = runTest(testDispatcher) {
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.autoScroll)
        viewModel.onAction(LogcatUiAction.ToggleAutoScroll)
        assertFalse(viewModel.uiState.value.autoScroll)

        assertFalse(viewModel.uiState.value.isPaused)
        viewModel.onAction(LogcatUiAction.TogglePause)
        assertTrue(viewModel.uiState.value.isPaused)
    }
}
