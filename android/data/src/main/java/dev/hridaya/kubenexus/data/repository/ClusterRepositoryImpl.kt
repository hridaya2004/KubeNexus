package dev.hridaya.kubenexus.data.repository

import dev.hridaya.kubenexus.core.common.dispatcher.DispatcherProvider
import dev.hridaya.kubenexus.core.common.result.AppError
import dev.hridaya.kubenexus.core.common.result.Result
import dev.hridaya.kubenexus.core.security.KubeconfigEncryptor
import dev.hridaya.kubenexus.core.security.LogSanitizer
import dev.hridaya.kubenexus.core.security.NoOpKubeconfigEncryptor
import dev.hridaya.kubenexus.data.kubeconfig.ClusterConnectionTester
import dev.hridaya.kubenexus.data.kubeconfig.KubeconfigParser
import dev.hridaya.kubenexus.data.mapper.toDomain
import dev.hridaya.kubenexus.data.mapper.toEntity
import dev.hridaya.kubenexus.data.nativebridge.KubeNexusNativeBridge
import dev.hridaya.kubenexus.data.source.local.dao.ClusterDao
import dev.hridaya.kubenexus.domain.model.Cluster
import dev.hridaya.kubenexus.domain.model.ClusterHealth
import dev.hridaya.kubenexus.domain.model.ClusterStatus
import dev.hridaya.kubenexus.domain.repository.ClusterRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

