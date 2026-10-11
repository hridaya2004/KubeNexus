package dev.hridaya.kubenexus.presentation.services.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.hridaya.kubenexus.core.common.dispatcher.DispatcherProvider
import dev.hridaya.kubenexus.core.common.result.Result
import dev.hridaya.kubenexus.domain.model.ServiceDraft
import dev.hridaya.kubenexus.domain.usecase.CreateNamespaceUseCase
import dev.hridaya.kubenexus.domain.usecase.CreateServiceUseCase
import dev.hridaya.kubenexus.presentation.common.NamespaceNameValidator
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Guided Service creation. The cluster id, starting namespace and known namespaces
 * are supplied by the host ([dev.hridaya.kubenexus.presentation.main.MainScreen])
 * from the Home state at creation time, mirroring the PodDetailViewModel
 * assisted pattern.
 */
@HiltViewModel(assistedFactory = CreateServiceViewModel.Factory::class)
class CreateServiceViewModel @AssistedInject constructor(
    @Assisted("clusterId") private val clusterId: String?,
    @Assisted("namespace") initialNamespace: String,
    @Assisted("availableNamespaces") initialAvailableNamespaces: List<String>,
    private val createServiceUseCase: CreateServiceUseCase,
    private val createNamespaceUseCase: CreateNamespaceUseCase,
    private val dispatcherProvider: DispatcherProvider,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(
            @Assisted("clusterId") clusterId: String?,
            @Assisted("namespace") namespace: String,
            @Assisted("availableNamespaces") availableNamespaces: List<String>,
        ): CreateServiceViewModel
    }

    private val _uiState = MutableStateFlow(
        validated(
            CreateServiceUiState(
                namespace = initialNamespace.ifBlank { "default" },
                availableNamespaces = initialAvailableNamespaces
                    .filter { it.isNotBlank() && it != "All Namespaces" }
                    .distinct(),
            ),
        ),
    )
    val uiState: StateFlow<CreateServiceUiState> = _uiState.asStateFlow()

    private val _effects = Channel<CreateServiceUiEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    fun onAction(action: CreateServiceUiAction) {
        when (action) {
            is CreateServiceUiAction.NameChanged -> reduceField { it.copy(name = action.value) }
            is CreateServiceUiAction.SelectorAppChanged -> reduceField { it.copy(selectorApp = action.value) }
            is CreateServiceUiAction.PortChanged -> reduceField { it.copy(port = action.value) }
            is CreateServiceUiAction.TargetPortChanged -> reduceField { it.copy(targetPort = action.value) }
            is CreateServiceUiAction.ServiceTypeSelected -> reduceField { it.copy(serviceType = action.value) }
            is CreateServiceUiAction.NamespaceSelected -> reduceField { it.copy(namespace = action.namespace) }

            is CreateServiceUiAction.PreviewSubmitted -> previewYaml()

            is CreateServiceUiAction.BackToFormClicked -> _uiState.update {
                it.copy(step = CreateServiceStep.FORM, errorMessage = null)
            }

            is CreateServiceUiAction.ApplySubmitted -> applyReviewedYaml()

            is CreateServiceUiAction.ReviewedYamlChanged -> _uiState.update {
                it.copy(reviewedYaml = action.value)
            }

            is CreateServiceUiAction.DismissError -> _uiState.update {
                it.copy(errorMessage = null)
            }

            is CreateServiceUiAction.CreateNamespaceClicked -> _uiState.update {
                it.copy(
                    showCreateNamespaceDialog = true,
                    newNamespaceName = "",
                    newNamespaceError = null,
                )
            }

            is CreateServiceUiAction.DismissCreateNamespaceClicked -> _uiState.update {
                if (it.isCreatingNamespace) {
                    it
                } else {
                    it.copy(showCreateNamespaceDialog = false, newNamespaceError = null)
                }
            }

            is CreateServiceUiAction.NewNamespaceNameChanged -> _uiState.update {
                it.copy(
                    newNamespaceName = action.value,
                    newNamespaceError = NamespaceNameValidator.errorFor(action.value),
                )
            }

            is CreateServiceUiAction.CreateNamespaceSubmitted -> createNamespace()

            is CreateServiceUiAction.Reset -> _uiState.update { current ->
                validated(
                    CreateServiceUiState(
                        namespace = current.namespace,
                        availableNamespaces = current.availableNamespaces,
                    ),
                )
            }
        }
    }

    /**
     * Live validation: every edit revalidates the whole draft so errors clear as
     * the user types. Input is never rewritten or discarded on failure.
     */
    private fun reduceField(transform: (CreateServiceUiState) -> CreateServiceUiState) {
        _uiState.update { validated(transform(it)) }
    }

    private fun previewYaml() {
        val (draft, fullErrors) = draftWithErrors(_uiState.value, includeRequired = true)
        if (fullErrors.isNotEmpty()) {
            _uiState.update {
                it.copy(
                    hasSubmitted = true,
                    fieldErrors = fullErrors,
                    errorMessage = "Fix the highlighted fields to continue",
                )
            }
            return
        }
        val draftValid = buildDraft(state = _uiState.value)
        when (val result = createServiceUseCase.previewYaml(draftValid)) {
            is Result.Success -> _uiState.update {
                it.copy(
                    step = CreateServiceStep.REVIEW,
                    generatedYaml = result.data,
                    reviewedYaml = result.data,
                    errorMessage = null,
                )
            }

            is Result.Error -> _uiState.update {
                it.copy(errorMessage = PREVIEW_ERROR_MESSAGE)
            }

            is Result.Loading -> Unit
        }
    }

    /**
     * Applies exactly what the editor shows — never regenerates from the form —
     * so the cluster can only receive what the user confirmed. On failure the
     * REVIEW step, edited YAML and all form input are kept intact.
     */
    private fun applyReviewedYaml() {
        val state = _uiState.value
        if (state.isSubmitting || state.reviewedYaml.isBlank()) return

        val serviceName = state.name

        _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
        viewModelScope.launch(dispatcherProvider.io) {
            when (val result = createServiceUseCase(clusterId, state.reviewedYaml)) {
                is Result.Success -> {
                    // Reset form state so reopening the dialog starts clean without stale data
                    _uiState.update { current ->
                        validated(
                            CreateServiceUiState(
                                namespace = current.namespace,
                                availableNamespaces = current.availableNamespaces,
                            ),
                        )
                    }
                    _effects.send(CreateServiceUiEffect.Created(serviceName))
                }

                is Result.Error -> _uiState.update {
                    val errorMsg = result.error.message.takeIf { msg -> msg.isNotBlank() } ?: APPLY_ERROR_MESSAGE
                    it.copy(isSubmitting = false, errorMessage = errorMsg)
                }

                is Result.Loading -> Unit
            }
        }
    }

    private fun createNamespace() {
        val state = _uiState.value
        if (state.isCreatingNamespace) return
        val name = state.newNamespaceName.trim()
        NamespaceNameValidator.errorFor(name)?.let { error ->
            _uiState.update { it.copy(newNamespaceError = error) }
            return
        }

        _uiState.update { it.copy(isCreatingNamespace = true, newNamespaceError = null) }
        viewModelScope.launch(dispatcherProvider.io) {
            when (val result = createNamespaceUseCase(clusterId, name)) {
                is Result.Success -> {
                    // Append without refetching and land the draft in the new namespace.
                    _uiState.update { current ->
                        validated(
                            current.copy(
                                showCreateNamespaceDialog = false,
                                newNamespaceName = "",
                                newNamespaceError = null,
                                isCreatingNamespace = false,
                                availableNamespaces =
                                    (current.availableNamespaces + name).distinct(),
                                namespace = name,
                            ),
                        )
                    }
                    _effects.send(CreateServiceUiEffect.NamespaceCreated)
                }

                is Result.Error -> _uiState.update {
                    it.copy(
                        isCreatingNamespace = false,
                        newNamespaceError = result.error.message.ifBlank { NAMESPACE_ERROR_MESSAGE }
                    )
                }

                is Result.Loading -> Unit
            }
        }
    }

    private fun validated(state: CreateServiceUiState): CreateServiceUiState {
        return state.copy(
            fieldErrors = draftWithErrors(
                state,
                includeRequired = state.hasSubmitted
            ).second
        )
    }

    private fun buildDraft(state: CreateServiceUiState): ServiceDraft {
        return draftWithErrors(state).first
    }

    /** Parses the ports safely; unparseable text becomes field errors instead of a crash. */
    private fun draftWithErrors(
        state: CreateServiceUiState,
        includeRequired: Boolean = state.hasSubmitted,
    ): Pair<ServiceDraft, Map<String, String>> {
        val parsedPort = state.port.trim().toIntOrNull()
        val parsedTargetPort = state.targetPort.trim().toIntOrNull()
        val draft = ServiceDraft(
            name = state.name,
            namespace = state.namespace,
            selectorApp = state.selectorApp,
            port = parsedPort ?: ServiceDraft.DEFAULT_PORT,
            targetPort = parsedTargetPort ?: ServiceDraft.DEFAULT_PORT,
            serviceType = state.serviceType,
        )
        val allErrors = buildMap {
            if (state.port.isNotBlank() && parsedPort == null) {
                put("port", "Port must be a whole number")
            }
            if (state.targetPort.isNotBlank() && parsedTargetPort == null) {
                put("targetPort", "Target port must be a whole number")
            }
            putAll(draft.validate())
        }
        val errors = if (includeRequired) {
            allErrors
        } else {
            allErrors.filter { (field, _) ->
                when (field) {
                    "name" -> state.name.isNotBlank()
                    "selectorApp" -> state.selectorApp.isNotBlank()
                    "namespace" -> state.namespace.isNotBlank()
                    "port" -> state.port.isNotBlank()
                    "targetPort" -> state.targetPort.isNotBlank()
                    else -> true
                }
            }
        }
        return draft to errors
    }

    private companion object {
        const val PREVIEW_ERROR_MESSAGE =
            "Couldn't prepare the manifest preview. Please check your inputs and try again."
        const val APPLY_ERROR_MESSAGE =
            "Couldn't create the service. Please try again in a moment."
        const val NAMESPACE_ERROR_MESSAGE =
            "Couldn't create that namespace."
    }
}
