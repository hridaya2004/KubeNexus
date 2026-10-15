package dev.hridaya.kubenexus.domain.usecase

import dev.hridaya.kubenexus.core.common.result.AppError
import dev.hridaya.kubenexus.core.common.result.Result
import dev.hridaya.kubenexus.domain.fake.FakeDeploymentRepository
import dev.hridaya.kubenexus.domain.fake.FakeResourceRepository
import dev.hridaya.kubenexus.domain.model.resource.NamespaceScope
import dev.hridaya.kubenexus.domain.model.resource.ResourceKinds
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RestartDeploymentUseCaseTest {

    private val deployments = FakeDeploymentRepository()
    private val resources = FakeResourceRepository()
    private val useCase = RestartDeploymentUseCase(deployments, resources)

    @Test
    fun `delegates restart to repository`() = runTest {
        val result = useCase(clusterId = "c1", namespace = "prod", name = "nginx")

        assertTrue(result is Result.Success)
        assertEquals(
            FakeDeploymentRepository.Call("restart", "c1", "prod", "nginx", replicas = null),
            deployments.lastCall,
        )
    }

    @Test
    fun `refreshes the namespace's cached deployments after restarting`() = runTest {
        useCase(clusterId = "c1", namespace = "prod", name = "nginx")

        assertEquals(
            FakeResourceRepository.SyncCall(
                "c1",
                ResourceKinds.Deployments,
                NamespaceScope.Only("prod")
            ),
            resources.syncCalls.single(),
        )
    }

    @Test
    fun `a failed restart skips the refresh`() = runTest {
        deployments.result = Result.Error(AppError.Network("forbidden"))

        useCase(clusterId = "c1", namespace = "prod", name = "nginx")

        assertTrue(resources.syncCalls.isEmpty())
    }
}
