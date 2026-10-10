package dev.hridaya.kubenexus.domain.usecase

import dev.hridaya.kubenexus.core.common.result.Result
import dev.hridaya.kubenexus.domain.model.resource.NamespaceScope
import dev.hridaya.kubenexus.domain.model.resource.ResourceKinds
import dev.hridaya.kubenexus.domain.repository.NamespaceRepository
import dev.hridaya.kubenexus.domain.repository.ResourceRepository
import javax.inject.Inject

/**
 * Refreshes the cluster overview shown on Home: the namespace list every filter
 * offers, then the pods in [namespace].
 *
 * Namespaces are best-effort. The result is the pod sync's, because a cluster
 * that answers for pods but not namespaces (a namespaced role, say) is still
 * usable.
 */
class RefreshWorkloadsUseCase @Inject constructor(
    private val namespaceRepository: NamespaceRepository,
    private val resourceRepository: ResourceRepository,
) {
    suspend operator fun invoke(clusterId: String?, namespace: String? = null): Result<Unit> {
        namespaceRepository.sync(clusterId)
        return resourceRepository.sync(clusterId, ResourceKinds.Pods, NamespaceScope.of(namespace))
    }
}
