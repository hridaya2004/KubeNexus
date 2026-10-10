package dev.hridaya.kubenexus.domain.usecase

import dev.hridaya.kubenexus.core.common.result.Result
import dev.hridaya.kubenexus.core.common.result.onSuccess
import dev.hridaya.kubenexus.domain.model.resource.NamespaceScope
import dev.hridaya.kubenexus.domain.model.resource.ResourceKinds
import dev.hridaya.kubenexus.domain.repository.DeploymentRepository
import dev.hridaya.kubenexus.domain.repository.ResourceRepository
import javax.inject.Inject

/**
 * Starts a rolling restart of a Deployment, then refreshes the cached
 * Deployments of its namespace. A failed refresh does not fail the restart.
 */
class RestartDeploymentUseCase @Inject constructor(
    private val deploymentRepository: DeploymentRepository,
    private val resourceRepository: ResourceRepository,
) {
    suspend operator fun invoke(
        clusterId: String?,
        namespace: String,
        name: String,
    ): Result<Unit> =
        deploymentRepository.restartDeployment(clusterId, namespace, name).onSuccess {
            resourceRepository.sync(clusterId, ResourceKinds.Deployments, NamespaceScope.Only(namespace))
        }
}
