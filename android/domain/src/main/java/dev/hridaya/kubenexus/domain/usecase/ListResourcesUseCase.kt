package dev.hridaya.kubenexus.domain.usecase

import dev.hridaya.kubenexus.core.common.result.Result
import dev.hridaya.kubenexus.domain.model.resource.KubeObjectSummary
import dev.hridaya.kubenexus.domain.model.resource.NamespaceScope
import dev.hridaya.kubenexus.domain.model.resource.ResourceKind
import dev.hridaya.kubenexus.domain.repository.ResourceRepository
import javax.inject.Inject

/**
 * Lists live objects of one kind, bypassing the cache. [matchLabels] narrows the
 * list the way a Deployment or Service selects its pods.
 */
class ListResourcesUseCase @Inject constructor(
    private val resourceRepository: ResourceRepository,
) {
    suspend operator fun <S : KubeObjectSummary> invoke(
        clusterId: String?,
        kind: ResourceKind<S, *>,
        scope: NamespaceScope,
        matchLabels: Map<String, String> = emptyMap(),
    ): Result<List<S>> =
        resourceRepository.list(
            clusterId,
            kind,
            scope,
            labelSelector = matchLabels.toLabelSelector()
        )

    private fun Map<String, String>.toLabelSelector(): String =
        entries.joinToString(separator = ",") { (key, value) -> "$key=$value" }
}
