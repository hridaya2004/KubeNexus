package dev.hridaya.kubenexus.domain.usecase

import dev.hridaya.kubenexus.core.common.result.Result
import dev.hridaya.kubenexus.domain.repository.NamespaceRepository
import javax.inject.Inject

class DeleteNamespaceUseCase @Inject constructor(private val namespaceRepository: NamespaceRepository) {
    suspend operator fun invoke(
        clusterId: String?,
        namespace: String,
    ): Result<Unit> {
        return namespaceRepository.delete(clusterId, namespace)
    }
}
