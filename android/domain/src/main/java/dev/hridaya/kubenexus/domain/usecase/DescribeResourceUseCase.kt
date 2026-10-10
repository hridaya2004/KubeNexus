package dev.hridaya.kubenexus.domain.usecase

import dev.hridaya.kubenexus.core.common.result.Result
import dev.hridaya.kubenexus.domain.model.resource.ResourceKind
import dev.hridaya.kubenexus.domain.repository.ResourceRepository
import javax.inject.Inject

/** Describes one object, including its best-effort events. */
class DescribeResourceUseCase @Inject constructor(
    private val resourceRepository: ResourceRepository,
) {
    suspend operator fun <D : Any> invoke(
        clusterId: String?,
        kind: ResourceKind<*, D>,
        namespace: String,
        name: String,
    ): Result<D> = resourceRepository.describe(clusterId, kind, namespace, name)
}
