package dev.hridaya.kubenexus.data.mapper

import dev.hridaya.kubenexus.core.security.KubeconfigEncryptor
import dev.hridaya.kubenexus.core.security.NoOpKubeconfigEncryptor
import dev.hridaya.kubenexus.data.source.local.entity.ClusterEntity
import dev.hridaya.kubenexus.domain.model.Cluster
import dev.hridaya.kubenexus.domain.model.ClusterStatus

fun ClusterEntity.toDomain(encryptor: KubeconfigEncryptor = NoOpKubeconfigEncryptor): Cluster {
    val clusterStatus = try {
        ClusterStatus.valueOf(status)
    } catch (_: Exception) {
        ClusterStatus.DISCONNECTED
    }

    return Cluster(
        id = id,
        name = name,
        serverUrl = serverUrl,
        credentialsUnavailable = rawKubeconfig.isNotBlank() && encryptor.decryptOrEmpty(rawKubeconfig).isBlank(),
        contextName = contextName,
        userName = userName,
        namespace = namespace,
        isActive = isActive,
        createdAt = createdAt,
        lastConnectedAt = lastConnectedAt,
        status = clusterStatus,
    )
}

/** The kubeconfig is not part of the domain model, so it is passed in to be stored encrypted. */
fun Cluster.toEntity(rawKubeconfig: String, encryptor: KubeconfigEncryptor = NoOpKubeconfigEncryptor): ClusterEntity {
    return ClusterEntity(
        id = id,
        name = name,
        serverUrl = serverUrl,
        rawKubeconfig = encryptor.encrypt(rawKubeconfig),
        contextName = contextName,
        userName = userName,
        namespace = namespace,
        isActive = isActive,
        createdAt = createdAt,
        lastConnectedAt = lastConnectedAt,
        status = status.name,
    )
}
