package dev.hridaya.kubenexus.domain.usecase

import dev.hridaya.kubenexus.core.common.dispatcher.DispatcherProvider
import dev.hridaya.kubenexus.core.common.result.AppError
import dev.hridaya.kubenexus.core.common.result.Result
import dev.hridaya.kubenexus.domain.model.Cluster
import dev.hridaya.kubenexus.domain.model.ClusterHealth
import dev.hridaya.kubenexus.domain.model.ClusterStatus
import dev.hridaya.kubenexus.domain.repository.ClusterRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CheckClusterHealthUseCaseTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testDispatcherProvider = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
        override val unconfined: CoroutineDispatcher = testDispatcher
    }

    private lateinit var fakeRepository: FakeClusterRepository
    private lateinit var useCase: CheckClusterHealthUseCase

    @Before
    fun setUp() {
        fakeRepository = FakeClusterRepository()
        useCase = CheckClusterHealthUseCase(fakeRepository, testDispatcherProvider)
    }

    @Test
    fun `checkHealth queries health by clusterId`() = runTest(testDispatcher) {
        val result = useCase.checkHealth("cluster-1")
        assertTrue(result is Result.Success)
        val health = (result as Result.Success).data
        assertTrue(health.livez)
        assertTrue(health.readyz)
        assertEquals("v1.30.0", health.serverVersion)
        assertEquals("Ready", health.statusMessage)
    }

    @Test
    fun `checkHealthByKubeconfig queries health by kubeconfig string`() = runTest(testDispatcher) {
        val result = useCase.checkHealthByKubeconfig("raw-kubeconfig")
        assertTrue(result is Result.Success)
        val health = (result as Result.Success).data
        assertTrue(health.livez)
        assertEquals("Ready", health.statusMessage)
    }

    private class FakeClusterRepository : ClusterRepository {
        override fun getClustersStream(): Flow<List<Cluster>> = emptyFlow()
        override fun getActiveClusterStream(): Flow<Cluster?> = emptyFlow()
        override suspend fun getClusterById(id: String): Cluster? = null
        override suspend fun addCluster(
            kubeconfigRaw: String,
            customName: String?,
            setAsActive: Boolean
        ): Result<Cluster> =
            Result.Error(AppError.Unknown())

        override suspend fun setActiveCluster(id: String): Result<Unit> = Result.Success(Unit)
        override suspend fun deleteCluster(id: String): Result<Unit> = Result.Success(Unit)

        override suspend fun clearCachedData(): Result<Unit> = Result.Success(Unit)
        override suspend fun testConnection(kubeconfigRaw: String): Result<String> =
            Result.Success("OK")

        override suspend fun testClusterById(id: String): Result<String> = Result.Success("OK")
        override suspend fun checkClusterHealth(id: String): Result<ClusterHealth> =
            Result.Success(
                ClusterHealth(
                    livez = true,
                    readyz = true,
                    serverVersion = "v1.30.0",
                    statusMessage = "Ready"
                )
            )

        override suspend fun checkClusterHealthByKubeconfig(kubeconfigRaw: String): Result<ClusterHealth> =
            Result.Success(
                ClusterHealth(
                    livez = true,
                    readyz = true,
                    serverVersion = "v1.30.0",
                    statusMessage = "Ready"
                )
            )

        override suspend fun updateClusterName(id: String, newName: String): Result<Unit> =
            Result.Success(Unit)

        override suspend fun updateClusterStatus(
            id: String,
            status: ClusterStatus,
            lastConnectedAt: Long?
        ): Result<Unit> = Result.Success(Unit)

        override suspend fun migratePlaintextClusters(): Result<Int> = Result.Success(0)
    }
}
