package dev.hridaya.kubenexus.data.repository

import dev.hridaya.kubenexus.core.common.dispatcher.DispatcherProvider
import dev.hridaya.kubenexus.core.common.result.AppError
import dev.hridaya.kubenexus.core.common.result.Result
import dev.hridaya.kubenexus.core.security.AesGcmKubeconfigEncryptor
import dev.hridaya.kubenexus.data.nativebridge.FakeKubeNexusNativeBridge
import dev.hridaya.kubenexus.data.source.local.dao.ClusterDao
import dev.hridaya.kubenexus.data.source.local.dao.DeploymentDao
import dev.hridaya.kubenexus.data.source.local.dao.NamespaceDao
import dev.hridaya.kubenexus.data.source.local.dao.PodDao
import dev.hridaya.kubenexus.data.source.local.entity.ClusterEntity
import dev.hridaya.kubenexus.data.source.local.entity.DeploymentEntity
import dev.hridaya.kubenexus.data.source.local.entity.NamespaceEntity
import dev.hridaya.kubenexus.data.source.local.entity.PodEntity
import dev.hridaya.kubenexus.data.source.local.entity.SyncMetadataEntity
import dev.hridaya.kubenexus.domain.model.DeploymentDetails
import dev.hridaya.kubenexus.domain.model.DeploymentSummary
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DeploymentRepositoryImplTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testDispatcherProvider = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
        override val unconfined: CoroutineDispatcher = testDispatcher
    }

    private lateinit var fakeDao: FakeClusterDao
    private lateinit var deploymentDao: RecordingDeploymentDao
    private lateinit var encryptor: AesGcmKubeconfigEncryptor
    private lateinit var recordingBridge: RecordingBridge
    private lateinit var repository: DeploymentRepositoryImpl
    private lateinit var podRepository: PodRepositoryImpl

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
        apiVersion: apps/v1
        kind: Deployment
        metadata:
          name: nginx
          namespace: web
        spec:
          replicas: 2
    """.trimIndent()

    @Before
    fun setUp() {
        fakeDao = FakeClusterDao()
        val secretKey = AesGcmKubeconfigEncryptor.generateKey()
        encryptor = AesGcmKubeconfigEncryptor(secretKey)
        recordingBridge = RecordingBridge()
        deploymentDao = RecordingDeploymentDao()

        repository = DeploymentRepositoryImpl(
            clusterDao = fakeDao,
            deploymentDao = deploymentDao,
            nativeBridge = recordingBridge,
            encryptor = encryptor,
            dispatcherProvider = testDispatcherProvider,
        )

        podRepository = PodRepositoryImpl(
            clusterDao = fakeDao,
            podDao = NoOpPodDao(),
            namespaceDao = NoOpNamespaceDao(),
            nativeBridge = recordingBridge,
            encryptor = encryptor,
            dispatcherProvider = testDispatcherProvider,
        )
    }

    @Test
    fun `createFromManifest fails when no cluster is selected`() = runTest(testDispatcher) {
        val result = repository.createFromManifest(clusterId = null, manifestYaml = sampleManifest)

        assertTrue(result is Result.Error)
        assertEquals(
            "No cluster selected",
            (result as Result.Error).error.message,
        )
    }

    @Test
    fun `createFromManifest fails when cluster id is unknown`() = runTest(testDispatcher) {
        val result = repository.createFromManifest(
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
    fun `createFromManifest decrypts kubeconfig and applies reviewed manifest verbatim`() =
        runTest(testDispatcher) {
            seedCluster(id = "c-1", rawKubeconfig = encryptor.encrypt(sampleKubeconfig))

            val result = repository.createFromManifest(
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
    fun `createFromManifest surfaces sanitized bridge error messages`() = runTest(testDispatcher) {
        seedCluster(id = "c-1", rawKubeconfig = encryptor.encrypt(sampleKubeconfig))
        recordingBridge.resultToReturn = Result.Error(
            AppError.Unknown("deployment.apps denied: token: secret-token-12345 rejected"),
        )

        val result = repository.createFromManifest(
            clusterId = "c-1",
            manifestYaml = sampleManifest,
        )

        assertTrue(result is Result.Error)
        val error = (result as Result.Error).error
        assertTrue(error is AppError.Network)
        assertEquals(
            "deployment.apps denied: token: [REDACTED] rejected",
            error.message,
        )
    }

    @Test
    fun `createFromManifest maps bridge exceptions to network errors`() = runTest(testDispatcher) {
        seedCluster(id = "c-1", rawKubeconfig = encryptor.encrypt(sampleKubeconfig))
        recordingBridge.errorToThrow = RuntimeException("connection reset by peer")

        val result = repository.createFromManifest(
            clusterId = "c-1",
            manifestYaml = sampleManifest,
        )

        assertTrue(result is Result.Error)
        val error = (result as Result.Error).error
        assertTrue(error is AppError.Network)
        assertEquals("connection reset by peer", error.message)
    }

    @Test
    fun `createFromManifest applies multi-document yaml manifests for deployment and service`() =
        runTest(testDispatcher) {
            seedCluster(id = "c-1", rawKubeconfig = encryptor.encrypt(sampleKubeconfig))

            val multiDocManifest = """
                apiVersion: apps/v1
                kind: Deployment
                metadata:
                  name: web-app
                  namespace: default
                ---
                apiVersion: v1
                kind: Service
                metadata:
                  name: web-app
                  namespace: default
            """.trimIndent()

            val result = repository.createFromManifest(
                clusterId = "c-1",
                manifestYaml = multiDocManifest,
            )

            assertTrue(result is Result.Success)
            assertEquals(1, recordingBridge.createdDeployments.size)
            assertEquals(1, recordingBridge.createdServices.size)
            assertTrue(recordingBridge.createdDeployments.single().contains("kind: Deployment"))
            assertTrue(recordingBridge.createdServices.single().contains("kind: Service"))
        }

    @Test
    fun `getDeployments fails when no cluster is selected`() = runTest(testDispatcher) {
        val result = repository.getDeployments(clusterId = null, namespace = "default")

        assertTrue(result is Result.Error)
        assertEquals(
            "No cluster selected",
            (result as Result.Error).error.message,
        )
    }

    @Test
    fun `getDeployments fails when cluster id is unknown`() = runTest(testDispatcher) {
        val result = repository.getDeployments(clusterId = "c-missing", namespace = "default")

        assertTrue(result is Result.Error)
        assertTrue((result as Result.Error).error is AppError.NotFound)
        assertEquals(
            "Cluster 'c-missing' not found",
            result.error.message,
        )
    }

    @Test
    fun `getDeployments maps summaries and passes namespace verbatim`() = runTest(testDispatcher) {
        seedCluster(id = "c-1", rawKubeconfig = encryptor.encrypt(sampleKubeconfig))
        val summary = DeploymentSummary(
            id = "web_nginx",
            name = "nginx",
            namespace = "web",
            desiredReplicas = 3,
            readyReplicas = 3,
            availableReplicas = 3,
            images = listOf("nginx:1.25"),
            creationTimestampMillis = 1000L,
        )
        recordingBridge.listResultToReturn = Result.Success(listOf(summary))

        // A blank namespace means all namespaces and must reach the bridge as-is.
        val result = repository.getDeployments(clusterId = "c-1", namespace = "")

        assertTrue(result is Result.Success)
        assertEquals(listOf(summary), (result as Result.Success).data)
        assertEquals(sampleKubeconfig, recordingBridge.capturedKubeconfig)
        assertEquals("", recordingBridge.capturedListNamespace)
    }

    @Test
    fun `getDeployments surfaces sanitized bridge error messages`() = runTest(testDispatcher) {
        seedCluster(id = "c-1", rawKubeconfig = encryptor.encrypt(sampleKubeconfig))
        recordingBridge.listResultToReturn = Result.Error(
            AppError.Unknown("deployments.apps denied: token: secret-token-12345 rejected"),
        )

        val result = repository.getDeployments(clusterId = "c-1", namespace = "web")

        assertTrue(result is Result.Error)
        val error = (result as Result.Error).error
        assertTrue(error is AppError.Network)
        assertEquals(
            "deployments.apps denied: token: [REDACTED] rejected",
            error.message,
        )
    }

    @Test
    fun `syncDeployments fails when no cluster is selected`() = runTest(testDispatcher) {
        val result = repository.syncDeployments(clusterId = null, namespace = "default")

        assertTrue(result is Result.Error)
        assertEquals(
            "No active cluster specified",
            (result as Result.Error).error.message,
        )
    }

    @Test
    fun `syncDeployments caches summaries and records the deployments sync key`() =
        runTest(testDispatcher) {
            seedCluster(id = "c-1", rawKubeconfig = encryptor.encrypt(sampleKubeconfig))
            recordingBridge.listResultToReturn = Result.Success(
                listOf(
                    DeploymentSummary(
                        id = "web_nginx",
                        name = "nginx",
                        namespace = "web",
                        desiredReplicas = 2,
                        readyReplicas = 2,
                        availableReplicas = 2,
                        images = listOf("nginx:1.25", "sidecar:1.0"),
                        creationTimestampMillis = 1000L,
                    ),
                ),
            )

            // "All Namespaces" is the UI's no-filter sentinel and must sync the
            // whole cluster, reaching the bridge as null.
            val result = repository.syncDeployments(clusterId = "c-1", namespace = "All Namespaces")

            assertTrue(result is Result.Success)
            assertEquals(null, recordingBridge.capturedListNamespace)

            // Entity ids are clusterId-qualified so two clusters cannot collide.
            assertEquals(listOf("c-1_web_nginx"), deploymentDao.syncedIds.single())
            // The DAO sees the normalized null scope, matching what was fetched.
            assertNull(deploymentDao.syncedNamespaces.single())

            val metadata = deploymentDao.syncedMetadata.single()
            assertEquals("c-1_deployments", metadata.key)
            assertEquals("c-1", metadata.clusterId)
            assertEquals("deployments", metadata.resourceType)
        }

    @Test
    fun `syncDeployments scopes a named namespace to that namespace only`() =
        runTest(testDispatcher) {
            seedCluster(id = "c-1", rawKubeconfig = encryptor.encrypt(sampleKubeconfig))

            val result = repository.syncDeployments(clusterId = "c-1", namespace = "web")

            assertTrue(result is Result.Success)
            assertEquals("web", recordingBridge.capturedListNamespace)
            assertEquals(listOf("web"), deploymentDao.namespaceLookups)
        }

    @Test
    fun `getDeploymentsStream maps cached entities and picks the whole-cluster query`() =
        runTest(testDispatcher) {
            deploymentDao.rows.value = listOf(
                DeploymentEntity(
                    id = "c-1_web_nginx",
                    clusterId = "c-1",
                    name = "nginx",
                    namespace = "web",
                    desiredReplicas = 2,
                    readyReplicas = 1,
                    availableReplicas = 1,
                    updatedReplicas = 0,
                    creationTimestampMillis = 1000L,
                    images = "nginx:1.25,sidecar:1.0",
                ),
            )

            val summaries =
                repository.getDeploymentsStream(clusterId = "c-1", namespace = null).first()

            assertEquals(DeploymentDaoVariant.CLUSTER, deploymentDao.lastStreamVariant)
            assertEquals(1, summaries.size)
            // Comma-joined storage round-trips into a real image list.
            assertEquals(listOf("nginx:1.25", "sidecar:1.0"), summaries.single().images)
        }

    @Test
    fun `getDeploymentsStream routes a named namespace to the scoped query`() =
        runTest(testDispatcher) {
            repository.getDeploymentsStream(clusterId = "c-1", namespace = "web").first()

            assertEquals(DeploymentDaoVariant.NAMESPACE, deploymentDao.lastStreamVariant)
        }

    @Test
    fun `getDeploymentDetails passes decrypted kubeconfig and returns details`() =
        runTest(testDispatcher) {
            seedCluster(id = "c-1", rawKubeconfig = encryptor.encrypt(sampleKubeconfig))
            val expected = DeploymentDetails(
                name = "nginx",
                namespace = "web",
                creationTimestampMillis = 1000L,
                desiredReplicas = 2,
                readyReplicas = 2,
                availableReplicas = 2,
                updatedReplicas = 2,
                strategyType = null,
                minReadySeconds = null,
                selectorMatchLabels = emptyMap(),
                labels = emptyMap(),
                annotations = emptyMap(),
                conditions = emptyList(),
                images = listOf("nginx:1.25"),
                events = emptyList(),
            )
            recordingBridge.describeResultToReturn = Result.Success(expected)

            val result = repository.getDeploymentDetails(
                clusterId = "c-1",
                namespace = "web",
                name = "nginx"
            )

            assertTrue(result is Result.Success)
            assertEquals(expected, (result as Result.Success).data)
            assertEquals(sampleKubeconfig, recordingBridge.capturedKubeconfig)
            assertEquals("web", recordingBridge.capturedDescribeNamespace)
            assertEquals("nginx", recordingBridge.capturedDescribeName)
        }

    @Test
    fun `getDeploymentDetails surfaces sanitized bridge error messages`() =
        runTest(testDispatcher) {
            seedCluster(id = "c-1", rawKubeconfig = encryptor.encrypt(sampleKubeconfig))
            recordingBridge.describeResultToReturn = Result.Error(
                AppError.Unknown("describe denied: token: secret-token-12345 rejected"),
            )

            val result = repository.getDeploymentDetails(
                clusterId = "c-1",
                namespace = "web",
                name = "nginx"
            )

            assertTrue(result is Result.Error)
            val error = (result as Result.Error).error
            assertTrue(error is AppError.Network)
            assertEquals(
                "describe denied: token: [REDACTED] rejected",
                error.message,
            )
        }

    @Test
    fun `createNamespace fails when no cluster is selected`() = runTest(testDispatcher) {
        val result = podRepository.createNamespace(clusterId = null, name = "team-a")

        assertTrue(result is Result.Error)
        assertEquals(
            "No cluster selected",
            (result as Result.Error).error.message,
        )
    }

    @Test
    fun `createNamespace passes decrypted kubeconfig and name to the bridge`() =
        runTest(testDispatcher) {
            seedCluster(id = "c-1", rawKubeconfig = encryptor.encrypt(sampleKubeconfig))

            val result = podRepository.createNamespace(clusterId = "c-1", name = "team-a")

            assertTrue(result is Result.Success)
            assertEquals(Unit, (result as Result.Success).data)
            // The bridge must receive the decrypted plaintext, never the stored ciphertext.
            assertEquals(sampleKubeconfig, recordingBridge.capturedCreateNamespaceKubeconfig)
            assertEquals("team-a", recordingBridge.capturedCreateNamespaceName)
        }

    @Test
    fun `createNamespace surfaces sanitized bridge error messages`() = runTest(testDispatcher) {
        seedCluster(id = "c-1", rawKubeconfig = encryptor.encrypt(sampleKubeconfig))
        recordingBridge.createNamespaceResultToReturn = Result.Error(
            AppError.Unknown("namespaces denied: token: secret-token-12345 rejected"),
        )

        val result = podRepository.createNamespace(clusterId = "c-1", name = "team-a")

        assertTrue(result is Result.Error)
        val error = (result as Result.Error).error
        assertTrue(error is AppError.Network)
        assertEquals(
            "namespaces denied: token: [REDACTED] rejected",
            error.message,
        )
    }

    @Test
    fun `scaleDeployment decrypts kubeconfig and passes replicas to bridge`() = runTest(testDispatcher) {
        seedCluster(id = "c-1", rawKubeconfig = encryptor.encrypt(sampleKubeconfig))
        recordingBridge.listResultToReturn = Result.Success(emptyList())

        val result = repository.scaleDeployment(
            clusterId = "c-1",
            namespace = "web",
            name = "nginx",
            replicas = 5,
        )

        assertTrue(result is Result.Success)
        assertEquals(sampleKubeconfig, recordingBridge.capturedScaleKubeconfig)
        assertEquals("web", recordingBridge.capturedScaleNamespace)
        assertEquals("nginx", recordingBridge.capturedScaleName)
        assertEquals(5, recordingBridge.capturedScaleReplicas)
        // Sync was invoked to refresh cached state
        assertTrue(recordingBridge.capturedListNamespace == "web")
    }

    @Test
    fun `scaleDeployment surfaces sanitized error when bridge fails`() = runTest(testDispatcher) {
        seedCluster(id = "c-1", rawKubeconfig = encryptor.encrypt(sampleKubeconfig))
        recordingBridge.scaleResultToReturn = Result.Error(
            AppError.Unknown("forbidden: token: secret-token-12345 cannot scale"),
        )

        val result = repository.scaleDeployment(
            clusterId = "c-1",
            namespace = "web",
            name = "nginx",
            replicas = 5,
        )

        assertTrue(result is Result.Error)
        val error = (result as Result.Error).error
        assertEquals("forbidden: token: [REDACTED] cannot scale", error.message)
    }

    @Test
    fun `restartDeployment decrypts kubeconfig and triggers rollout restart`() = runTest(testDispatcher) {
        seedCluster(id = "c-1", rawKubeconfig = encryptor.encrypt(sampleKubeconfig))
        recordingBridge.listResultToReturn = Result.Success(emptyList())

        val result = repository.restartDeployment(
            clusterId = "c-1",
            namespace = "web",
            name = "nginx",
        )

        assertTrue(result is Result.Success)
        assertEquals(sampleKubeconfig, recordingBridge.capturedRestartKubeconfig)
        assertEquals("web", recordingBridge.capturedRestartNamespace)
        assertEquals("nginx", recordingBridge.capturedRestartName)
    }

    @Test
    fun `deleteDeployment deletes from DAO, bridge and refreshes cache`() = runTest(testDispatcher) {
        seedCluster(id = "c-1", rawKubeconfig = encryptor.encrypt(sampleKubeconfig))
        deploymentDao.insertDeployments(
            listOf(
                DeploymentEntity(
                    id = "c-1_web_nginx",
                    clusterId = "c-1",
                    name = "nginx",
                    namespace = "web",
                    desiredReplicas = 2,
                    readyReplicas = 2,
                    availableReplicas = 2,
                    updatedReplicas = 2,
                    creationTimestampMillis = 1000L,
                    images = "nginx:1.27",
                ),
            ),
        )
        recordingBridge.listResultToReturn = Result.Success(emptyList())

        val result = repository.deleteDeployment(
            clusterId = "c-1",
            namespace = "web",
            name = "nginx",
        )

        assertTrue(result is Result.Success)
        assertEquals(sampleKubeconfig, recordingBridge.capturedDeleteKubeconfig)
        assertEquals("web", recordingBridge.capturedDeleteNamespace)
        assertEquals("nginx", recordingBridge.capturedDeleteName)
        // Verified removed from DAO
        assertTrue(deploymentDao.rows.value.isEmpty())
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
     * Records every [createDeployment], [listDeployments], [describeDeployment]
     * and [createNamespace] argument so tests can assert exactly what crossed
     * the bridge, then returns the preconfigured outcome.
     */
    private class RecordingBridge : FakeKubeNexusNativeBridge() {
        var capturedKubeconfig: String? = null
        var capturedNamespace: String? = null
        var capturedManifest: String? = null
        val createdDeployments = mutableListOf<String>()
        val createdServices = mutableListOf<String>()
        val createdPods = mutableListOf<String>()

        var capturedListNamespace: String? = null
        var listResultToReturn: Result<List<DeploymentSummary>>? = null

        var capturedDescribeNamespace: String? = null
        var capturedDescribeName: String? = null
        var describeResultToReturn: Result<DeploymentDetails>? = null

        var capturedCreateNamespaceKubeconfig: String? = null
        var capturedCreateNamespaceName: String? = null
        var createNamespaceResultToReturn: Result<Unit>? = null

        var capturedScaleKubeconfig: String? = null
        var capturedScaleNamespace: String? = null
        var capturedScaleName: String? = null
        var capturedScaleReplicas: Int? = null
        var scaleResultToReturn: Result<Unit>? = null

        var capturedRestartKubeconfig: String? = null
        var capturedRestartNamespace: String? = null
        var capturedRestartName: String? = null
        var restartResultToReturn: Result<Unit>? = null

        var capturedDeleteKubeconfig: String? = null
        var capturedDeleteNamespace: String? = null
        var capturedDeleteName: String? = null
        var deleteResultToReturn: Result<Unit>? = null

        var resultToReturn: Result<String>? = null
        var errorToThrow: Throwable? = null

        override fun createDeployment(
            rawKubeconfig: String,
            namespace: String,
            manifestYaml: String,
        ): Result<String> {
            capturedKubeconfig = rawKubeconfig
            capturedNamespace = namespace
            capturedManifest = manifestYaml
            createdDeployments.add(manifestYaml)
            errorToThrow?.let { throw it }
            return resultToReturn ?: Result.Success(manifestYaml)
        }

        override fun createService(
            rawKubeconfig: String,
            namespace: String,
            manifestYaml: String,
        ): Result<String> {
            capturedKubeconfig = rawKubeconfig
            capturedNamespace = namespace
            capturedManifest = manifestYaml
            createdServices.add(manifestYaml)
            errorToThrow?.let { throw it }
            return resultToReturn ?: Result.Success(manifestYaml)
        }

        override fun createPod(
            rawKubeconfig: String,
            namespace: String,
            manifestYaml: String,
        ): Result<String> {
            capturedKubeconfig = rawKubeconfig
            capturedNamespace = namespace
            capturedManifest = manifestYaml
            createdPods.add(manifestYaml)
            errorToThrow?.let { throw it }
            return resultToReturn ?: Result.Success(manifestYaml)
        }

        override fun listDeployments(
            rawKubeconfig: String,
            namespace: String?,
        ): Result<List<DeploymentSummary>> {
            capturedKubeconfig = rawKubeconfig
            capturedListNamespace = namespace
            errorToThrow?.let { throw it }
            return listResultToReturn ?: Result.Success(emptyList())
        }

        override fun describeDeployment(
            rawKubeconfig: String,
            namespace: String,
            name: String,
        ): Result<DeploymentDetails> {
            capturedKubeconfig = rawKubeconfig
            capturedDescribeNamespace = namespace
            capturedDescribeName = name
            errorToThrow?.let { throw it }
            return describeResultToReturn
                ?: Result.Error(AppError.NotFound("Deployment '$name' not found"))
        }

        override fun createNamespace(rawKubeconfig: String, name: String): Result<Unit> {
            capturedCreateNamespaceKubeconfig = rawKubeconfig
            capturedCreateNamespaceName = name
            errorToThrow?.let { throw it }
            return createNamespaceResultToReturn ?: Result.Success(Unit)
        }

        override fun scaleDeployment(
            rawKubeconfig: String,
            namespace: String,
            name: String,
            replicas: Int,
        ): Result<Unit> {
            capturedScaleKubeconfig = rawKubeconfig
            capturedScaleNamespace = namespace
            capturedScaleName = name
            capturedScaleReplicas = replicas
            errorToThrow?.let { throw it }
            return scaleResultToReturn ?: Result.Success(Unit)
        }

        override fun restartDeployment(
            rawKubeconfig: String,
            namespace: String,
            name: String,
        ): Result<Unit> {
            capturedRestartKubeconfig = rawKubeconfig
            capturedRestartNamespace = namespace
            capturedRestartName = name
            errorToThrow?.let { throw it }
            return restartResultToReturn ?: Result.Success(Unit)
        }

        override fun deleteDeployment(
            rawKubeconfig: String,
            namespace: String,
            name: String,
        ): Result<Unit> {
            capturedDeleteKubeconfig = rawKubeconfig
            capturedDeleteNamespace = namespace
            capturedDeleteName = name
            errorToThrow?.let { throw it }
            return deleteResultToReturn ?: Result.Success(Unit)
        }
    }

    /** Which stream query the repository picked, so namespace scoping is observable. */
    private enum class DeploymentDaoVariant { CLUSTER, NAMESPACE }

    /**
     * In-memory [DeploymentDao]. The @Transaction default methods run for real,
     * so sync tests exercise the actual delete/upsert/metadata sequence through
     * these recorded primitives.
     */
    private class RecordingDeploymentDao : DeploymentDao {
        val rows = MutableStateFlow<List<DeploymentEntity>>(emptyList())
        var lastStreamVariant: DeploymentDaoVariant? = null

        val namespaceLookups = mutableListOf<String>()
        val syncedIds = mutableListOf<List<String>>()
        val syncedNamespaces = mutableListOf<String?>()
        val syncedMetadata = mutableListOf<SyncMetadataEntity>()

        private val storedIds = mutableSetOf<String>()

        override suspend fun syncDeployments(
            clusterId: String,
            namespace: String?,
            deployments: List<DeploymentEntity>,
            timestamp: Long,
            chunkSize: Int,
        ) {
            // Record the requested scope, then let the real @Transaction body run.
            syncedNamespaces.add(namespace)
            syncedIds.add(deployments.map { it.id })
            super.syncDeployments(clusterId, namespace, deployments, timestamp, chunkSize)
        }

        override fun getDeploymentsStream(clusterId: String): Flow<List<DeploymentEntity>> {
            lastStreamVariant = DeploymentDaoVariant.CLUSTER
            return rows
        }

        override fun getDeploymentsByNamespaceStream(
            clusterId: String,
            namespace: String
        ): Flow<List<DeploymentEntity>> {
            lastStreamVariant = DeploymentDaoVariant.NAMESPACE
            return rows
        }

        override suspend fun getDeploymentIdsForCluster(clusterId: String): List<String> =
            storedIds.toList()

        override suspend fun getDeploymentIdsForNamespace(
            clusterId: String,
            namespace: String
        ): List<String> {
            namespaceLookups.add(namespace)
            return storedIds.filter { it.endsWith("_${namespace}") }
        }

        override suspend fun insertDeployments(deployments: List<DeploymentEntity>) {
            deployments.forEach { entity ->
                storedIds.add(entity.id)
                rows.value = rows.value.filterNot { it.id == entity.id } + entity
            }
        }

        override suspend fun deleteDeploymentsByIds(ids: List<String>) {
            storedIds.removeAll(ids.toSet())
            rows.value = rows.value.filterNot { it.id in ids }
        }

        override suspend fun deleteDeployment(clusterId: String, namespace: String, name: String) {
            val id = "${clusterId}_${namespace}_$name"
            storedIds.remove(id)
            rows.value = rows.value.filterNot { it.clusterId == clusterId && it.namespace == namespace && it.name == name }
        }

        override fun getSyncMetadataStream(key: String): Flow<Long?> = MutableStateFlow(null)

        override suspend fun getLastRefreshedTime(key: String): Long? = null

        override suspend fun insertSyncMetadata(metadata: SyncMetadataEntity) {
            syncedMetadata.add(metadata)
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
