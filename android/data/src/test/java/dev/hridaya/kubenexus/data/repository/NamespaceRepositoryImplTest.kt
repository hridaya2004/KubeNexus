package dev.hridaya.kubenexus.data.repository

import dev.hridaya.kubenexus.core.common.result.Result
import dev.hridaya.kubenexus.data.nativebridge.FakeKubeNexusNativeBridge
import dev.hridaya.kubenexus.data.source.local.entity.CachedResourceEntity
import dev.hridaya.kubenexus.data.testing.InMemoryClusterDao
import dev.hridaya.kubenexus.data.testing.InMemoryNamespaceDao
import dev.hridaya.kubenexus.data.testing.InMemoryResourceCacheDao
import dev.hridaya.kubenexus.data.testing.TestDispatcherProvider
import dev.hridaya.kubenexus.data.testing.testKubeconfigEncryptor
import dev.hridaya.kubenexus.domain.model.Namespace
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class NamespaceRepositoryImplTest {

    private val testDispatcher = StandardTestDispatcher()
    private val encryptor = testKubeconfigEncryptor()
    private val clusterDao = InMemoryClusterDao().apply {
        seed(
            id = CLUSTER,
            rawKubeconfig = encryptor.encrypt("kubeconfig", CLUSTER)
        )
    }
    private val namespaceDao = InMemoryNamespaceDao()
    private val cacheDao = InMemoryResourceCacheDao()
    private var liveNamespaces: Result<List<Namespace>> = Result.Success(emptyList())

    private val bridge = object : FakeKubeNexusNativeBridge() {
        override fun listNamespaces(rawKubeconfig: String): Result<List<Namespace>> = liveNamespaces
    }

    private val repository = NamespaceRepositoryImpl(
        clusterCalls = ClusterCallRunner(
            clusterDao,
            encryptor,
            TestDispatcherProvider(testDispatcher)
        ),
        nativeBridge = bridge,
        namespaceDao = namespaceDao,
        resourceCacheDao = cacheDao,
        dispatcherProvider = TestDispatcherProvider(testDispatcher),
    )

    @Test
    fun `offers the namespaces every cluster has until the first sync`() = runTest(testDispatcher) {
        assertEquals(listOf("default", "kube-system"), repository.observeNames(CLUSTER).first())
    }

    @Test
    fun `sync replaces the cached names, sorted`() = runTest(testDispatcher) {
        liveNamespaces =
            Result.Success(listOf(Namespace("team-b"), Namespace("default"), Namespace("team-a")))

        repository.sync(CLUSTER)

        assertEquals(
            listOf("default", "team-a", "team-b"),
            repository.observeNames(CLUSTER).first()
        )
    }

    @Test
    fun `an empty answer keeps what is cached`() = runTest(testDispatcher) {
        liveNamespaces = Result.Success(listOf(Namespace("team-a")))
        repository.sync(CLUSTER)

        liveNamespaces = Result.Success(emptyList())
        repository.sync(CLUSTER)

        assertEquals(listOf("team-a"), repository.observeNames(CLUSTER).first())
    }

    @Test
    fun `deleting a namespace drops everything cached inside it`() = runTest(testDispatcher) {
        liveNamespaces = Result.Success(listOf(Namespace("team-a"), Namespace("team-b")))
        repository.sync(CLUSTER)
        cacheDao.upsert(listOf(cachedPod("team-a", "web-1"), cachedPod("team-b", "api-1")))

        val result = repository.delete(CLUSTER, "team-a")

        assertEquals(Result.Success(Unit), result)
        assertEquals(listOf("team-b"), repository.observeNames(CLUSTER).first())
        assertEquals(listOf("api-1"), cacheDao.rows.value.values.map { it.name })
    }

    private fun cachedPod(namespace: String, name: String) = CachedResourceEntity(
        id = CachedResourceEntity.idOf(CLUSTER, "v1/pods", namespace, name),
        clusterId = CLUSTER,
        resourceKey = "v1/pods",
        namespace = namespace,
        name = name,
        payload = "{}",
        lastUpdated = 0L,
    )

    private companion object {
        const val CLUSTER = "c-1"
    }
}