class ClusterRepositoryImpl @Inject constructor(
    private val clusterDao: ClusterDao,
    private val connectionTester: ClusterConnectionTester,
    private val nativeBridge: KubeNexusNativeBridge,
    private val encryptor: KubeconfigEncryptor = NoOpKubeconfigEncryptor,
    private val dispatcherProvider: DispatcherProvider,
) : ClusterRepository {

    override fun getClustersStream(): Flow<List<Cluster>> {
        return clusterDao.observeClusters()
            .map { list -> list.map { it.toDomain(encryptor) } }
            .flowOn(dispatcherProvider.io)
    }

    override fun getActiveClusterStream(): Flow<Cluster?> {
        return clusterDao.observeActiveCluster()
            .map { it?.toDomain(encryptor) }
            .flowOn(dispatcherProvider.io)
    }

    override suspend fun getClusterById(id: String): Cluster? = withContext(dispatcherProvider.io) {
        clusterDao.getClusterById(id)?.toDomain(encryptor)
    }

    override suspend fun addCluster(
        kubeconfigRaw: String,
        customName: String?,
        setAsActive: Boolean
    ): Result<Cluster> = withContext(dispatcherProvider.io) {
        try {
            val parsed = KubeconfigParser.parse(kubeconfigRaw, customName)
            connectionTester.testConnection(parsed)

            val clusterId = UUID.randomUUID().toString()
            val newCluster = Cluster(
                id = clusterId,
                name = parsed.clusterName,
                serverUrl = parsed.serverUrl,
                rawKubeconfig = parsed.rawKubeconfig,
                contextName = parsed.contextName,
                userName = parsed.userName,
                namespace = parsed.namespace,
                isActive = setAsActive,
                createdAt = System.currentTimeMillis(),
                lastConnectedAt = System.currentTimeMillis(),
                status = ClusterStatus.CONNECTED,
            )

            if (setAsActive) {
                clusterDao.deactivateAllClusters()
            }
            clusterDao.insertCluster(newCluster.toEntity(encryptor))

            Result.Success(newCluster)
        } catch (t: Throwable) {
            val sanitizedMsg = LogSanitizer.sanitize(t.message)
            val errorMsg = sanitizedMsg.ifEmpty { "Failed to add cluster due to unknown error." }
            Result.Error(AppError.Unknown(errorMsg, t))
        }
    }

    override suspend fun setActiveCluster(id: String): Result<Unit> =
        withContext(dispatcherProvider.io) {
            try {
                val clusterEntity = clusterDao.getClusterById(id)
                    ?: return@withContext Result.Error(AppError.Unknown("Cluster with ID '$id' not found."))

                val decryptedKubeconfig = encryptor.decryptOrEmpty(clusterEntity.rawKubeconfig)
                val parsed = KubeconfigParser.parse(decryptedKubeconfig, clusterEntity.name)
                connectionTester.testConnection(parsed)

                // Opportunistic migration if this record was still stored as plaintext
                if (!encryptor.isEncrypted(clusterEntity.rawKubeconfig)) {
                    val encrypted = encryptor.encrypt(clusterEntity.rawKubeconfig)
                    clusterDao.updateCluster(clusterEntity.copy(rawKubeconfig = encrypted))
                }

                clusterDao.setActiveCluster(id)
                clusterDao.updateStatus(
                    id,
                    ClusterStatus.CONNECTED.name,
                    System.currentTimeMillis(),
                )

                Result.Success(Unit)
            } catch (t: Throwable) {
                clusterDao.updateStatus(id, ClusterStatus.ERROR.name, null)
                val sanitizedMsg = LogSanitizer.sanitize(t.message)
                val errorMsg = sanitizedMsg.ifEmpty { "Failed to switch active cluster." }
                Result.Error(AppError.Unknown(errorMsg, t))
            }
        }

    override suspend fun deleteCluster(id: String): Result<Unit> =
        withContext(dispatcherProvider.io) {
            try {
                // Removes the cached workloads, namespaces, discovery, explains and
                // OpenAPI schema blob along with the cluster row.
                clusterDao.deleteClusterWithCachedData(id)
                Result.Success(Unit)
            } catch (t: Throwable) {
                val sanitizedMsg = LogSanitizer.sanitize(t.message)
                Result.Error(AppError.Unknown("Failed to delete cluster: $sanitizedMsg", t))
            }
        }

    override suspend fun clearCachedData(): Result<Unit> =
        withContext(dispatcherProvider.io) {
            try {
                clusterDao.clearCachedData()
                Result.Success(Unit)
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                val sanitizedMsg = LogSanitizer.sanitize(t.message)
                Result.Error(AppError.Database("Failed to clear cached data: $sanitizedMsg"))
            }
        }

    override suspend fun testConnection(kubeconfigRaw: String): Result<String> =
        withContext(dispatcherProvider.io) {
            try {
                val parsed = KubeconfigParser.parse(kubeconfigRaw)
                val info = connectionTester.testConnection(parsed)
                Result.Success(info)
            } catch (t: Throwable) {
                val sanitizedMsg = LogSanitizer.sanitize(t.message)
                val errorMsg = sanitizedMsg.ifEmpty { "Connection test failed." }
                Result.Error(AppError.Unknown(errorMsg, t))
            }
        }

    override suspend fun testClusterById(id: String): Result<String> =
        withContext(dispatcherProvider.io) {
            try {
                val clusterEntity = clusterDao.getClusterById(id)
                    ?: return@withContext Result.Error(AppError.Unknown("Cluster with ID '$id' not found."))
                val decryptedKubeconfig = encryptor.decryptOrEmpty(clusterEntity.rawKubeconfig)
                val parsed = KubeconfigParser.parse(decryptedKubeconfig, clusterEntity.name)
                val info = connectionTester.testConnection(parsed)
                clusterDao.updateStatus(
                    id,
                    ClusterStatus.CONNECTED.name,
                    System.currentTimeMillis(),
                )
                Result.Success(info)
            } catch (t: Throwable) {
                clusterDao.updateStatus(id, ClusterStatus.ERROR.name, null)
                val sanitizedMsg = LogSanitizer.sanitize(t.message)
                val errorMsg = sanitizedMsg.ifEmpty { "Connection test failed." }
                Result.Error(AppError.Unknown(errorMsg, t))
            }
        }

    override suspend fun checkClusterHealth(id: String): Result<ClusterHealth> =
        withContext(dispatcherProvider.io) {
            try {
                val clusterEntity = clusterDao.getClusterById(id)
                    ?: return@withContext Result.Error(AppError.Unknown("Cluster with ID '$id' not found."))
                val decryptedKubeconfig = encryptor.decryptOrEmpty(clusterEntity.rawKubeconfig)
                nativeBridge.checkHealth(decryptedKubeconfig)
            } catch (t: Throwable) {
                val sanitizedMsg = LogSanitizer.sanitize(t.message)
                val errorMsg = sanitizedMsg.ifEmpty { "Health check failed." }
                Result.Error(AppError.Unknown(errorMsg, t))
            }
        }

    override suspend fun checkClusterHealthByKubeconfig(kubeconfigRaw: String): Result<ClusterHealth> =
        withContext(dispatcherProvider.io) {
            try {
                nativeBridge.checkHealth(kubeconfigRaw)
            } catch (t: Throwable) {
                val sanitizedMsg = LogSanitizer.sanitize(t.message)
                val errorMsg = sanitizedMsg.ifEmpty { "Health check failed." }
                Result.Error(AppError.Unknown(errorMsg, t))
            }
        }

    override suspend fun updateClusterName(id: String, newName: String): Result<Unit> =
        withContext(dispatcherProvider.io) {
            try {
                require(newName.isNotBlank()) { "Cluster name cannot be blank." }
                clusterDao.updateClusterName(id, newName.trim())
                Result.Success(Unit)
            } catch (t: Throwable) {
                val sanitizedMsg = LogSanitizer.sanitize(t.message)
                Result.Error(AppError.Unknown("Failed to update cluster name: $sanitizedMsg", t))
            }
        }

    override suspend fun updateClusterStatus(
        id: String,
        status: ClusterStatus,
        lastConnectedAt: Long?
    ): Result<Unit> = withContext(dispatcherProvider.io) {
        try {
            clusterDao.updateStatus(id, status.name, lastConnectedAt)
            Result.Success(Unit)
        } catch (t: Throwable) {
            val sanitizedMsg = LogSanitizer.sanitize(t.message)
            Result.Error(AppError.Unknown("Failed to update status: $sanitizedMsg", t))
        }
    }

    override suspend fun migratePlaintextClusters(): Result<Int> =
        withContext(dispatcherProvider.io) {
            try {
                val clusters = clusterDao.getAllClusters()
                var migratedCount = 0
                for (entity in clusters) {
                    if (!encryptor.isEncrypted(entity.rawKubeconfig)) {
                        val encryptedKubeconfig = encryptor.encrypt(entity.rawKubeconfig)
                        clusterDao.updateCluster(entity.copy(rawKubeconfig = encryptedKubeconfig))
                        migratedCount++
                    }
                }
                Result.Success(migratedCount)
            } catch (t: Throwable) {
                val sanitizedMsg = LogSanitizer.sanitize(t.message)
                Result.Error(
                    AppError.Unknown(
                        "Failed to migrate plaintext clusters: $sanitizedMsg",
                        t,
                    ),
                )
            }
        }
}
