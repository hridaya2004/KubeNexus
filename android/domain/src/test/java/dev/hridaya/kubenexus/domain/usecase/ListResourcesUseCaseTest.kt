package dev.hridaya.kubenexus.domain.usecase

import dev.hridaya.kubenexus.core.common.result.Result
import dev.hridaya.kubenexus.domain.fake.FakeResourceRepository
import dev.hridaya.kubenexus.domain.model.Pod
import dev.hridaya.kubenexus.domain.model.resource.NamespaceScope
import dev.hridaya.kubenexus.domain.model.resource.ResourceKinds
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ListResourcesUseCaseTest {

    private val repository = FakeResourceRepository()
    private val useCase = ListResourcesUseCase(repository)

    @Test
    fun `match labels become a comma separated label selector`() = runTest {
        val pod = Pod(id = "web_nginx", name = "nginx", namespace = "web")
        repository.givenList(ResourceKinds.Pods, Result.Success(listOf(pod)))

        val result = useCase(
            clusterId = "c1",
            kind = ResourceKinds.Pods,
            scope = NamespaceScope.Only("web"),
            matchLabels = linkedMapOf("app" to "nginx", "tier" to "frontend"),
        )

        assertEquals(Result.Success(listOf(pod)), result)
        assertEquals(
            FakeResourceRepository.ListCall(
                "c1",
                ResourceKinds.Pods,
                NamespaceScope.Only("web"),
                "app=nginx,tier=frontend"
            ),
            repository.listCalls.single(),
        )
    }

    @Test
    fun `no match labels lists everything`() = runTest {
        useCase(clusterId = "c1", kind = ResourceKinds.Deployments, scope = NamespaceScope.All)

        assertEquals("", repository.listCalls.single().labelSelector)
    }
}
