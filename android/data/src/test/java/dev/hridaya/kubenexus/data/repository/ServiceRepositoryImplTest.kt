package dev.hridaya.kubenexus.data.repository

import dev.hridaya.kubenexus.core.common.dispatcher.DispatcherProvider
import dev.hridaya.kubenexus.core.common.result.AppError
import dev.hridaya.kubenexus.core.common.result.Result
import dev.hridaya.kubenexus.core.security.AesGcmKubeconfigEncryptor
import dev.hridaya.kubenexus.data.nativebridge.FakeKubeNexusNativeBridge
import dev.hridaya.kubenexus.data.source.local.dao.ClusterDao
import dev.hridaya.kubenexus.data.source.local.dao.ServiceDao
import dev.hridaya.kubenexus.data.source.local.entity.ClusterEntity
import dev.hridaya.kubenexus.data.source.local.entity.ServiceEntity
import dev.hridaya.kubenexus.data.source.local.entity.SyncMetadataEntity
import dev.hridaya.kubenexus.domain.model.ServiceDetails
import dev.hridaya.kubenexus.domain.model.ServicePortDetail
import dev.hridaya.kubenexus.domain.model.ServiceSummary
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ServiceRepositoryImplTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testDispatcherProvider = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
        override val unconfined: CoroutineDispatcher = testDispatcher
    }

    private lateinit var fakeDao: FakeClusterDao
    private lateinit var serviceDao: RecordingServiceDao
    private lateinit var encryptor: AesGcmKubeconfigEncryptor
    private lateinit var recordingBridge: RecordingBridge
    private lateinit var repository: ServiceRepositoryImpl

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
        kind: Service
        metadata:
          name: nginx
          namespace: web
        spec:
          selector:
            app: nginx
          type: ClusterIP
          ports:
            - port: 80
              targetPort: 8080
    """.trimIndent()

    @Before
    fun setUp() {
        fakeDao = FakeClusterDao()
        val secretKey = AesGcmKubeconfigEncryptor.generateKey()
        encryptor = AesGcmKubeconfigEncryptor(secretKey)
        recordingBridge = RecordingBridge()
        serviceDao = RecordingServiceDao()

        repository = ServiceRepositoryImpl(
            clusterDao = fakeDao,
            serviceDao = serviceDao,
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
            AppError.Unknown("services denied: token: secret-token-12345 rejected"),
        )

        val result = repository.createFromManifest(
            clusterId = "c-1",
            manifestYaml = sampleManifest,
        )

        assertTrue(result is Result.Error)
        val error = (result as Result.Error).error
        assertTrue(error is AppError.Network)
        assertEquals(
            "services denied: token: [REDACTED] rejected",
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
    fun `syncServices caches summaries and records the services sync key`() =
        runTest(testDispatcher) {
            seedCluster(id = "c-1", rawKubeconfig = encryptor.encrypt(sampleKubeconfig))
            recordingBridge.listResultToReturn = Result.Success(
                listOf(
                    ServiceSummary(
                        id = "web_nginx",
                        name = "nginx",
                        namespace = "web",
                        type = "ClusterIP",
                        clusterIP = "10.96.0.10",
                        ports = listOf(
                            ServicePortDetail(
                                port = 80,
                                targetPort = 8080,
                                nodePort = null,
                                protocol = "TCP",
                                name = "http"
                            ),
                        ),
                        creationTimestampMillis = 1000L,
                    ),
                ),
            )

            val result = repository.syncServices(clusterId = "c-1", namespace = "All Namespaces")

            assertTrue(result is Result.Success)
            assertEquals(null, recordingBridge.capturedListNamespace)

            // Entity ids are clusterId-qualified so two clusters cannot collide.
            assertEquals(listOf("c-1_web_nginx"), serviceDao.syncedIds.single())

            val metadata = serviceDao.syncedMetadata.single()
            assertEquals("c-1_services", metadata.key)
            assertEquals("c-1", metadata.clusterId)
            assertEquals("services", metadata.resourceType)
        }

    @Test
    fun `syncServices fails when the bridge fails and writes no sync metadata`() =
        runTest(testDispatcher) {
            seedCluster(id = "c-1", rawKubeconfig = encryptor.encrypt(sampleKubeconfig))
            recordingBridge.listResultToReturn = Result.Error(
                AppError.Unknown("services denied: token: secret-token-12345 rejected"),
            )

            val result = repository.syncServices(clusterId = "c-1", namespace = "web")

            assertTrue(result is Result.Error)
            assertTrue((result as Result.Error).error is AppError.Network)
            assertTrue(serviceDao.syncedMetadata.isEmpty())
        }

    @Test
    fun `getServicesStream routes a named namespace to the scoped query`() =
        runTest(testDispatcher) {
            repository.getServicesStream(clusterId = "c-1", namespace = "web").first()

            assertEquals(ServiceDaoVariant.NAMESPACE, serviceDao.lastStreamVariant)
        }

    @Test
    fun `getServicesStream maps cached entities back to structured ports`() =
        runTest(testDispatcher) {
            serviceDao.rows.value = listOf(
                ServiceEntity(
                    id = "c-1_web_nginx",
                    clusterId = "c-1",
                    name = "nginx",
                    namespace = "web",
                    type = "NodePort",
                    clusterIp = "10.96.0.10",
                    ports = "http|80|8080|30080|TCP",
                    creationTimestampMillis = 1000L,
                ),
            )

            val summaries =
                repository.getServicesStream(clusterId = "c-1", namespace = null).first()

            assertEquals(ServiceDaoVariant.CLUSTER, serviceDao.lastStreamVariant)
            val port = summaries.single().ports.single()
            assertEquals(80, port.port)
            assertEquals(8080, port.targetPort)
            assertEquals(30080, port.nodePort)
            assertEquals("TCP", port.protocol)
            assertEquals("http", port.name)
        }

    @Test
    fun `getServiceDetails passes decrypted kubeconfig and returns details`() =
        runTest(testDispatcher) {
            seedCluster(id = "c-1", rawKubeconfig = encryptor.encrypt(sampleKubeconfig))
            val expected = ServiceDetails(
                name = "nginx",
                namespace = "web",
                creationTimestampMillis = 1000L,
                type = "ClusterIP",
                clusterIP = "10.96.0.10",
                clusterIPs = listOf("10.96.0.10"),
                externalIPs = emptyList(),
                selector = emptyMap(),
                ports = emptyList(),
                labels = emptyMap(),
                annotations = emptyMap(),
                events = emptyList(),
            )
            recordingBridge.describeResultToReturn = Result.Success(expected)

            val result =
                repository.getServiceDetails(clusterId = "c-1", namespace = "web", name = "nginx")

            assertTrue(result is Result.Success)
            assertEquals(expected, (result as Result.Success).data)
            // The bridge must receive the decrypted plaintext, never the stored ciphertext.
            assertEquals(sampleKubeconfig, recordingBridge.capturedDescribeKubeconfig)
            assertEquals("web", recordingBridge.capturedDescribeNamespace)
            assertEquals("nginx", recordingBridge.capturedDescribeName)
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
     * Records every [createService], [listServices] and [describeService]
     * argument so tests can assert exactly what crossed the bridge, then
     * returns the preconfigured outcome.
     */
    private class RecordingBridge : FakeKubeNexusNativeBridge() {
        var capturedKubeconfig: String? = null
        var capturedNamespace: String? = null
        var capturedManifest: String? = null

        var capturedListNamespace: String? = null
        var listResultToReturn: Result<List<ServiceSummary>>? = null

        var capturedDescribeKubeconfig: String? = null
        var capturedDescribeNamespace: String? = null
        var capturedDescribeName: String? = null
        var describeResultToReturn: Result<ServiceDetails>? = null

        var resultToReturn: Result<String>? = null
        var errorToThrow: Throwable? = null

        override fun createService(
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

        override fun listServices(
            rawKubeconfig: String,
            namespace: String?,
        ): Result<List<ServiceSummary>> {
            capturedKubeconfig = rawKubeconfig
            capturedListNamespace = namespace
            errorToThrow?.let { throw it }
            return listResultToReturn ?: Result.Success(emptyList())
        }

        override fun describeService(
            rawKubeconfig: String,
            namespace: String,
            name: String,
        ): Result<ServiceDetails> {
            capturedDescribeKubeconfig = rawKubeconfig
            capturedDescribeNamespace = namespace
            capturedDescribeName = name
            errorToThrow?.let { throw it }
            return describeResultToReturn
                ?: Result.Error(AppError.NotFound("Service '$name' not found"))
        }
    }

    /** Which stream query the repository picked, so namespace scoping is observable. */
    private enum class ServiceDaoVariant { CLUSTER, NAMESPACE }

    /**
     * In-memory [ServiceDao]. The @Transaction default methods run for real,
     * so sync tests exercise the actual delete/upsert/metadata sequence through
     * these recorded primitives.
     */
    private class RecordingServiceDao : ServiceDao {
        val rows = MutableStateFlow<List<ServiceEntity>>(emptyList())
        var lastStreamVariant: ServiceDaoVariant? = null

        val syncedIds = mutableListOf<List<String>>()
        val syncedMetadata = mutableListOf<SyncMetadataEntity>()

        private val storedIds = mutableSetOf<String>()

        override fun getServicesStream(clusterId: String): Flow<List<ServiceEntity>> {
            lastStreamVariant = ServiceDaoVariant.CLUSTER
            return rows
        }

        override fun getServicesByNamespaceStream(
            clusterId: String,
            namespace: String
        ): Flow<List<ServiceEntity>> {
            lastStreamVariant = ServiceDaoVariant.NAMESPACE
            return rows
        }

        override suspend fun getServiceIdsForCluster(clusterId: String): List<String> =
            storedIds.toList()

        override suspend fun getServiceIdsForNamespace(
            clusterId: String,
            namespace: String
        ): List<String> =
            storedIds.filter { it.endsWith("_${namespace}") }

        override suspend fun insertServices(services: List<ServiceEntity>) {
            services.forEach { entity ->
                storedIds.add(entity.id)
                rows.value = rows.value.filterNot { it.id == entity.id } + entity
            }
        }

        override suspend fun deleteServicesByIds(ids: List<String>) {
            storedIds.removeAll(ids.toSet())
            rows.value = rows.value.filterNot { it.id in ids }
        }

        override suspend fun syncServices(
            clusterId: String,
            namespace: String?,
            services: List<ServiceEntity>,
            timestamp: Long,
            chunkSize: Int,
        ) {
            // Record the requested scope, then let the real @Transaction body run.
            syncedIds.add(services.map { it.id })
            super.syncServices(clusterId, namespace, services, timestamp, chunkSize)
        }

        override fun getSyncMetadataStream(key: String): Flow<Long?> = MutableStateFlow(null)

        override suspend fun getLastRefreshedTime(key: String): Long? = null

        override suspend fun insertSyncMetadata(metadata: SyncMetadataEntity) {
            syncedMetadata.add(metadata)
        }
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
