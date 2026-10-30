package dev.hridaya.kubenexus.data.repository

import dev.hridaya.kubenexus.core.common.result.AppError
import dev.hridaya.kubenexus.core.common.result.Result
import dev.hridaya.kubenexus.data.resource.ResourceCodecRegistry
import dev.hridaya.kubenexus.data.resource.codec.DeploymentCodec
import dev.hridaya.kubenexus.data.resource.codec.PodCodec
import dev.hridaya.kubenexus.data.resource.codec.ServiceCodec
import dev.hridaya.kubenexus.data.source.local.entity.CachedResourceEntity
import dev.hridaya.kubenexus.data.testing.InMemoryClusterDao
import dev.hridaya.kubenexus.data.testing.InMemoryResourceCacheDao
import dev.hridaya.kubenexus.data.testing.RecordingResourceBridge
import dev.hridaya.kubenexus.data.testing.TestDispatcherProvider
import dev.hridaya.kubenexus.data.testing.testKubeconfigEncryptor
import dev.hridaya.kubenexus.domain.model.ServicePortDetail
import dev.hridaya.kubenexus.domain.model.resource.NamespaceScope
import dev.hridaya.kubenexus.domain.model.resource.ResourceKinds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResourceRepositoryImplTest {

    private val testDispatcher = StandardTestDispatcher()
    private val encryptor = testKubeconfigEncryptor()
    private val clusterDao = InMemoryClusterDao()
    private val cacheDao = InMemoryResourceCacheDao()
    private val bridge = RecordingResourceBridge()

    private val repository = ResourceRepositoryImpl(
        clusterCalls = ClusterCallRunner(
            clusterDao,
            encryptor,
            TestDispatcherProvider(testDispatcher)
        ),
        nativeBridge = bridge,
        cacheDao = cacheDao,
        codecs = ResourceCodecRegistry(setOf(PodCodec, DeploymentCodec, ServiceCodec)),
        dispatcherProvider = TestDispatcherProvider(testDispatcher),
    )

    private fun givenCluster() =
        clusterDao.seed(id = CLUSTER, rawKubeconfig = encryptor.encrypt(KUBECONFIG, CLUSTER))

    // region sync and observe

    @Test
    fun `sync fails when no cluster is selected`() = runTest(testDispatcher) {
        val result = repository.sync(null, ResourceKinds.Services, NamespaceScope.All)

        assertTrue((result as Result.Error).error is AppError.NotFound)
        assertTrue(bridge.calls.isEmpty())
    }

    @Test
    fun `sync lists with the decrypted kubeconfig and an empty namespace for all namespaces`() =
        runTest(testDispatcher) {
            givenCluster()

            repository.sync(CLUSTER, ResourceKinds.Services, NamespaceScope.All)

            val call = bridge.calls.single()
            assertEquals(KUBECONFIG, call.kubeconfig)
            assertEquals(ResourceKinds.Services.type, call.type)
            assertEquals("", call.namespace)
        }

    @Test
    fun `synced objects are observed as summaries`() = runTest(testDispatcher) {
        givenCluster()
        bridge.listResult = Result.Success(SERVICES_JSON)

        repository.sync(CLUSTER, ResourceKinds.Services, NamespaceScope.All)
        val services =
            repository.observe(CLUSTER, ResourceKinds.Services, NamespaceScope.All).first()

        // Cached rows come back ordered by namespace, then name.
        assertEquals(listOf("api", "nginx"), services.map { it.name })
        val nginx = services.single { it.name == "nginx" }
        assertEquals("web", nginx.namespace)
        assertEquals("10.96.0.10", nginx.clusterIP)
        assertEquals(
            listOf(
                ServicePortDetail(
                    port = 80,
                    targetPort = 8080,
                    nodePort = null,
                    protocol = "TCP",
                    name = "http"
                )
            ),
            nginx.ports,
        )
    }

    @Test
    fun `objects without a namespace are cached in default`() = runTest(testDispatcher) {
        givenCluster()
        bridge.listResult = Result.Success("""{"items":[{"metadata":{"name":"orphan"}}]}""")

        repository.sync(CLUSTER, ResourceKinds.Services, NamespaceScope.All)

        val cached =
            repository.observe(CLUSTER, ResourceKinds.Services, NamespaceScope.Only("default"))
                .first()
        assertEquals("orphan", cached.single().name)
    }

    @Test
    fun `syncing one namespace replaces only that namespace's rows`() = runTest(testDispatcher) {
        givenCluster()
        bridge.listResult = Result.Success(SERVICES_JSON)
        repository.sync(CLUSTER, ResourceKinds.Services, NamespaceScope.All)

        // "nginx" is gone from web; team-a's "api" was not part of this sync.
        bridge.listResult = Result.Success("""{"items":[]}""")
        repository.sync(CLUSTER, ResourceKinds.Services, NamespaceScope.Only("web"))

        val services =
            repository.observe(CLUSTER, ResourceKinds.Services, NamespaceScope.All).first()
        assertEquals(listOf("api"), services.map { it.name })
        assertEquals("web", bridge.calls.last().namespace)
    }

    @Test
    fun `kinds are cached apart from each other`() = runTest(testDispatcher) {
        givenCluster()
        bridge.listResult = Result.Success(SERVICES_JSON)
        repository.sync(CLUSTER, ResourceKinds.Services, NamespaceScope.All)

        val deployments =
            repository.observe(CLUSTER, ResourceKinds.Deployments, NamespaceScope.All).first()

        assertTrue(deployments.isEmpty())
    }

    @Test
    fun `sync records the time it finished`() = runTest(testDispatcher) {
        givenCluster()

        repository.sync(CLUSTER, ResourceKinds.Pods, NamespaceScope.All)

        assertNotNull(repository.observeLastSynced(CLUSTER, ResourceKinds.Pods).first())
    }

    @Test
    fun `a failed sync leaves the cache and its sync time untouched`() = runTest(testDispatcher) {
        givenCluster()
        bridge.listResult =
            Result.Error(AppError.Unknown("services denied: token: secret-token-12345 rejected"))

        val result = repository.sync(CLUSTER, ResourceKinds.Services, NamespaceScope.All)

        val error = (result as Result.Error).error
        assertTrue(error is AppError.Network)
        assertEquals("services denied: token: [REDACTED] rejected", error.message)
        assertTrue(cacheDao.rows.value.isEmpty())
        assertEquals(null, repository.observeLastSynced(CLUSTER, ResourceKinds.Services).first())
    }

    @Test
    fun `an unreadable cached row is skipped rather than failing the list`() =
        runTest(testDispatcher) {
            givenCluster()
            bridge.listResult = Result.Success(SERVICES_JSON)
            repository.sync(CLUSTER, ResourceKinds.Services, NamespaceScope.All)
            val key = ResourceKinds.Services.type.key
            cacheDao.upsert(
                listOf(
                    CachedResourceEntity(
                        id = CachedResourceEntity.idOf(CLUSTER, key, "web", "broken"),
                        clusterId = CLUSTER,
                        resourceKey = key,
                        namespace = "web",
                        name = "broken",
                        payload = "not json",
                        lastUpdated = 0L,
                    ),
                ),
            )

            val services =
                repository.observe(CLUSTER, ResourceKinds.Services, NamespaceScope.All).first()

            assertEquals(listOf("api", "nginx"), services.map { it.name })
        }

    // endregion

    // region list, describe and delete

    @Test
    fun `list passes the label selector and leaves the cache alone`() = runTest(testDispatcher) {
        givenCluster()
        bridge.listResult = Result.Success(SERVICES_JSON)

        val result = repository.list(
            CLUSTER,
            ResourceKinds.Services,
            NamespaceScope.Only("web"),
            "app=nginx"
        )

        assertEquals(listOf("nginx", "api"), (result as Result.Success).data.map { it.name })
        assertEquals("app=nginx", bridge.calls.single().labelSelector)
        assertTrue(cacheDao.rows.value.isEmpty())
    }

    @Test
    fun `describe decodes the object and attaches its events`() = runTest(testDispatcher) {
        givenCluster()
        bridge.getResult = Result.Success(SERVICE_JSON)
        bridge.eventsResult =
            Result.Success("""{"items":[{"type":"Warning","reason":"SyncFailed","message":"boom","count":2}]}""")

        val details = (repository.describe(
            CLUSTER,
            ResourceKinds.Services,
            "web",
            "nginx"
        ) as Result.Success).data

        assertEquals("nginx", details.name)
        assertEquals("SyncFailed", details.events.single().reason)
        val events = bridge.calls.single { it.operation == "events" }
        assertEquals("web", events.namespace)
        assertEquals("nginx", events.name)
    }

    @Test
    fun `describe survives events failing to load`() = runTest(testDispatcher) {
        givenCluster()
        bridge.getResult = Result.Success(SERVICE_JSON)
        bridge.eventsResult = Result.Error(AppError.Unknown("events are forbidden"))

        val details = (repository.describe(
            CLUSTER,
            ResourceKinds.Services,
            "web",
            "nginx"
        ) as Result.Success).data

        assertTrue(details.events.isEmpty())
    }

    @Test
    fun `describing a pod keeps its raw json`() = runTest(testDispatcher) {
        givenCluster()
        val podJson =
            """{"metadata":{"name":"web-1","namespace":"web"},"status":{"phase":"Running"}}"""
        bridge.getResult = Result.Success(podJson)

        val details = (repository.describe(
            CLUSTER,
            ResourceKinds.Pods,
            "web",
            "web-1"
        ) as Result.Success).data

        assertEquals(podJson, details.rawDescribeText)
    }

    @Test
    fun `delete removes the object from the cluster and the cache`() = runTest(testDispatcher) {
        givenCluster()
        bridge.listResult = Result.Success(SERVICES_JSON)
        repository.sync(CLUSTER, ResourceKinds.Services, NamespaceScope.All)

        val result = repository.delete(CLUSTER, ResourceKinds.Services, "web", "nginx")

        assertEquals(Result.Success(Unit), result)
        val remaining =
            repository.observe(CLUSTER, ResourceKinds.Services, NamespaceScope.All).first()
        assertEquals(listOf("api"), remaining.map { it.name })
    }

    @Test
    fun `a failed delete keeps the cached row`() = runTest(testDispatcher) {
        givenCluster()
        bridge.listResult = Result.Success(SERVICES_JSON)
        repository.sync(CLUSTER, ResourceKinds.Services, NamespaceScope.All)
        bridge.deleteResult = Result.Error(AppError.Unknown("forbidden"))

        val result = repository.delete(CLUSTER, ResourceKinds.Services, "web", "nginx")

        assertTrue(result is Result.Error)
        assertEquals(
            2,
            repository.observe(CLUSTER, ResourceKinds.Services, NamespaceScope.All).first().size
        )
    }

    // endregion

    // region create

    @Test
    fun `create sends each document of a manifest to the type it declares`() =
        runTest(testDispatcher) {
            givenCluster()

            val result = repository.create(CLUSTER, "$DEPLOYMENT_MANIFEST\n---\n$SERVICE_MANIFEST")

            assertEquals(Result.Success(Unit), result)
            val creates = bridge.calls.filter { it.operation == "create" }
            assertEquals(
                listOf(ResourceKinds.Deployments.type, ResourceKinds.Services.type),
                creates.map { it.type })
            assertEquals(listOf(DEPLOYMENT_MANIFEST, SERVICE_MANIFEST), creates.map { it.manifest })
            assertEquals(KUBECONFIG, creates.first().kubeconfig)
        }

    @Test
    fun `create rejects a kind the app does not model without calling the cluster`() =
        runTest(testDispatcher) {
            givenCluster()

            val result =
                repository.create(CLUSTER, "apiVersion: v1\nkind: Secret\nmetadata:\n  name: db")

            assertTrue((result as Result.Error).error is AppError.Validation)
            assertTrue(bridge.calls.isEmpty())
        }

    @Test
    fun `create stops at the first document that fails`() = runTest(testDispatcher) {
        givenCluster()
        bridge.createResults = listOf(Result.Error(AppError.Unknown("deployments are forbidden")))

        val result = repository.create(CLUSTER, "$DEPLOYMENT_MANIFEST\n---\n$SERVICE_MANIFEST")

        assertEquals(Result.Error(AppError.Network("deployments are forbidden")), result)
        assertEquals(1, bridge.calls.count { it.operation == "create" })
    }

    @Test
    fun `create fails when the cluster id is unknown`() = runTest(testDispatcher) {
        val result = repository.create("missing", SERVICE_MANIFEST)

        assertTrue((result as Result.Error).error is AppError.NotFound)
    }

    @Test
    fun `bridge exceptions become network errors`() = runTest(testDispatcher) {
        givenCluster()
        bridge.errorToThrow = RuntimeException("connection reset by peer")

        val result = repository.create(CLUSTER, SERVICE_MANIFEST)

        assertEquals(Result.Error(AppError.Network("connection reset by peer")), result)
    }

    // A cancelled call (the screen closed, or a newer refresh replaced it) is not a failure:
    // it must reach the caller as cancellation, not be logged and returned as an error.
    @Test
    fun `cancellation propagates instead of being reported as a failed request`() =
        runTest(testDispatcher) {
            givenCluster()
            bridge.errorToThrow = CancellationException("screen closed")

            val thrown = runCatching {
                repository.sync(
                    CLUSTER,
                    ResourceKinds.Pods,
                    NamespaceScope.All
                )
            }.exceptionOrNull()

            assertTrue(thrown is CancellationException)
        }

    // endregion

    private companion object {
        const val CLUSTER = "c-1"

        val KUBECONFIG = """
            apiVersion: v1
            kind: Config
            current-context: minikube
            users:
            - name: minikube
              user:
                token: secret-token-12345
        """.trimIndent()

        const val SERVICE_JSON = """
            {"metadata":{"name":"nginx","namespace":"web","creationTimestamp":"2026-03-01T10:00:00Z"},
             "spec":{"type":"ClusterIP","clusterIP":"10.96.0.10",
                     "ports":[{"name":"http","port":80,"targetPort":8080,"protocol":"TCP"}]}}
        """

        const val SERVICES_JSON = """
            {"metadata":{"resourceVersion":"42"},"items":[
              $SERVICE_JSON,
              {"metadata":{"name":"api","namespace":"team-a"},
               "spec":{"type":"NodePort","clusterIP":"10.96.0.11","ports":[{"port":8080,"targetPort":"http","nodePort":31000}]}}
            ]}
        """

        val DEPLOYMENT_MANIFEST = """
            apiVersion: apps/v1
            kind: Deployment
            metadata:
              name: nginx
              namespace: web
        """.trimIndent()

        val SERVICE_MANIFEST = """
            apiVersion: v1
            kind: Service
            metadata:
              name: nginx
              namespace: web
        """.trimIndent()
    }
}
