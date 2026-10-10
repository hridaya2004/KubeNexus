package dev.hridaya.kubenexus.domain.usecase

import dev.hridaya.kubenexus.core.common.result.Result
import dev.hridaya.kubenexus.domain.model.resource.NamespaceScope
import dev.hridaya.kubenexus.domain.model.resource.ResourceKind
import dev.hridaya.kubenexus.domain.repository.ResourceRepository
import javax.inject.Inject

/** Pulls the live list of one resource kind into the cache read by [ObserveResourcesUseCase]. */
class SyncResourcesUseCase @Inject constructor(
    private val resourceRepository: ResourceRepository,
) {
    suspend operator fun invoke(
        clusterId: String?,
        kind: ResourceKind<*, *>,
        scope: NamespaceScope,
    ): Result<Unit> = resourceRepository.sync(clusterId, kind, scope)
}
