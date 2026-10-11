package dev.hridaya.kubenexus.domain.usecase

import dev.hridaya.kubenexus.core.common.result.Result
import dev.hridaya.kubenexus.domain.repository.PortForwardSessionRepository
import javax.inject.Inject

class StopPortForwardUseCase @Inject constructor(
    private val repository: PortForwardSessionRepository,
) {
    suspend operator fun invoke(handleId: String): Result<Unit> = repository.stop(handleId)
}
