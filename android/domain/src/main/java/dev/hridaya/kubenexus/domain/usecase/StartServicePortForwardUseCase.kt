package dev.hridaya.kubenexus.domain.usecase

import dev.hridaya.kubenexus.core.common.result.Result
import dev.hridaya.kubenexus.domain.model.ServiceForwardTarget
import dev.hridaya.kubenexus.domain.repository.PortForwardSessionRepository
import javax.inject.Inject

/** Opens 127.0.0.1:localPort to a service port through the pod [target] resolved for it. */
class StartServicePortForwardUseCase @Inject constructor(
    private val repository: PortForwardSessionRepository,
) {
    suspend operator fun invoke(
        clusterId: String,
        namespace: String,
        serviceName: String,
        localPort: Int,
        servicePort: Int,
        target: ServiceForwardTarget,
    ): Result<String> = repository.startServiceForward(
        clusterId = clusterId,
        namespace = namespace,
        serviceName = serviceName,
        localPort = localPort,
        servicePort = servicePort,
        targetPodName = target.podName,
        targetPodPort = target.podPort,
    )
}
