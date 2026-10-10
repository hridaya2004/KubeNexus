package dev.hridaya.kubenexus.domain.usecase

import dev.hridaya.kubenexus.domain.model.resource.KubeObjectSummary
import dev.hridaya.kubenexus.domain.model.resource.NamespaceScope
import dev.hridaya.kubenexus.domain.model.resource.ResourceKind
import dev.hridaya.kubenexus.domain.repository.ResourceRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Observes the offline-first list of one resource kind. Emits from the cache
 * immediately and again after every [SyncResourcesUseCase] run, so callers never
 * block on the network to render.
 */
class ObserveResourcesUseCase @Inject constructor(
    private val resourceRepository: ResourceRepository,
) {
    operator fun <S : KubeObjectSummary> invoke(
        clusterId: String?,
        kind: ResourceKind<S, *>,
        scope: NamespaceScope,
    ): Flow<List<S>> = resourceRepository.observe(clusterId, kind, scope)
}
