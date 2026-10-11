package dev.hridaya.kubenexus.presentation.testing

import dev.hridaya.kubenexus.core.common.result.Result
import dev.hridaya.kubenexus.domain.model.Cluster
import dev.hridaya.kubenexus.domain.model.ClusterHealth
import dev.hridaya.kubenexus.domain.model.ClusterStatus
import dev.hridaya.kubenexus.domain.repository.ClusterRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** One active cluster, "c-1", whose operations all succeed. */
class FakeClusterRepository : ClusterRepository {
    private val activeCluster = Cluster(
        id = "c-1",
        name = "prod-cluster",
        serverUrl = "https://127.0.0.1:6443",
        contextName = "prod",
        isActive = true,
        status = ClusterStatus.CONNECTED,
    )

    private val health = ClusterHealth(
        livez = true,
        readyz = true,
        healthz = true,
        serverVersion = "1.30.0",
        statusMessage = "Ready",
    )

    override fun getClustersStream(): Flow<List<Cluster>> = flowOf(listOf(activeCluster))
    override fun getActiveClusterStream(): Flow<Cluster?> = flowOf(activeCluster)
    override suspend fun getClusterById(id: String): Cluster = activeCluster
    override suspend fun addCluster(
        kubeconfigRaw: String,
        customName: String?,
        setAsActive: Boolean,
    ): Result<Cluster> = Result.Success(activeCluster)

    override suspend fun setActiveCluster(id: String): Result<Unit> = Result.Success(Unit)
    override suspend fun updateClusterName(id: String, newName: String): Result<Unit> = Result.Success(Unit)
    override suspend fun testConnection(kubeconfigRaw: String): Result<String> = Result.Success("OK")
    override suspend fun testClusterById(id: String): Result<String> = Result.Success("OK")
    override suspend fun checkClusterHealth(id: String): Result<ClusterHealth> = Result.Success(health)
    override suspend fun checkClusterHealthByKubeconfig(kubeconfigRaw: String): Result<ClusterHealth> =
        Result.Success(health)

    override suspend fun updateClusterStatus(
        id: String,
        status: ClusterStatus,
        lastConnectedAt: Long?,
    ): Result<Unit> = Result.Success(Unit)

    override suspend fun deleteCluster(id: String): Result<Unit> = Result.Success(Unit)
    override suspend fun clearCachedData(): Result<Unit> = Result.Success(Unit)
}
