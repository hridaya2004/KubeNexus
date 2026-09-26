package dev.hridaya.kubenexus.presentation.logcat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.hridaya.kubenexus.core.common.dispatcher.DispatcherProvider
import dev.hridaya.kubenexus.core.common.result.Result
import dev.hridaya.kubenexus.domain.model.LogLevel
import dev.hridaya.kubenexus.domain.model.LogcatEntry
import dev.hridaya.kubenexus.domain.usecase.ClearLogcatUseCase
import dev.hridaya.kubenexus.domain.usecase.DumpLogcatUseCase
import dev.hridaya.kubenexus.domain.usecase.GetLogcatStreamUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class LogcatViewModel @Inject constructor(
    private val getLogcatStreamUseCase: GetLogcatStreamUseCase,
    private val dumpLogcatUseCase: DumpLogcatUseCase,
    private val clearLogcatUseCase: ClearLogcatUseCase,
    private val dispatcherProvider: DispatcherProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LogcatUiState())
    val uiState: StateFlow<LogcatUiState> = _uiState.asStateFlow()

    private val _events = Channel<LogcatUiEvent>(Channel.BUFFERED)
    val events: Flow<LogcatUiEvent> = _events.receiveAsFlow()

    private var logStreamJob: Job? = null

    init {
        startLogStream()
    }

    fun onAction(action: LogcatUiAction) {
        when (action) {
            is LogcatUiAction.UpdateSearchQuery -> {
                _uiState.update { state ->
                    val filtered = applyFilter(state.logs, action.query, state.selectedLogLevel)
                    state.copy(searchQuery = action.query, filteredLogs = filtered)
                }
            }

            is LogcatUiAction.SelectLogLevel -> {
                _uiState.update { state ->
                    val filtered = applyFilter(state.logs, state.searchQuery, action.level)
                    state.copy(selectedLogLevel = action.level, filteredLogs = filtered)
                }
            }

            is LogcatUiAction.ToggleAutoScroll -> {
                _uiState.update { it.copy(autoScroll = !it.autoScroll) }
            }

            is LogcatUiAction.TogglePause -> {
                val newPaused = !_uiState.value.isPaused
                _uiState.update { it.copy(isPaused = newPaused) }
                if (newPaused) {
                    logStreamJob?.cancel()
                } else {
                    startLogStream()
                }
            }

            is LogcatUiAction.ToggleSearch -> {
                _uiState.update { state ->
                    val expanded = !state.isSearchExpanded
                    val query = if (!expanded) "" else state.searchQuery
                    val filtered = applyFilter(state.logs, query, state.selectedLogLevel)
                    state.copy(
                        isSearchExpanded = expanded,
                        searchQuery = query,
                        filteredLogs = filtered,
                    )
                }
            }

            is LogcatUiAction.ClearLogs -> {
                viewModelScope.launch {
                    // The stream keeps its own rolling buffer and would re-send the old lines
                    // with the next new one, so it is stopped and started afresh around the clear.
                    logStreamJob?.cancel()
                    clearLogcatUseCase()
                    _uiState.update {
                        it.copy(
                            logs = emptyList(),
                            filteredLogs = emptyList(),
                            levelCounts = emptyMap(),
                        )
                    }
                    if (!_uiState.value.isPaused) startLogStream()
                    _events.send(LogcatUiEvent.ShowMessage("Logs cleared"))
                }
            }

            is LogcatUiAction.RefreshLogs -> {
                viewModelScope.launch {
                    _uiState.update { it.copy(isLoading = true) }
                    when (val result = dumpLogcatUseCase(2000)) {
                        is Result.Success -> {
                            updateLogsInternal(result.data)
                        }

                        is Result.Error -> {
                            _uiState.update { it.copy(isLoading = false) }
                            _events.send(
                                LogcatUiEvent.ShowMessage(
                                    "Couldn't export your logs right now. Please try again.",
                                ),
                            )
                        }

                        Result.Loading -> Unit
                    }
                }
            }

            is LogcatUiAction.ShareLogs -> {
                viewModelScope.launch {
                    val textToShare = buildFormattedLogText(_uiState.value.filteredLogs)
                    if (textToShare.isNotBlank()) {
                        _events.send(LogcatUiEvent.ShareText(textToShare))
                    } else {
                        _events.send(LogcatUiEvent.ShowMessage("No logs to share"))
                    }
                }
            }

            is LogcatUiAction.CopyLogs -> {
                viewModelScope.launch {
                    val textToCopy = buildFormattedLogText(_uiState.value.filteredLogs)
                    if (textToCopy.isNotBlank()) {
                        _events.send(LogcatUiEvent.CopyToClipboard(textToCopy, "KubeNexus Logcat"))
                        _events.send(LogcatUiEvent.ShowMessage("Logs copied to clipboard"))
                    } else {
                        _events.send(LogcatUiEvent.ShowMessage("No logs to copy"))
                    }
                }
            }

            is LogcatUiAction.CopyLogEntry -> {
                viewModelScope.launch {
                    _events.send(LogcatUiEvent.CopyToClipboard(action.entry.raw, "Log Entry"))
                    _events.send(LogcatUiEvent.ShowMessage("Log entry copied"))
                }
            }
        }
    }

    private fun startLogStream() {
        logStreamJob?.cancel()
        logStreamJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            getLogcatStreamUseCase(maxBufferSize = 2000).collect { newLogs ->
                updateLogsInternal(newLogs)
            }
        }
    }

    private suspend fun updateLogsInternal(newLogs: List<LogcatEntry>) =
        withContext(dispatcherProvider.default) {
            val currentQuery = _uiState.value.searchQuery
            val currentLevel = _uiState.value.selectedLogLevel
            val counts = computeCounts(newLogs)
            val filtered = applyFilter(newLogs, currentQuery, currentLevel)

            _uiState.update { state ->
                state.copy(
                    logs = newLogs,
                    filteredLogs = filtered,
                    levelCounts = counts,
                    isLoading = false,
                )
            }
        }

    private fun applyFilter(
        logs: List<LogcatEntry>,
        query: String,
        level: LogLevel?
    ): List<LogcatEntry> {
        val trimmedQuery = query.trim()
        return logs.filter { entry ->
            val matchesLevel =
                level == null || entry.level == level || (level != LogLevel.UNKNOWN && entry.level.priority >= level.priority)
            val matchesQuery = trimmedQuery.isEmpty() ||
                    entry.tag.contains(trimmedQuery, ignoreCase = true) ||
                    entry.message.contains(trimmedQuery, ignoreCase = true) ||
                    entry.raw.contains(trimmedQuery, ignoreCase = true)
            matchesLevel && matchesQuery
        }
    }

    private fun computeCounts(logs: List<LogcatEntry>): Map<LogLevel, Int> {
        val counts = mutableMapOf<LogLevel, Int>()
        for (entry in logs) {
            counts[entry.level] = (counts[entry.level] ?: 0) + 1
        }
        return counts
    }

    private fun buildFormattedLogText(logs: List<LogcatEntry>): String {
        return buildString {
            appendLine("=== KubeNexus Logcat Export (${logs.size} entries) ===")
            appendLine("Timestamp: ${Date()}")
            appendLine()
            for (entry in logs) {
                appendLine(entry.raw)
            }
        }
    }
}
