package dev.hridaya.kubenexus.data.repository

import dev.hridaya.kubenexus.core.common.dispatcher.DispatcherProvider
import dev.hridaya.kubenexus.core.common.result.AppError
import dev.hridaya.kubenexus.core.common.result.Result
import dev.hridaya.kubenexus.core.security.AesGcmKubeconfigEncryptor
import dev.hridaya.kubenexus.data.nativebridge.FakeKubeNexusNativeBridge
import dev.hridaya.kubenexus.data.source.local.dao.ClusterDao
import dev.hridaya.kubenexus.data.source.local.dao.NamespaceDao
import dev.hridaya.kubenexus.data.source.local.dao.PodDao
import dev.hridaya.kubenexus.data.source.local.entity.ClusterEntity
import dev.hridaya.kubenexus.data.source.local.entity.NamespaceEntity
import dev.hridaya.kubenexus.data.source.local.entity.PodEntity
import dev.hridaya.kubenexus.data.source.local.entity.SyncMetadataEntity
import dev.hridaya.kubenexus.domain.model.Pod
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PodRepositoryImplTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testDispatcherProvider = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
        override val unconfined: CoroutineDispatcher = testDispatcher
    }

    private lateinit var fakeDao: FakeClusterDao
    private lateinit var encryptor: AesGcmKubeconfigEncryptor
    private lateinit var recordingBridge: RecordingBridge
    private lateinit var repository: PodRepositoryImpl

    private val sampleKubeconfig = """
        apiVersion: v1
        clusters:
        - cluster:
            certificate-authority-data: LS0tLS1CRUdJTi...
            server: https://192.168.49.2:8443
          name: minikube
        contexts:
        - context:
            cluster: minikube
            namespace: default
            user: minikube
          name: minikube
        current-context: minikube
        kind: Config
        preferences: {}
        users:
        - name: minikube
          user:
            client-certificate-data: LS0tLS1CRUdJTi...
            client-key-data: LS0tLS1CRUdJTi...
            token: secret-token-12345
    """.trimIndent()

    private val sampleManifest = """
        apiVersion: v1
        kind: Pod
        metadata:
          name: nginx
          namespace: web
    """.trimIndent()

    @Before
    fun setUp() {
        fakeDao = FakeClusterDao()
        val secretKey = AesGcmKubeconfigEncryptor.generateKey()
        encryptor = AesGcmKubeconfigEncryptor(secretKey)
        recordingBridge = RecordingBridge()

        repository = PodRepositoryImpl(
            clusterDao = fakeDao,
            podDao = NoOpPodDao(),
            namespaceDao = NoOpNamespaceDao(),
            nativeBridge = recordingBridge,
            encryptor = encryptor,
            dispatcherProvider = testDispatcherProvider,
        )
    }

    @Test
    fun `createPodFromManifest fails when no cluster is selected`() = runTest(testDispatcher) {
        val result =
            repository.createPodFromManifest(clusterId = null, manifestYaml = sampleManifest)

        assertTrue(result is Result.Error)
        assertEquals(
            "No cluster selected",
            (result as Result.Error).error.message,
        )
    }

    @Test
    fun `createPodFromManifest fails when cluster id is unknown`() = runTest(testDispatcher) {
        val result = repository.createPodFromManifest(
            clusterId = "c-missing",
            manifestYaml = sampleManifest,
        )

        assertTrue(result is Result.Error)
        assertTrue((result as Result.Error).error is AppError.NotFound)
        assertEquals(
            "Cluster 'c-missing' not found",
            result.error.message,
        )
    }

    @Test
    fun `createPodFromManifest decrypts kubeconfig and applies reviewed manifest verbatim`() =
        runTest(testDispatcher) {
            seedCluster(id = "c-1", rawKubeconfig = encryptor.encrypt(sampleKubeconfig))

            val result = repository.createPodFromManifest(
                clusterId = "c-1",
                manifestYaml = sampleManifest,
            )

            assertTrue(result is Result.Success)
            assertEquals(Unit, (result as Result.Success).data)
            // The bridge must receive the decrypted plaintext, never the stored ciphertext.
            assertEquals(sampleKubeconfig, recordingBridge.capturedKubeconfig)
            // The manifest declares its own namespace, so none is passed on top of it.
            assertEquals("", recordingBridge.capturedNamespace)
            assertEquals(sampleManifest, recordingBridge.capturedManifest)
        }

    @Test
    fun `createPodFromManifest surfaces sanitized bridge error messages`() =
        runTest(testDispatcher) {
            seedCluster(id = "c-1", rawKubeconfig = encryptor.encrypt(sampleKubeconfig))
            recordingBridge.resultToReturn = Result.Error(
                AppError.Unknown("pods denied: token: secret-token-12345 rejected"),
            )

            val result = repository.createPodFromManifest(
                clusterId = "c-1",
                manifestYaml = sampleManifest,
            )

            assertTrue(result is Result.Error)
            val error = (result as Result.Error).error
            assertTrue(error is AppError.Network)
            assertEquals(
                "pods denied: token: [REDACTED] rejected",
                error.message,
            )
        }

    @Test
    fun `createPodFromManifest maps bridge exceptions to network errors`() =
        runTest(testDispatcher) {
            seedCluster(id = "c-1", rawKubeconfig = encryptor.encrypt(sampleKubeconfig))
            recordingBridge.errorToThrow = RuntimeException("connection reset by peer")

            val result = repository.createPodFromManifest(
                clusterId = "c-1",
                manifestYaml = sampleManifest,
            )

            assertTrue(result is Result.Error)
            val error = (result as Result.Error).error
            assertTrue(error is AppError.Network)
            assertEquals("connection reset by peer", error.message)
        }

    @Test
    fun `getPodsBySelector decrypts kubeconfig and passes selector to bridge`() =
        runTest(testDispatcher) {
            seedCluster(id = "c-1", rawKubeconfig = encryptor.encrypt(sampleKubeconfig))
            recordingBridge.listPodsResultToReturn = Result.Success(emptyList())

            val result = repository.getPodsBySelector(
                clusterId = "c-1",
                namespace = "web",
                labelSelector = "app=nginx",
            )

            assertTrue(result is Result.Success)
            assertEquals(sampleKubeconfig, recordingBridge.capturedListPodsKubeconfig)
            assertEquals("web", recordingBridge.capturedListPodsNamespace)
            assertEquals("app=nginx", recordingBridge.capturedListPodsSelector)
        }

    private fun seedCluster(id: String, rawKubeconfig: String) {
        fakeDao.clusters[id] = ClusterEntity(
            id = id,
            name = "Test Cluster",
            serverUrl = "https://k8s.example.com",
            rawKubeconfig = rawKubeconfig,
            contextName = "ctx",
            userName = "admin",
            namespace = "default",
            isActive = true,
            createdAt = 1000L,
            lastConnectedAt = null,
            status = "CONNECTED",
        )
    }

    /**
     * Records every [createPod] argument so tests can assert exactly what
     * crossed the bridge, then returns the preconfigured outcome.
     */
    private class RecordingBridge : FakeKubeNexusNativeBridge() {
        var capturedKubeconfig: String? = null
        var capturedNamespace: String? = null
        var capturedManifest: String? = null
        var resultToReturn: Result<String>? = null
        var errorToThrow: Throwable? = null

        var capturedListPodsKubeconfig: String? = null
        var capturedListPodsNamespace: String? = null
        var capturedListPodsSelector: String? = null
        var listPodsResultToReturn: Result<List<Pod>>? = null

        override fun listPods(
            rawKubeconfig: String,
            namespace: String?,
            labelSelector: String,
            limit: Long,
        ): Result<List<Pod>> {
            capturedListPodsKubeconfig = rawKubeconfig
            capturedListPodsNamespace = namespace
            capturedListPodsSelector = labelSelector
            errorToThrow?.let { throw it }
            return listPodsResultToReturn ?: Result.Success(emptyList())
        }

        override fun createPod(
            rawKubeconfig: String,
            namespace: String,
            manifestYaml: String,
        ): Result<String> {
            capturedKubeconfig = rawKubeconfig
            capturedNamespace = namespace
            capturedManifest = manifestYaml
            errorToThrow?.let { throw it }
            return resultToReturn ?: Result.Success(manifestYaml)
        }
    }

    private class NoOpPodDao : PodDao {
        override fun getPodsStream(clusterId: String): Flow<List<PodEntity>> =
            MutableStateFlow(emptyList())

        override fun getPodsByNamespaceStream(
            clusterId: String,
            namespace: String
        ): Flow<List<PodEntity>> =
            MutableStateFlow(emptyList())

        override suspend fun getPodIdsForCluster(clusterId: String): List<String> = emptyList()

        override suspend fun getPodIdsForNamespace(
            clusterId: String,
            namespace: String
        ): List<String> =
            emptyList()

        override suspend fun getPodsList(clusterId: String): List<PodEntity> = emptyList()

        override suspend fun insertPods(pods: List<PodEntity>) = Unit

        override suspend fun deletePodsForCluster(clusterId: String) = Unit

        override suspend fun deletePodsForNamespace(clusterId: String, namespace: String) = Unit

        override suspend fun deletePod(podId: String) = Unit

        override suspend fun deletePodsByIds(ids: List<String>) = Unit

        override fun getSyncMetadataStream(key: String): Flow<Long?> = MutableStateFlow(null)

        override suspend fun getLastRefreshedTime(key: String): Long? = null

        override suspend fun insertSyncMetadata(metadata: SyncMetadataEntity) = Unit
    }

    private class NoOpNamespaceDao : NamespaceDao {
        override fun getNamespacesStream(clusterId: String): Flow<List<NamespaceEntity>> =
            MutableStateFlow(emptyList())

        override suspend fun insertNamespaces(namespaces: List<NamespaceEntity>) = Unit

        override suspend fun deleteNamespacesForCluster(clusterId: String) = Unit

        override suspend fun deleteNamespace(clusterId: String, name: String) = Unit
    }

    private class FakeClusterDao : ClusterDao() {
        val clusters = mutableMapOf<String, ClusterEntity>()

        override fun observeClusters(): Flow<List<ClusterEntity>> =
            MutableStateFlow(clusters.values.toList())

        override fun observeActiveCluster(): Flow<ClusterEntity?> =
            MutableStateFlow(clusters.values.firstOrNull { it.isActive })

        override suspend fun getClusterById(id: String): ClusterEntity? = clusters[id]

        override suspend fun getAllClusters(): List<ClusterEntity> = clusters.values.toList()

        override suspend fun insertCluster(cluster: ClusterEntity) {
            clusters[cluster.id] = cluster
        }

        override suspend fun updateCluster(cluster: ClusterEntity) {
            clusters[cluster.id] = cluster
        }

        override suspend fun deleteCluster(id: String) {
            clusters.remove(id)
        }

        override suspend fun deletePodsForCluster(id: String) = Unit
        override suspend fun deleteNamespacesForCluster(id: String) = Unit
        override suspend fun deleteAPIResourcesForCluster(id: String) = Unit
        override suspend fun deleteExplainedResourcesForCluster(id: String) = Unit
        override suspend fun deleteOpenApiSchemaForCluster(id: String) = Unit
        override suspend fun deleteSyncMetadataForCluster(id: String) = Unit
        override suspend fun deleteDeploymentsForCluster(id: String) = Unit
        override suspend fun deleteServicesForCluster(id: String) = Unit
        override suspend fun deleteAllPods() = Unit
        override suspend fun deleteAllNamespaces() = Unit
        override suspend fun deleteAllDeployments() = Unit
        override suspend fun deleteAllServices() = Unit
        override suspend fun deleteAllAPIResources() = Unit
        override suspend fun deleteAllExplainedResources() = Unit
        override suspend fun deleteAllOpenApiSchemas() = Unit
        override suspend fun deleteAllSyncMetadata() = Unit

        override suspend fun deactivateAllClusters() {
            clusters.replaceAll { _, v -> v.copy(isActive = false) }
        }

        override suspend fun activateCluster(id: String) {
            clusters[id]?.let { clusters[id] = it.copy(isActive = true) }
        }

        override suspend fun updateClusterName(id: String, name: String) {
            clusters[id]?.let { clusters[id] = it.copy(name = name) }
        }

        override suspend fun updateStatus(id: String, status: String, lastConnectedAt: Long?) {
            clusters[id]?.let {
                clusters[id] = it.copy(status = status, lastConnectedAt = lastConnectedAt)
            }
        }
    }
}
