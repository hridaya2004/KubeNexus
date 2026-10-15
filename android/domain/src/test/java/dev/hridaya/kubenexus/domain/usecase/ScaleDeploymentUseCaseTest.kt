package dev.hridaya.kubenexus.domain.usecase

import dev.hridaya.kubenexus.core.common.result.AppError
import dev.hridaya.kubenexus.core.common.result.Result
import dev.hridaya.kubenexus.domain.fake.FakeDeploymentRepository
import dev.hridaya.kubenexus.domain.fake.FakeResourceRepository
import dev.hridaya.kubenexus.domain.model.resource.NamespaceScope
import dev.hridaya.kubenexus.domain.model.resource.ResourceKinds
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScaleDeploymentUseCaseTest {

    private val deployments = FakeDeploymentRepository()
    private val resources = FakeResourceRepository()
    private val useCase = ScaleDeploymentUseCase(deployments, resources)

    @Test
    fun `rejects negative replicas without delegating to repository`() = runTest {
        val result = useCase(clusterId = "c1", namespace = "default", name = "nginx", replicas = -1)

        assertTrue(result is Result.Error)
        assertTrue((result as Result.Error).error is AppError.Validation)
        assertEquals("Replica count cannot be negative", result.error.message)
        assertNull(deployments.lastCall)
    }

    @Test
    fun `delegates valid replica scale to repository`() = runTest {
        val result =
            useCase(clusterId = "c1", namespace = "prod", name = "api-server", replicas = 3)

        assertTrue(result is Result.Success)
        assertEquals(
            FakeDeploymentRepository.Call("scale", "c1", "prod", "api-server", 3),
            deployments.lastCall,
        )
    }

    @Test
    fun `refreshes the namespace's cached deployments after scaling`() = runTest {
        useCase(clusterId = "c1", namespace = "prod", name = "api-server", replicas = 3)

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
    fun `a failed refresh does not fail the scale`() = runTest {
        resources.syncResult = Result.Error(AppError.Network("offline"))

        val result =
            useCase(clusterId = "c1", namespace = "prod", name = "api-server", replicas = 3)

        assertEquals(Result.Success(Unit), result)
    }

    @Test
    fun `a failed scale skips the refresh`() = runTest {
        deployments.result = Result.Error(AppError.Network("forbidden"))

        val result =
            useCase(clusterId = "c1", namespace = "prod", name = "api-server", replicas = 3)

        assertTrue(result is Result.Error)
        assertTrue(resources.syncCalls.isEmpty())
    }
}
