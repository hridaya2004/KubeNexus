package dev.hridaya.kubenexus.domain.usecase

import dev.hridaya.kubenexus.core.common.result.Result
import dev.hridaya.kubenexus.domain.repository.NamespaceRepository
import javax.inject.Inject

/** Creates an empty namespace on the active cluster. */
class CreateNamespaceUseCase @Inject constructor(
    private val namespaceRepository: NamespaceRepository,
) {

    suspend operator fun invoke(clusterId: String?, name: String): Result<Unit> {
        return namespaceRepository.create(clusterId, name)
    }
}
