package dev.hridaya.kubenexus.presentation.portforward.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.hridaya.kubenexus.domain.model.ActivePortForwardSession
import dev.hridaya.kubenexus.domain.usecase.ObservePortForwardSessionsUseCase
import dev.hridaya.kubenexus.domain.usecase.StopPortForwardUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The app-wide port-forward list, with the include-stopped toggle and local dismissal. */
@HiltViewModel
class PortForwardSessionsViewModel @Inject constructor(
    private val observePortForwardSessions: ObservePortForwardSessionsUseCase,
    private val stopPortForward: StopPortForwardUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PortForwardSessionsUiState())
    val uiState: StateFlow<PortForwardSessionsUiState> = _uiState.asStateFlow()

    /**
     * Handles dismissed from the UI. The manager keeps STOPPED rows around by
     * contract, so dismissal cannot be a passthrough (the next emission would
     * resurrect the row); it is filtered locally here and must also survive
     * later emissions, hence the remembered set.
     */
    private val locallyDismissedHandles = mutableSetOf<String>()

    init {
        viewModelScope.launch {
            observePortForwardSessions().collect { sessions ->
                _uiState.update { state ->
                    state.copy(sessions = sessions.notDismissed())
                }
            }
        }
    }

    fun onAction(action: PortForwardSessionsUiAction) {
        when (action) {
            is PortForwardSessionsUiAction.Stop -> stop(action.handleId)
            is PortForwardSessionsUiAction.DismissStopped -> dismissStopped(action.handleId)
            PortForwardSessionsUiAction.StopAllActive -> stopAllActive()
            PortForwardSessionsUiAction.ToggleIncludeStopped ->
                _uiState.update { it.copy(includeStopped = !it.includeStopped) }
        }
    }

    /** Asks the manager to tear down one tunnel; state updates via the flow. */
    fun stop(handleId: String) {
        viewModelScope.launch {
            stopPortForward(handleId)
        }
    }

    /** Removes one STOPPED row from view only; never touches the manager. */
    fun dismissStopped(handleId: String) {
        locallyDismissedHandles += handleId
        _uiState.update { state ->
            state.copy(sessions = state.sessions.filterNot { it.handleId == handleId })
        }
    }

    private fun stopAllActive() {
        _uiState.value.sessions
            .filter { it.isActive }
            .forEach { stop(it.handleId) }
    }

    private fun List<ActivePortForwardSession>.notDismissed(): List<ActivePortForwardSession> =
        filterNot { it.handleId in locallyDismissedHandles }
}
