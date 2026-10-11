package dev.hridaya.kubenexus.domain.usecase

import dev.hridaya.kubenexus.core.common.result.Result
import dev.hridaya.kubenexus.domain.repository.PortForwardSessionRepository
import javax.inject.Inject

/** Opens 127.0.0.1:localPort -> pod:remotePort and returns the forward's handle. */
class StartPodPortForwardUseCase @Inject constructor(
    private val repository: PortForwardSessionRepository,
) {
    suspend operator fun invoke(
        clusterId: String,
        namespace: String,
        podName: String,
        localPort: Int,
        remotePort: Int,
    ): Result<String> = repository.startPodForward(clusterId, namespace, podName, localPort, remotePort)
}
