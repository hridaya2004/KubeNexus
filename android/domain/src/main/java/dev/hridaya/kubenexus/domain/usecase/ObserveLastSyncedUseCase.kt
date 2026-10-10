package dev.hridaya.kubenexus.domain.usecase

import dev.hridaya.kubenexus.domain.model.resource.ResourceKind
import dev.hridaya.kubenexus.domain.repository.ResourceRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Observes when one resource kind was last synced on a cluster, in epoch millis. */
class ObserveLastSyncedUseCase @Inject constructor(
    private val resourceRepository: ResourceRepository,
) {
    operator fun invoke(clusterId: String?, kind: ResourceKind<*, *>): Flow<Long?> =
        resourceRepository.observeLastSynced(clusterId, kind)
}
