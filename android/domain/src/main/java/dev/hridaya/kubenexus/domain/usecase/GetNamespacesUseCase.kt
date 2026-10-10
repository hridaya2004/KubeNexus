package dev.hridaya.kubenexus.domain.usecase

import dev.hridaya.kubenexus.domain.repository.NamespaceRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetNamespacesUseCase @Inject constructor(private val namespaceRepository: NamespaceRepository) {
    operator fun invoke(clusterId: String?): Flow<List<String>> {
        return namespaceRepository.observeNames(clusterId)
    }
}
