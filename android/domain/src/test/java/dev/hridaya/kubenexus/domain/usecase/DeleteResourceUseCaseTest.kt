package dev.hridaya.kubenexus.domain.usecase

import dev.hridaya.kubenexus.core.common.result.AppError
import dev.hridaya.kubenexus.core.common.result.Result
import dev.hridaya.kubenexus.domain.fake.FakeResourceRepository
import dev.hridaya.kubenexus.domain.model.resource.ResourceKinds
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeleteResourceUseCaseTest {

    private val repository = FakeResourceRepository()
    private val useCase = DeleteResourceUseCase(repository)

    @Test
    fun `delegates the delete to the repository`() = runTest {
        val result = useCase("c1", ResourceKinds.Deployments, "default", "nginx")

        assertEquals(Result.Success(Unit), result)
        assertEquals(
            FakeResourceRepository.DeleteCall("c1", ResourceKinds.Deployments, "default", "nginx"),
            repository.deleteCalls.single(),
        )
    }

    @Test
    fun `propagates a repository failure`() = runTest {
        repository.deleteResult = Result.Error(AppError.Network("forbidden"))

        val result = useCase("c1", ResourceKinds.Pods, "default", "web-1")

        assertEquals(Result.Error(AppError.Network("forbidden")), result)
    }

    @Test
    fun `rejects a blank name without calling the repository`() = runTest {
        val result = useCase("c1", ResourceKinds.Services, "default", " ")

        assertTrue((result as Result.Error).error is AppError.Validation)
        assertTrue(repository.deleteCalls.isEmpty())
    }
}
