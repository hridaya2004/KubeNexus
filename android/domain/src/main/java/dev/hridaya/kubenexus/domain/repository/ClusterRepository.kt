package dev.hridaya.kubenexus.domain.repository

import dev.hridaya.kubenexus.core.common.result.Result
import dev.hridaya.kubenexus.domain.model.Cluster
import dev.hridaya.kubenexus.domain.model.ClusterHealth
import dev.hridaya.kubenexus.domain.model.ClusterStatus
import kotlinx.coroutines.flow.Flow

interface ClusterRepository {
    fun getClustersStream(): Flow<List<Cluster>>
    fun getActiveClusterStream(): Flow<Cluster?>
    suspend fun getClusterById(id: String): Cluster?
    suspend fun addCluster(
        kubeconfigRaw: String,
        customName: String?,
        setAsActive: Boolean = true
    ): Result<Cluster>

    suspend fun setActiveCluster(id: String): Result<Unit>
    suspend fun deleteCluster(id: String): Result<Unit>

    /** Deletes cached cluster data for every cluster, keeping the clusters and credentials. */
    suspend fun clearCachedData(): Result<Unit>
    suspend fun testConnection(kubeconfigRaw: String): Result<String>
    suspend fun testClusterById(id: String): Result<String>
    suspend fun checkClusterHealth(id: String): Result<ClusterHealth>
    suspend fun checkClusterHealthByKubeconfig(kubeconfigRaw: String): Result<ClusterHealth>
    suspend fun updateClusterName(id: String, newName: String): Result<Unit>
    suspend fun updateClusterStatus(
        id: String,
        status: ClusterStatus,
        lastConnectedAt: Long?
    ): Result<Unit>

    suspend fun migratePlaintextClusters(): Result<Int>
}
