package dev.hridaya.kubenexus.data.testing

import dev.hridaya.kubenexus.data.source.local.dao.ClusterDao
import dev.hridaya.kubenexus.data.source.local.entity.ClusterEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Holds clusters in memory; cached-data cleanup is a no-op. */
class InMemoryClusterDao : ClusterDao() {

    private val clusters = MutableStateFlow<Map<String, ClusterEntity>>(emptyMap())

    /** Adds an active cluster whose stored kubeconfig is [rawKubeconfig]. */
    fun seed(id: String, rawKubeconfig: String) {
        clusters.value += id to ClusterEntity(
            id = id,
            name = "Test Cluster",
            serverUrl = "https://k8s.example.com",
            rawKubeconfig = rawKubeconfig,
            contextName = "ctx",
            userName = "admin",
            namespace = "default",
            isActive = true,
            createdAt = 1000L,
            lastConnectedAt = null,
            status = "CONNECTED",
        )
    }

    override fun observeClusters(): Flow<List<ClusterEntity>> = clusters.map { it.values.toList() }

    override fun observeActiveCluster(): Flow<ClusterEntity?> =
        clusters.map { it.values.firstOrNull { cluster -> cluster.isActive } }

    override suspend fun getClusterById(id: String): ClusterEntity? = clusters.value[id]

    override suspend fun getAllClusters(): List<ClusterEntity> = clusters.value.values.toList()

    override suspend fun insertCluster(cluster: ClusterEntity) {
        clusters.value += cluster.id to cluster
    }

    override suspend fun updateCluster(cluster: ClusterEntity) = insertCluster(cluster)

    override suspend fun deleteCluster(id: String) {
        clusters.value -= id
    }

    override suspend fun deleteCachedResourcesForCluster(id: String) = Unit
    override suspend fun deleteNamespacesForCluster(id: String) = Unit
    override suspend fun deleteAPIResourcesForCluster(id: String) = Unit
    override suspend fun deleteExplainedResourcesForCluster(id: String) = Unit
    override suspend fun deleteOpenApiSchemaForCluster(id: String) = Unit
    override suspend fun deleteSyncMetadataForCluster(id: String) = Unit
    override suspend fun deleteAllCachedResources() = Unit
    override suspend fun deleteAllNamespaces() = Unit
    override suspend fun deleteAllAPIResources() = Unit
    override suspend fun deleteAllExplainedResources() = Unit
    override suspend fun deleteAllOpenApiSchemas() = Unit
    override suspend fun deleteAllSyncMetadata() = Unit

    override suspend fun deactivateAllClusters() {
        clusters.value = clusters.value.mapValues { (_, cluster) -> cluster.copy(isActive = false) }
    }

    override suspend fun activateCluster(id: String) {
        clusters.value[id]?.let { clusters.value += id to it.copy(isActive = true) }
    }

    override suspend fun updateClusterName(id: String, name: String) {
        clusters.value[id]?.let { clusters.value += id to it.copy(name = name) }
    }

    override suspend fun updateStatus(id: String, status: String, lastConnectedAt: Long?) {
        clusters.value[id]?.let { clusters.value += id to it.copy(status = status, lastConnectedAt = lastConnectedAt) }
    }
}
