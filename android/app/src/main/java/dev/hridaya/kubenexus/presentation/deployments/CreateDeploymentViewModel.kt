package dev.hridaya.kubenexus.presentation.deployments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.hridaya.kubenexus.core.common.dispatcher.DispatcherProvider
import dev.hridaya.kubenexus.core.common.result.Result
import dev.hridaya.kubenexus.domain.model.DeploymentDraft
import dev.hridaya.kubenexus.domain.usecase.CreateDeploymentUseCase
import dev.hridaya.kubenexus.domain.usecase.CreateNamespaceUseCase
import dev.hridaya.kubenexus.presentation.common.NamespaceNameValidator
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Guided Deployment creation (issue #5). The cluster id, starting namespace and
 * known namespaces are supplied by the host ([dev.hridaya.kubenexus.presentation.main.MainScreen])
 * from the Home state at creation time, mirroring the PodDetailViewModel
 * assisted pattern.
 */
@HiltViewModel(assistedFactory = CreateDeploymentViewModel.Factory::class)
class CreateDeploymentViewModel @AssistedInject constructor(
    @Assisted("clusterId") private val clusterId: String?,
    @Assisted("namespace") initialNamespace: String,
    @Assisted("availableNamespaces") initialAvailableNamespaces: List<String>,
    private val createDeploymentUseCase: CreateDeploymentUseCase,
    private val createNamespaceUseCase: CreateNamespaceUseCase,
    private val dispatcherProvider: DispatcherProvider,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(
            @Assisted("clusterId") clusterId: String?,
            @Assisted("namespace") namespace: String,
            @Assisted("availableNamespaces") availableNamespaces: List<String>,
        ): CreateDeploymentViewModel
    }

    private val _uiState = MutableStateFlow(
        validated(
            CreateDeploymentUiState(
                namespace = initialNamespace.ifBlank { "default" },
                availableNamespaces = initialAvailableNamespaces
                    .filter { it.isNotBlank() && it != "All Namespaces" }
                    .distinct(),
            ),
        ),
    )
    val uiState: StateFlow<CreateDeploymentUiState> = _uiState.asStateFlow()

    private val _effects = Channel<CreateDeploymentUiEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    fun onAction(action: CreateDeploymentUiAction) {
        when (action) {
            is CreateDeploymentUiAction.NameChanged -> reduceField { it.copy(name = action.value) }
            is CreateDeploymentUiAction.ImageChanged -> reduceField { it.copy(image = action.value) }
            is CreateDeploymentUiAction.ReplicasChanged -> reduceField { it.copy(replicas = action.value) }
            is CreateDeploymentUiAction.ContainerPortChanged -> reduceField { it.copy(containerPort = action.value) }
            is CreateDeploymentUiAction.ServiceTypeSelected -> reduceField { it.copy(serviceType = action.serviceType) }
            is CreateDeploymentUiAction.ServicePortChanged -> reduceField { it.copy(servicePort = action.value) }
            is CreateDeploymentUiAction.NamespaceSelected -> reduceField { it.copy(namespace = action.namespace) }

            is CreateDeploymentUiAction.PreviewSubmitted -> previewYaml()

            is CreateDeploymentUiAction.BackToFormClicked -> _uiState.update {
                it.copy(step = CreateDeploymentStep.FORM, errorMessage = null)
            }

            is CreateDeploymentUiAction.ApplySubmitted -> applyReviewedYaml()

            is CreateDeploymentUiAction.ReviewedYamlChanged -> _uiState.update {
                it.copy(reviewedYaml = action.value)
            }

            is CreateDeploymentUiAction.DismissError -> _uiState.update {
                it.copy(errorMessage = null)
            }

            is CreateDeploymentUiAction.CreateNamespaceClicked -> _uiState.update {
                it.copy(
                    showCreateNamespaceDialog = true,
                    newNamespaceName = "",
                    newNamespaceError = null,
                )
            }

            is CreateDeploymentUiAction.DismissCreateNamespaceClicked -> _uiState.update {
                if (it.isCreatingNamespace) {
                    it
                } else {
                    it.copy(showCreateNamespaceDialog = false, newNamespaceError = null)
                }
            }

            is CreateDeploymentUiAction.NewNamespaceNameChanged -> _uiState.update {
                it.copy(
                    newNamespaceName = action.value,
                    newNamespaceError = NamespaceNameValidator.errorFor(action.value),
                )
            }

            is CreateDeploymentUiAction.CreateNamespaceSubmitted -> createNamespace()

            is CreateDeploymentUiAction.Reset -> _uiState.update { current ->
                validated(
                    CreateDeploymentUiState(
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
    private fun reduceField(transform: (CreateDeploymentUiState) -> CreateDeploymentUiState) {
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
        when (val result = createDeploymentUseCase.previewYaml(draftValid)) {
            is Result.Success -> _uiState.update {
                it.copy(
                    step = CreateDeploymentStep.REVIEW,
                    generatedYaml = result.data,
                    reviewedYaml = result.data,
                    errorMessage = null,
                )
            }

            is Result.Error -> _uiState.update {
                it.copy(errorMessage = result.error.message.takeIf { msg -> msg.isNotBlank() } ?: PREVIEW_ERROR_MESSAGE)
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

        val deploymentName = state.name

        _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
        viewModelScope.launch(dispatcherProvider.io) {
            when (val result = createDeploymentUseCase(clusterId, state.reviewedYaml)) {
                is Result.Success -> {
                    // Reset form state so reopening the dialog starts clean without stale data
                    _uiState.update { current ->
                        validated(
                            CreateDeploymentUiState(
                                namespace = current.namespace,
                                availableNamespaces = current.availableNamespaces,
                            ),
                        )
                    }
                    _effects.send(CreateDeploymentUiEffect.Created(deploymentName))
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
                    _effects.send(CreateDeploymentUiEffect.NamespaceCreated)
                }

                is Result.Error -> _uiState.update {
                    it.copy(
                        isCreatingNamespace = false,
                        newNamespaceError = result.error.message.ifBlank { NAMESPACE_ERROR_MESSAGE },
                    )
                }

                is Result.Loading -> Unit
            }
        }
    }

    private fun validated(state: CreateDeploymentUiState): CreateDeploymentUiState {
        return state.copy(
            fieldErrors = draftWithErrors(
                state,
                includeRequired = state.hasSubmitted
            ).second
        )
    }

    private fun buildDraft(state: CreateDeploymentUiState): DeploymentDraft {
        return draftWithErrors(state).first
    }

    /** Parses replicas/port safely; unparseable text becomes a field error instead of a crash. */
    private fun draftWithErrors(
        state: CreateDeploymentUiState,
        includeRequired: Boolean = state.hasSubmitted,
    ): Pair<DeploymentDraft, Map<String, String>> {
        val parsedReplicas = state.replicas.trim().toIntOrNull()
        val parsedContainerPort = state.containerPort.trim().toIntOrNull()
        val parsedServicePort = state.servicePort.trim().toIntOrNull()
        val draft = DeploymentDraft(
            name = state.name,
            namespace = state.namespace,
            image = state.image,
            replicas = parsedReplicas ?: DeploymentDraft.DEFAULT_REPLICAS,
            containerPort = parsedContainerPort ?: DeploymentDraft.DEFAULT_CONTAINER_PORT,
            serviceType = state.serviceType,
            servicePort = parsedServicePort ?: DeploymentDraft.DEFAULT_SERVICE_PORT,
        )
        val needsServicePort = state.serviceType != DeploymentDraft.SERVICE_TYPE_NONE
        val allErrors = buildMap {
            when {
                state.replicas.isBlank() -> put("replicas", "Replicas is required")
                parsedReplicas == null -> put("replicas", "Replicas must be a whole number")
            }
            when {
                state.containerPort.isBlank() -> put("containerPort", "Port is required")
                parsedContainerPort == null -> put("containerPort", "Port must be a whole number")
            }
            when {
                !needsServicePort -> Unit
                state.servicePort.isBlank() -> put("servicePort", "Service port is required")
                parsedServicePort == null -> put("servicePort", "Service port must be a whole number")
            }
            putAll(draft.validate())
        }
        val errors = if (includeRequired) {
            allErrors
        } else {
            allErrors.filter { (field, _) ->
                when (field) {
                    "name" -> state.name.isNotBlank()
                    "image" -> state.image.isNotBlank()
                    "namespace" -> state.namespace.isNotBlank()
                    "replicas" -> state.replicas.isNotBlank()
                    "containerPort" -> state.containerPort.isNotBlank()
                    "servicePort" -> state.servicePort.isNotBlank()
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
            "Couldn't create the deployment. Please try again in a moment."
        const val NAMESPACE_ERROR_MESSAGE =
            "Couldn't create that namespace."
    }
}
