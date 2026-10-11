package dev.hridaya.kubenexus.data.portforward

import dev.hridaya.kubenexus.core.common.result.AppError
import dev.hridaya.kubenexus.core.common.result.Result
import dev.hridaya.kubenexus.domain.model.ActivePortForwardSession
import dev.hridaya.kubenexus.domain.repository.ClusterRepository
import dev.hridaya.kubenexus.domain.repository.PortForwardSessionRepository
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

class PortForwardSessionRepositoryImpl @Inject constructor(
    private val sessionManager: PortForwardSessionManager,
    private val clusterRepository: ClusterRepository,
) : PortForwardSessionRepository {

    override val sessions: StateFlow<List<ActivePortForwardSession>> = sessionManager.sessions

    override suspend fun startPodForward(
        clusterId: String,
        namespace: String,
        podName: String,
        localPort: Int,
        remotePort: Int,
    ): Result<String> {
        val kubeconfig = kubeconfigOf(clusterId) ?: return noCluster()
        return sessionManager.startPodForward(kubeconfig, namespace, podName, localPort, remotePort, clusterId)
    }

    override suspend fun startServiceForward(
        clusterId: String,
        namespace: String,
        serviceName: String,
        localPort: Int,
        servicePort: Int,
        targetPodName: String,
        targetPodPort: Int,
    ): Result<String> {
        val kubeconfig = kubeconfigOf(clusterId) ?: return noCluster()
        return sessionManager.startServiceForward(
            rawKubeconfig = kubeconfig,
            namespace = namespace,
            serviceName = serviceName,
            localPort = localPort,
            servicePort = servicePort,
            targetPodName = targetPodName,
            targetPodPort = targetPodPort,
            clusterId = clusterId,
        )
    }

    override suspend fun stop(handleId: String): Result<Unit> = sessionManager.stop(handleId)

    private suspend fun kubeconfigOf(clusterId: String): String? =
        clusterRepository.getClusterById(clusterId)?.rawKubeconfig?.takeIf { it.isNotBlank() }

    private fun noCluster(): Result<String> =
        Result.Error(AppError.Validation("No active Kubernetes cluster configured."))
}
