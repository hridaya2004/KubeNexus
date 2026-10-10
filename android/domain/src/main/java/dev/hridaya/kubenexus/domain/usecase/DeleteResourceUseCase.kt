package dev.hridaya.kubenexus.domain.usecase

import dev.hridaya.kubenexus.core.common.result.AppError
import dev.hridaya.kubenexus.core.common.result.Result
import dev.hridaya.kubenexus.domain.model.resource.ResourceKind
import dev.hridaya.kubenexus.domain.repository.ResourceRepository
import javax.inject.Inject

/** Deletes one object from the cluster and from the cache. */
class DeleteResourceUseCase @Inject constructor(
    private val resourceRepository: ResourceRepository,
) {
    suspend operator fun invoke(
        clusterId: String?,
        kind: ResourceKind<*, *>,
        namespace: String,
        name: String,
    ): Result<Unit> {
        if (name.isBlank()) {
            return Result.Error(AppError.Validation("${kind.type.kind} name is required"))
        }
        return resourceRepository.delete(clusterId, kind, namespace, name)
    }
}
