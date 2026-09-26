package dev.hridaya.kubenexus.data.repository

import android.util.Log
import dev.hridaya.kubenexus.core.common.dispatcher.DispatcherProvider
import dev.hridaya.kubenexus.core.common.result.AppError
import dev.hridaya.kubenexus.core.common.result.Result
import dev.hridaya.kubenexus.core.security.KubeconfigEncryptor
import dev.hridaya.kubenexus.core.security.LogSanitizer
import dev.hridaya.kubenexus.core.security.NoOpKubeconfigEncryptor
import dev.hridaya.kubenexus.data.mapper.toDomain
import dev.hridaya.kubenexus.data.mapper.toEntity
import dev.hridaya.kubenexus.data.nativebridge.KubeNexusNativeBridge
import dev.hridaya.kubenexus.data.source.local.dao.ClusterDao
import dev.hridaya.kubenexus.data.source.local.dao.DeploymentDao
import dev.hridaya.kubenexus.domain.model.DeploymentDetails
import dev.hridaya.kubenexus.domain.model.DeploymentSummary
import dev.hridaya.kubenexus.domain.repository.DeploymentRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

class DeploymentRepositoryImpl @Inject constructor(
    private val clusterDao: ClusterDao,
    private val deploymentDao: DeploymentDao,
    private val nativeBridge: KubeNexusNativeBridge,
    private val encryptor: KubeconfigEncryptor = NoOpKubeconfigEncryptor,
    private val dispatcherProvider: DispatcherProvider,
) : DeploymentRepository {

    companion object {
        private const val TAG = "DeploymentRepositoryImpl"
    }

    override suspend fun createFromManifest(
        clusterId: String?,
        manifestYaml: String,
    ): Result<Unit> = withContext(dispatcherProvider.io) {
        if (clusterId == null) return@withContext Result.Error(AppError.NotFound("No cluster selected"))
        val cluster = clusterDao.getClusterById(clusterId)
            ?: return@withContext Result.Error(AppError.NotFound("Cluster '$clusterId' not found"))

        val decryptedKubeconfig = encryptor.decryptOrEmpty(cluster.rawKubeconfig)

        try {
            val documents = splitYamlDocuments(manifestYaml)
            for (doc in documents) {
                if (doc.isBlank()) continue
                val kind = extractKindFromYaml(doc)
                val nativeResult = when (kind?.lowercase()) {
                    "service" -> nativeBridge.createService(decryptedKubeconfig, "", doc)
                    "pod" -> nativeBridge.createPod(decryptedKubeconfig, "", doc)
                    else -> nativeBridge.createDeployment(decryptedKubeconfig, "", doc)
                }
                if (nativeResult.isFailure) {
                    val error = nativeResult.exceptionOrNull()
                    val sanitizedMsg = LogSanitizer.sanitize(error?.message)
                    Log.e(TAG, "Failed to create resource ($kind) for cluster '$clusterId': $sanitizedMsg")
                    return@withContext Result.Error(AppError.Network(sanitizedMsg.ifEmpty { "Failed to create resource" }))
                }
            }
            Result.Success(Unit)
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            val sanitizedMsg = LogSanitizer.sanitize(t.message)
            Log.e(TAG, LogSanitizer.withStackTrace("Failed to create deployment for cluster '$clusterId': $sanitizedMsg", t))
            Result.Error(AppError.Network(sanitizedMsg.ifEmpty { "Failed to create deployment" }))
        }
    }

    override suspend fun getDeployments(
        clusterId: String?,
        namespace: String?,
    ): Result<List<DeploymentSummary>> = withContext(dispatcherProvider.io) {
        if (clusterId == null) return@withContext Result.Error(AppError.NotFound("No cluster selected"))
        val cluster = clusterDao.getClusterById(clusterId)
            ?: return@withContext Result.Error(AppError.NotFound("Cluster '$clusterId' not found"))

        val decryptedKubeconfig = encryptor.decryptOrEmpty(cluster.rawKubeconfig)

        try {
            val nativeResult = nativeBridge.listDeployments(decryptedKubeconfig, namespace)
            if (nativeResult.isSuccess) {
                Result.Success(nativeResult.getOrThrow())
            } else {
                val ex = nativeResult.exceptionOrNull()
                val sanitizedMsg = LogSanitizer.sanitize(ex?.message)
                Result.Error(AppError.Network(sanitizedMsg.ifEmpty { "Failed to load deployments" }))
            }
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            val sanitizedMsg = LogSanitizer.sanitize(t.message)
            Log.e(TAG, LogSanitizer.withStackTrace("Failed to load deployments for cluster '$clusterId': $sanitizedMsg", t))
            Result.Error(AppError.Network(sanitizedMsg.ifEmpty { "Failed to load deployments" }))
        }
    }

    override fun getDeploymentsStream(
        clusterId: String?,
        namespace: String?,
    ): Flow<List<DeploymentSummary>> {
        if (clusterId == null) return flowOf(emptyList())

        val stream =
            if (isAllNamespaces(namespace)) {
                deploymentDao.getDeploymentsStream(clusterId)
            } else {
                deploymentDao.getDeploymentsByNamespaceStream(clusterId, namespace!!.trim())
            }

        return stream.map { entities ->
            entities.map { entity -> entity.toDomain() }
        }.flowOn(dispatcherProvider.io)
    }

    override suspend fun syncDeployments(
        clusterId: String?,
        namespace: String?,
    ): Result<Unit> = withContext(dispatcherProvider.io) {
        if (clusterId == null) {
            return@withContext Result.Error(AppError.NotFound("No active cluster specified"))
        }

        val cluster = clusterDao.getClusterById(clusterId)
            ?: return@withContext Result.Error(AppError.NotFound("Cluster with ID '$clusterId' not found"))

        val decryptedKubeconfig = encryptor.decryptOrEmpty(cluster.rawKubeconfig)
        val queryNamespace = normalizedNamespaceOrNull(namespace)

        try {
            val nativeResult = nativeBridge.listDeployments(decryptedKubeconfig, queryNamespace)
            if (nativeResult.isFailure) {
                val ex = nativeResult.exceptionOrNull()
                val sanitizedMsg = LogSanitizer.sanitize(ex?.message)
                return@withContext Result.Error(
                    AppError.Network(sanitizedMsg.ifEmpty { "Failed to list deployments from cluster" }),
                )
            }
            val liveDeployments: List<DeploymentSummary> = nativeResult.getOrThrow()

            deploymentDao.syncDeployments(
                clusterId = clusterId,
                namespace = queryNamespace,
                deployments = liveDeployments.map { it.toEntity(clusterId) },
                timestamp = System.currentTimeMillis(),
            )

            Result.Success(Unit)
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            val sanitizedMsg = LogSanitizer.sanitize(t.message)
            Log.e(TAG, LogSanitizer.withStackTrace("Failed to sync deployments for cluster '$clusterId': $sanitizedMsg", t))
            Result.Error(AppError.Network(sanitizedMsg.ifEmpty { "Failed to connect to cluster API" }))
        }
    }

    override suspend fun getDeploymentDetails(
        clusterId: String?,
        namespace: String,
        name: String,
    ): Result<DeploymentDetails> = withContext(dispatcherProvider.io) {
        if (clusterId == null) return@withContext Result.Error(AppError.NotFound("No cluster selected"))
        val cluster = clusterDao.getClusterById(clusterId)
            ?: return@withContext Result.Error(AppError.NotFound("Cluster '$clusterId' not found"))

        val decryptedKubeconfig = encryptor.decryptOrEmpty(cluster.rawKubeconfig)

        try {
            // The bridge assembles details, including best-effort events.
            val nativeResult = nativeBridge.describeDeployment(decryptedKubeconfig, namespace, name)
            if (nativeResult.isSuccess) {
                Result.Success(nativeResult.getOrThrow())
            } else {
                val ex = nativeResult.exceptionOrNull()
                val sanitizedMsg = LogSanitizer.sanitize(ex?.message)
                Result.Error(
                    AppError.Network(sanitizedMsg.ifEmpty { "Failed to describe deployment '$name'" }),
                )
            }
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            val sanitizedMsg = LogSanitizer.sanitize(t.message)
            Log.e(TAG, LogSanitizer.withStackTrace("Failed to describe deployment '$name': $sanitizedMsg", t))
            Result.Error(
                AppError.Network(sanitizedMsg.ifEmpty { "Failed to describe deployment from cluster API" }),
            )
        }
    }

    override suspend fun scaleDeployment(
        clusterId: String?,
        namespace: String,
        name: String,
        replicas: Int,
    ): Result<Unit> = withContext(dispatcherProvider.io) {
        if (clusterId == null) return@withContext Result.Error(AppError.NotFound("No cluster selected"))
        val cluster = clusterDao.getClusterById(clusterId)
            ?: return@withContext Result.Error(AppError.NotFound("Cluster '$clusterId' not found"))

        val decryptedKubeconfig = encryptor.decryptOrEmpty(cluster.rawKubeconfig)

        try {
            val nativeResult = nativeBridge.scaleDeployment(decryptedKubeconfig, namespace, name, replicas)
            if (nativeResult.isSuccess) {
                syncDeployments(clusterId, namespace)
                Result.Success(Unit)
            } else {
                val ex = nativeResult.exceptionOrNull()
                val sanitizedMsg = LogSanitizer.sanitize(ex?.message)
                Result.Error(
                    AppError.Network(sanitizedMsg.ifEmpty { "Failed to scale deployment '$name'" }),
                )
            }
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            val sanitizedMsg = LogSanitizer.sanitize(t.message)
            Log.e(TAG, LogSanitizer.withStackTrace("Failed to scale deployment '$name': $sanitizedMsg", t))
            Result.Error(
                AppError.Network(sanitizedMsg.ifEmpty { "Failed to scale deployment from cluster API" }),
            )
        }
    }

    override suspend fun restartDeployment(
        clusterId: String?,
        namespace: String,
        name: String,
    ): Result<Unit> = withContext(dispatcherProvider.io) {
        if (clusterId == null) return@withContext Result.Error(AppError.NotFound("No cluster selected"))
        val cluster = clusterDao.getClusterById(clusterId)
            ?: return@withContext Result.Error(AppError.NotFound("Cluster '$clusterId' not found"))

        val decryptedKubeconfig = encryptor.decryptOrEmpty(cluster.rawKubeconfig)

        try {
            val nativeResult = nativeBridge.restartDeployment(decryptedKubeconfig, namespace, name)
            if (nativeResult.isSuccess) {
                syncDeployments(clusterId, namespace)
                Result.Success(Unit)
            } else {
                val ex = nativeResult.exceptionOrNull()
                val sanitizedMsg = LogSanitizer.sanitize(ex?.message)
                Result.Error(
                    AppError.Network(sanitizedMsg.ifEmpty { "Failed to restart deployment '$name'" }),
                )
            }
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            val sanitizedMsg = LogSanitizer.sanitize(t.message)
            Log.e(TAG, LogSanitizer.withStackTrace("Failed to restart deployment '$name': $sanitizedMsg", t))
            Result.Error(
                AppError.Network(sanitizedMsg.ifEmpty { "Failed to restart deployment from cluster API" }),
            )
        }
    }

    override suspend fun deleteDeployment(
        clusterId: String?,
        namespace: String,
        name: String,
    ): Result<Unit> = withContext(dispatcherProvider.io) {
        if (clusterId == null) return@withContext Result.Error(AppError.NotFound("No cluster selected"))
        val cluster = clusterDao.getClusterById(clusterId)
            ?: return@withContext Result.Error(AppError.NotFound("Cluster '$clusterId' not found"))

        val decryptedKubeconfig = encryptor.decryptOrEmpty(cluster.rawKubeconfig)

        try {
            val nativeResult = nativeBridge.deleteDeployment(decryptedKubeconfig, namespace, name)
            if (nativeResult.isSuccess) {
                deploymentDao.deleteDeployment(clusterId, namespace, name)
                syncDeployments(clusterId, namespace)
                Result.Success(Unit)
            } else {
                val ex = nativeResult.exceptionOrNull()
                val sanitizedMsg = LogSanitizer.sanitize(ex?.message)
                Result.Error(
                    AppError.Network(sanitizedMsg.ifEmpty { "Failed to delete deployment '$name'" }),
                )
            }
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            val sanitizedMsg = LogSanitizer.sanitize(t.message)
            Log.e(TAG, LogSanitizer.withStackTrace("Failed to delete deployment '$name': $sanitizedMsg", t))
            Result.Error(
                AppError.Network(sanitizedMsg.ifEmpty { "Failed to delete deployment from cluster API" }),
            )
        }
    }

    override fun getLastRefreshedStream(clusterId: String?): Flow<Long?> {
        if (clusterId == null) return flowOf(null)
        return deploymentDao.getSyncMetadataStream("${clusterId}_deployments")
            .flowOn(dispatcherProvider.io)
    }

    /** Mirrors how the pods screen expresses "no namespace filter". */
    private fun isAllNamespaces(namespace: String?): Boolean =
        namespace.isNullOrBlank() ||
                namespace == "All Namespaces" ||
                namespace.equals("all", ignoreCase = true)

    /**
     * The bridge normalizes its own namespace argument; the DAO needs the null
     * form so its sync scope matches what was actually fetched.
     */
    private fun normalizedNamespaceOrNull(namespace: String?): String? =
        if (isAllNamespaces(namespace)) null else namespace?.trim()

    private fun splitYamlDocuments(yaml: String): List<String> =
        yaml.split(Regex("(?m)^---\\s*$")).map { it.trim() }.filter { it.isNotEmpty() }

    private fun extractKindFromYaml(yaml: String): String? {
        val match = Regex("""(?m)^kind:\s*["']?([A-Za-z0-9_]+)["']?""").find(yaml)
        return match?.groupValues?.get(1)
    }
}

