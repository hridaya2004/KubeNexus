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

    val decrypted = encryptor.decryptOrEmpty(rawKubeconfig)
    return Cluster(
        id = id,
        name = name,
        serverUrl = serverUrl,
        rawKubeconfig = decrypted,
        credentialsUnavailable = rawKubeconfig.isNotBlank() && decrypted.isBlank(),
        contextName = contextName,
        userName = userName,
        namespace = namespace,
        isActive = isActive,
        createdAt = createdAt,
        lastConnectedAt = lastConnectedAt,
        status = clusterStatus,
    )
}

fun Cluster.toEntity(encryptor: KubeconfigEncryptor = NoOpKubeconfigEncryptor): ClusterEntity {
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
