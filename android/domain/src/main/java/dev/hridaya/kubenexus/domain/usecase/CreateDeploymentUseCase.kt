package dev.hridaya.kubenexus.domain.usecase

import dev.hridaya.kubenexus.core.common.result.AppError
import dev.hridaya.kubenexus.core.common.result.Result
import dev.hridaya.kubenexus.domain.model.DeploymentDraft
import dev.hridaya.kubenexus.domain.repository.ResourceRepository
import dev.hridaya.kubenexus.domain.util.DeploymentYamlGenerator
import javax.inject.Inject

/**
 * Guided Deployment creation (issue #5): validate the form, show the generated
 * manifest for review, then apply exactly that text.
 *
 * The two steps are separate on purpose — the apply step takes the already
 * reviewed manifest rather than the draft, so the cluster can never receive
 * anything other than what the user confirmed.
 */
class CreateDeploymentUseCase @Inject constructor(
    private val resourceRepository: ResourceRepository,
) {

    /**
     * Validates [draft] and renders it to YAML. Fails with [AppError.Validation]
     * carrying every field error when the draft is incomplete.
     */
    fun previewYaml(draft: DeploymentDraft): Result<String> {
        val errors = draft.validate()
        if (errors.isNotEmpty()) {
            val summary = errors.entries.joinToString("; ") { "${it.key}: ${it.value}" }
            return Result.Error(AppError.Validation(summary))
        }
        return Result.Success(DeploymentYamlGenerator.generate(draft))
    }

    /** Applies the reviewed [manifestYaml]. */
    suspend operator fun invoke(clusterId: String?, manifestYaml: String): Result<Unit> {
        return resourceRepository.create(clusterId, manifestYaml)
    }
}
