package dev.hridaya.kubenexus.domain.usecase

import dev.hridaya.kubenexus.core.common.result.AppError
import dev.hridaya.kubenexus.core.common.result.Result
import dev.hridaya.kubenexus.domain.fake.FakeResourceRepository
import dev.hridaya.kubenexus.domain.model.resource.NamespaceScope
import dev.hridaya.kubenexus.domain.model.resource.ResourceKinds
import dev.hridaya.kubenexus.domain.repository.NamespaceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RefreshWorkloadsUseCaseTest {

    private val namespaces = object : NamespaceRepository {
        val synced = mutableListOf<String?>()
        var syncResult: Result<Unit> = Result.Success(Unit)

        override fun observeNames(clusterId: String?): Flow<List<String>> = flowOf(emptyList())
        override suspend fun sync(clusterId: String?): Result<Unit> =
            syncResult.also { synced += clusterId }

        override suspend fun create(clusterId: String?, name: String): Result<Unit> =
            Result.Success(Unit)

        override suspend fun delete(clusterId: String?, name: String): Result<Unit> =
            Result.Success(Unit)
    }
    private val resources = FakeResourceRepository()
    private val useCase = RefreshWorkloadsUseCase(namespaces, resources)

    @Test
    fun `syncs the namespaces and the pods of the given namespace`() = runTest {
        useCase("c1", "web")

        assertEquals(listOf("c1"), namespaces.synced)
        assertEquals(
            FakeResourceRepository.SyncCall("c1", ResourceKinds.Pods, NamespaceScope.Only("web")),
            resources.syncCalls.single(),
        )
    }

    @Test
    fun `a namespace sync failure does not fail the refresh`() = runTest {
        namespaces.syncResult = Result.Error(AppError.Network("forbidden"))

        assertEquals(Result.Success(Unit), useCase("c1"))
    }

    @Test
    fun `the result is the pod sync's`() = runTest {
        resources.syncResult = Result.Error(AppError.Network("offline"))

        assertEquals(Result.Error(AppError.Network("offline")), useCase("c1"))
    }
}
