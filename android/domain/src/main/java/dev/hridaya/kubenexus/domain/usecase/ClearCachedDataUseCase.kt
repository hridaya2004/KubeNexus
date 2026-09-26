package dev.hridaya.kubenexus.domain.usecase

import dev.hridaya.kubenexus.core.common.result.Result
import dev.hridaya.kubenexus.domain.repository.ClusterRepository
import javax.inject.Inject

/** Deletes cached cluster data (workloads, discovery, schemas) while keeping clusters and credentials. */
class ClearCachedDataUseCase @Inject constructor(
    private val repository: ClusterRepository,
) {
    suspend operator fun invoke(): Result<Unit> = repository.clearCachedData()
}
