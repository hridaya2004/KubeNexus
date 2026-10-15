package dev.hridaya.kubenexus.domain.usecase

import dev.hridaya.kubenexus.core.common.result.AppError
import dev.hridaya.kubenexus.core.common.result.Result
import dev.hridaya.kubenexus.core.common.result.onSuccess
import dev.hridaya.kubenexus.domain.model.resource.NamespaceScope
import dev.hridaya.kubenexus.domain.model.resource.ResourceKinds
import dev.hridaya.kubenexus.domain.repository.DeploymentRepository
import dev.hridaya.kubenexus.domain.repository.ResourceRepository
import javax.inject.Inject

/**
 * Scales a Deployment, then refreshes the cached Deployments of its namespace
 * so lists show the new replica count. A failed refresh does not fail the
 * scale; the list just stays stale until its next sync.
 */
class ScaleDeploymentUseCase @Inject constructor(
    private val deploymentRepository: DeploymentRepository,
    private val resourceRepository: ResourceRepository,
) {
    suspend operator fun invoke(
        clusterId: String?,
        namespace: String,
        name: String,
        replicas: Int,
    ): Result<Unit> {
        if (replicas < 0) {
            return Result.Error(AppError.Validation("Replica count cannot be negative"))
        }
        return deploymentRepository.scaleDeployment(clusterId, namespace, name, replicas)
            .onSuccess {
                resourceRepository.sync(
                    clusterId,
                    ResourceKinds.Deployments,
                    NamespaceScope.Only(namespace)
                )
            }
    }
}
