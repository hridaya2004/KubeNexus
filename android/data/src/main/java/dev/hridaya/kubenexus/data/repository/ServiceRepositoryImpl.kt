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
import dev.hridaya.kubenexus.data.source.local.dao.ServiceDao
import dev.hridaya.kubenexus.domain.model.ServiceDetails
import dev.hridaya.kubenexus.domain.model.ServiceSummary
import dev.hridaya.kubenexus.domain.repository.ServiceRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

class ServiceRepositoryImpl @Inject constructor(
    private val clusterDao: ClusterDao,
    private val serviceDao: ServiceDao,
    private val nativeBridge: KubeNexusNativeBridge,
    private val encryptor: KubeconfigEncryptor = NoOpKubeconfigEncryptor,
    private val dispatcherProvider: DispatcherProvider,
) : ServiceRepository {

    companion object {
        private const val TAG = "ServiceRepositoryImpl"
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
            // The reviewed manifest carries its own namespace, so the bridge is
            // told to fall back to it rather than overriding the user's choice.
            val nativeResult = nativeBridge.createService(decryptedKubeconfig, "", manifestYaml)
            if (nativeResult.isSuccess) {
                Result.Success(Unit)
            } else {
                val error = nativeResult.exceptionOrNull()
                val sanitizedMsg = LogSanitizer.sanitize(error?.message)
                Log.e(TAG, "Failed to create service for cluster '$clusterId': $sanitizedMsg")
                Result.Error(AppError.Network(sanitizedMsg.ifEmpty { "Failed to create service" }))
            }
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            val sanitizedMsg = LogSanitizer.sanitize(t.message)
            Log.e(TAG, LogSanitizer.withStackTrace("Failed to create service for cluster '$clusterId': $sanitizedMsg", t))
            Result.Error(AppError.Network(sanitizedMsg.ifEmpty { "Failed to create service" }))
        }
    }

    override fun getServicesStream(
        clusterId: String?,
        namespace: String?,
    ): Flow<List<ServiceSummary>> {
        if (clusterId == null) return flowOf(emptyList())

        val stream =
            if (isAllNamespaces(namespace)) {
                serviceDao.getServicesStream(clusterId)
            } else {
                serviceDao.getServicesByNamespaceStream(clusterId, namespace!!.trim())
            }

        return stream.map { entities ->
            entities.map { entity -> entity.toDomain() }
        }.flowOn(dispatcherProvider.io)
    }

    override suspend fun syncServices(
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
            val nativeResult = nativeBridge.listServices(decryptedKubeconfig, queryNamespace)
            if (nativeResult.isFailure) {
                val ex = nativeResult.exceptionOrNull()
                val sanitizedMsg = LogSanitizer.sanitize(ex?.message)
                return@withContext Result.Error(
                    AppError.Network(sanitizedMsg.ifEmpty { "Failed to list services from cluster" }),
                )
            }
            val liveServices: List<ServiceSummary> = nativeResult.getOrThrow()

            serviceDao.syncServices(
                clusterId = clusterId,
                namespace = queryNamespace,
                services = liveServices.map { it.toEntity(clusterId) },
                timestamp = System.currentTimeMillis(),
            )

            Result.Success(Unit)
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            val sanitizedMsg = LogSanitizer.sanitize(t.message)
            Log.e(TAG, LogSanitizer.withStackTrace("Failed to sync services for cluster '$clusterId': $sanitizedMsg", t))
            Result.Error(AppError.Network(sanitizedMsg.ifEmpty { "Failed to connect to cluster API" }))
        }
    }

    override suspend fun getServiceDetails(
        clusterId: String?,
        namespace: String,
        name: String,
    ): Result<ServiceDetails> = withContext(dispatcherProvider.io) {
        if (clusterId == null) return@withContext Result.Error(AppError.NotFound("No cluster selected"))
        val cluster = clusterDao.getClusterById(clusterId)
            ?: return@withContext Result.Error(AppError.NotFound("Cluster '$clusterId' not found"))

        val decryptedKubeconfig = encryptor.decryptOrEmpty(cluster.rawKubeconfig)

        try {
            // The bridge assembles details, including best-effort events.
            val nativeResult = nativeBridge.describeService(decryptedKubeconfig, namespace, name)
            if (nativeResult.isSuccess) {
                Result.Success(nativeResult.getOrThrow())
            } else {
                val ex = nativeResult.exceptionOrNull()
                val sanitizedMsg = LogSanitizer.sanitize(ex?.message)
                Result.Error(
                    AppError.Network(sanitizedMsg.ifEmpty { "Failed to describe service '$name'" }),
                )
            }
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            val sanitizedMsg = LogSanitizer.sanitize(t.message)
            Log.e(TAG, LogSanitizer.withStackTrace("Failed to describe service '$name': $sanitizedMsg", t))
            Result.Error(
                AppError.Network(sanitizedMsg.ifEmpty { "Failed to describe service from cluster API" }),
            )
        }
    }

    override fun getLastRefreshedStream(clusterId: String?): Flow<Long?> {
        if (clusterId == null) return flowOf(null)
        return serviceDao.getSyncMetadataStream("${clusterId}_services")
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
}
