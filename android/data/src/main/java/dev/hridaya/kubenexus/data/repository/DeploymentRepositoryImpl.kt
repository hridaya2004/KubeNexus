package dev.hridaya.kubenexus.data.repository

import dev.hridaya.kubenexus.core.common.result.Result
import dev.hridaya.kubenexus.data.nativebridge.KubeNexusNativeBridge
import dev.hridaya.kubenexus.domain.repository.DeploymentRepository
import javax.inject.Inject

class DeploymentRepositoryImpl @Inject constructor(
    private val clusterCalls: ClusterCallRunner,
    private val nativeBridge: KubeNexusNativeBridge,
) : DeploymentRepository {

    override suspend fun scaleDeployment(
        clusterId: String?,
        namespace: String,
        name: String,
        replicas: Int,
    ): Result<Unit> =
        clusterCalls.run(clusterId, "Failed to scale deployment '$name'") { cluster ->
            nativeBridge.scaleDeployment(cluster.kubeconfig, namespace, name, replicas)
        }

    override suspend fun restartDeployment(
        clusterId: String?,
        namespace: String,
        name: String,
    ): Result<Unit> =
        clusterCalls.run(clusterId, "Failed to restart deployment '$name'") { cluster ->
            nativeBridge.restartDeployment(cluster.kubeconfig, namespace, name)
        }
}
