package dev.hridaya.kubenexus.presentation.pods.create

enum class CreatePodStep {
    FORM,
    REVIEW,
}

data class CreatePodUiState(
    val name: String = "",
    val namespace: String = "",
    val image: String = "",
    /** Blank means no port. */
    val containerPort: String = "",
    val fieldErrors: Map<String, String> = emptyMap(),
    val availableNamespaces: List<String> = emptyList(),
    val showCreateNamespaceDialog: Boolean = false,
    val newNamespaceName: String = "",
    val newNamespaceError: String? = null,
    val isCreatingNamespace: Boolean = false,
    val step: CreatePodStep = CreatePodStep.FORM,
    val generatedYaml: String? = null,

    /** Exactly what the editor shows; Apply sends this text verbatim. */
    val reviewedYaml: String = "",
    val isSubmitting: Boolean = false,
    val hasSubmitted: Boolean = false,
    val errorMessage: String? = null,
)

sealed interface CreatePodUiAction {
    data class NameChanged(val value: String) : CreatePodUiAction
    data class ImageChanged(val value: String) : CreatePodUiAction
    data class ContainerPortChanged(val value: String) : CreatePodUiAction
    data class NamespaceSelected(val namespace: String) : CreatePodUiAction
    data object PreviewSubmitted : CreatePodUiAction
    data object BackToFormClicked : CreatePodUiAction
    data object ApplySubmitted : CreatePodUiAction
    data class ReviewedYamlChanged(val value: String) : CreatePodUiAction
    data object DismissError : CreatePodUiAction
    data object CreateNamespaceClicked : CreatePodUiAction
    data object DismissCreateNamespaceClicked : CreatePodUiAction
    data class NewNamespaceNameChanged(val value: String) : CreatePodUiAction
    data object CreateNamespaceSubmitted : CreatePodUiAction
    data object Reset : CreatePodUiAction
}

sealed interface CreatePodUiEffect {
    data class Created(val podName: String) : CreatePodUiEffect
    data object NamespaceCreated : CreatePodUiEffect
}
