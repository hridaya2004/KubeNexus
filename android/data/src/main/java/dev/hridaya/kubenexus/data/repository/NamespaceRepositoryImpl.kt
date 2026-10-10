package dev.hridaya.kubenexus.data.repository

import dev.hridaya.kubenexus.core.common.dispatcher.DispatcherProvider
import dev.hridaya.kubenexus.core.common.result.Result
import dev.hridaya.kubenexus.core.common.result.map
import dev.hridaya.kubenexus.core.common.result.onSuccess
import dev.hridaya.kubenexus.data.mapper.toDomainName
import dev.hridaya.kubenexus.data.mapper.toEntity
import dev.hridaya.kubenexus.data.nativebridge.KubeNexusNativeBridge
import dev.hridaya.kubenexus.data.source.local.dao.NamespaceDao
import dev.hridaya.kubenexus.data.source.local.dao.ResourceCacheDao
import dev.hridaya.kubenexus.domain.repository.NamespaceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class NamespaceRepositoryImpl @Inject constructor(
    private val clusterCalls: ClusterCallRunner,
    private val nativeBridge: KubeNexusNativeBridge,
    private val namespaceDao: NamespaceDao,
    private val resourceCacheDao: ResourceCacheDao,
    private val dispatcherProvider: DispatcherProvider,
) : NamespaceRepository {

    override fun observeNames(clusterId: String?): Flow<List<String>> {
        if (clusterId == null) return flowOf(DEFAULT_NAMESPACES)
        return namespaceDao.getNamespacesStream(clusterId).map { entities ->
            entities.map { it.toDomainName() }.filter { it.isNotBlank() }.distinct().sorted()
                .ifEmpty { DEFAULT_NAMESPACES }
        }.flowOn(dispatcherProvider.io)
    }

    override suspend fun sync(clusterId: String?): Result<Unit> =
        clusterCalls.run(clusterId, "Failed to sync namespaces") { cluster ->
            nativeBridge.listNamespaces(cluster.kubeconfig).onSuccess { namespaces ->
                val entities = namespaces
                    .filter { it.name.isNotBlank() }
                    .map { it.toEntity(cluster.id) }
                // An empty answer is more likely a restricted role than a
                // cluster without namespaces; keep what is cached.
                if (entities.isNotEmpty()) namespaceDao.syncNamespaces(cluster.id, entities)
            }.map { }
        }

    override suspend fun create(clusterId: String?, name: String): Result<Unit> =
        clusterCalls.run(clusterId, "Failed to create namespace '$name'") { cluster ->
            nativeBridge.createNamespace(cluster.kubeconfig, name)
        }

    override suspend fun delete(clusterId: String?, name: String): Result<Unit> =
        clusterCalls.run(clusterId, "Failed to delete namespace '$name'") { cluster ->
            nativeBridge.deleteNamespace(cluster.kubeconfig, name).onSuccess {
                namespaceDao.deleteNamespace(cluster.id, name)
                resourceCacheDao.deleteNamespace(cluster.id, name)
            }
        }

    private companion object {
        /** Offered until the first sync, since every cluster has them. */
        val DEFAULT_NAMESPACES = listOf("default", "kube-system")
    }
}
