package dev.hridaya.kubenexus.presentation.pods.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.hridaya.kubenexus.core.common.dispatcher.DispatcherProvider
import dev.hridaya.kubenexus.core.common.result.Result
import dev.hridaya.kubenexus.domain.model.PodDraft
import dev.hridaya.kubenexus.domain.usecase.CreateNamespaceUseCase
import dev.hridaya.kubenexus.domain.usecase.CreatePodUseCase
import dev.hridaya.kubenexus.presentation.common.NamespaceNameValidator
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Guided Pod creation. The cluster id, starting namespace and known namespaces
 * are supplied by the host ([dev.hridaya.kubenexus.presentation.main.MainScreen])
 * from the Home state at creation time, mirroring the PodDetailViewModel
 * assisted pattern.
 */
@HiltViewModel(assistedFactory = CreatePodViewModel.Factory::class)
class CreatePodViewModel @AssistedInject constructor(
    @Assisted("clusterId") private val clusterId: String?,
    @Assisted("namespace") initialNamespace: String,
    @Assisted("availableNamespaces") initialAvailableNamespaces: List<String>,
    private val createPodUseCase: CreatePodUseCase,
    private val createNamespaceUseCase: CreateNamespaceUseCase,
    private val dispatcherProvider: DispatcherProvider,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(
            @Assisted("clusterId") clusterId: String?,
            @Assisted("namespace") namespace: String,
            @Assisted("availableNamespaces") availableNamespaces: List<String>,
        ): CreatePodViewModel
    }

    private val _uiState = MutableStateFlow(
        validated(
            CreatePodUiState(
                namespace = initialNamespace.ifBlank { "default" },
                availableNamespaces = initialAvailableNamespaces
                    .filter { it.isNotBlank() && it != "All Namespaces" }
                    .distinct(),
            ),
        ),
    )
    val uiState: StateFlow<CreatePodUiState> = _uiState.asStateFlow()

    private val _effects = Channel<CreatePodUiEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    fun onAction(action: CreatePodUiAction) {
        when (action) {
            is CreatePodUiAction.NameChanged -> reduceField { it.copy(name = action.value) }
            is CreatePodUiAction.ImageChanged -> reduceField { it.copy(image = action.value) }
            is CreatePodUiAction.ContainerPortChanged -> reduceField { it.copy(containerPort = action.value) }
            is CreatePodUiAction.NamespaceSelected -> reduceField { it.copy(namespace = action.namespace) }

            is CreatePodUiAction.PreviewSubmitted -> previewYaml()

            is CreatePodUiAction.BackToFormClicked -> _uiState.update {
                it.copy(step = CreatePodStep.FORM, errorMessage = null)
            }

            is CreatePodUiAction.ApplySubmitted -> applyReviewedYaml()

            is CreatePodUiAction.ReviewedYamlChanged -> _uiState.update {
                it.copy(reviewedYaml = action.value)
            }

            is CreatePodUiAction.DismissError -> _uiState.update {
                it.copy(errorMessage = null)
            }

            is CreatePodUiAction.CreateNamespaceClicked -> _uiState.update {
                it.copy(
                    showCreateNamespaceDialog = true,
                    newNamespaceName = "",
                    newNamespaceError = null,
                )
            }

            is CreatePodUiAction.DismissCreateNamespaceClicked -> _uiState.update {
                if (it.isCreatingNamespace) {
                    it
                } else {
                    it.copy(showCreateNamespaceDialog = false, newNamespaceError = null)
                }
            }

            is CreatePodUiAction.NewNamespaceNameChanged -> _uiState.update {
                it.copy(
                    newNamespaceName = action.value,
                    newNamespaceError = NamespaceNameValidator.errorFor(action.value),
                )
            }

            is CreatePodUiAction.CreateNamespaceSubmitted -> createNamespace()

            is CreatePodUiAction.Reset -> _uiState.update { current ->
                validated(
                    CreatePodUiState(
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
    private fun reduceField(transform: (CreatePodUiState) -> CreatePodUiState) {
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
        when (val result = createPodUseCase.previewYaml(draft)) {
            is Result.Success -> _uiState.update {
                it.copy(
                    step = CreatePodStep.REVIEW,
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

        val podName = state.name

        _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
        viewModelScope.launch(dispatcherProvider.io) {
            when (val result = createPodUseCase(clusterId, state.reviewedYaml)) {
                is Result.Success -> {
                    // Reset form state so reopening the dialog starts clean without stale data
                    _uiState.update { current ->
                        validated(
                            CreatePodUiState(
                                namespace = current.namespace,
                                availableNamespaces = current.availableNamespaces,
                            ),
                        )
                    }
                    _effects.send(CreatePodUiEffect.Created(podName))
                }

                is Result.Error -> _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        errorMessage = result.error.message.ifBlank { APPLY_ERROR_MESSAGE })
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
                    _effects.send(CreatePodUiEffect.NamespaceCreated)
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

    private fun validated(state: CreatePodUiState): CreatePodUiState {
        return state.copy(
            fieldErrors = draftWithErrors(
                state,
                includeRequired = state.hasSubmitted
            ).second
        )
    }

    private fun buildDraft(state: CreatePodUiState): PodDraft {
        return draftWithErrors(state).first
    }

    /** Parses the port safely; unparseable text becomes a field error instead of a crash. */
    private fun draftWithErrors(
        state: CreatePodUiState,
        includeRequired: Boolean = state.hasSubmitted,
    ): Pair<PodDraft, Map<String, String>> {
        val parsedContainerPort = state.containerPort.trim().toIntOrNull()
        val draft = PodDraft(
            name = state.name,
            namespace = state.namespace,
            image = state.image,
            containerPort = parsedContainerPort ?: PodDraft.DEFAULT_CONTAINER_PORT,
        )
        val allErrors = buildMap {
            if (state.containerPort.isNotBlank() && parsedContainerPort == null) {
                put("containerPort", "Container port must be a whole number")
            }
            if (parsedContainerPort == 0) {
                put("containerPort", "Container port must be between 1 and 65535")
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
            "Couldn't create the pod. Please try again in a moment."
        const val NAMESPACE_ERROR_MESSAGE =
            "Couldn't create that namespace."
    }
}
