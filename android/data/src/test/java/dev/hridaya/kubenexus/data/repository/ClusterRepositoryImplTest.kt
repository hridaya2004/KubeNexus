package dev.hridaya.kubenexus.data.repository

import dev.hridaya.kubenexus.core.common.dispatcher.DispatcherProvider
import dev.hridaya.kubenexus.core.common.result.Result
import dev.hridaya.kubenexus.core.security.AesGcmKubeconfigEncryptor
import dev.hridaya.kubenexus.data.kubeconfig.ClusterConnectionTester
import dev.hridaya.kubenexus.data.nativebridge.FakeKubeNexusNativeBridge
import dev.hridaya.kubenexus.data.source.local.dao.ClusterDao
import dev.hridaya.kubenexus.data.source.local.entity.ClusterEntity
import dev.hridaya.kubenexus.domain.model.ParsedKubeconfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ClusterRepositoryImplTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testDispatcherProvider = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
        override val unconfined: CoroutineDispatcher = testDispatcher
    }

    private lateinit var fakeDao: FakeClusterDao
    private lateinit var encryptor: AesGcmKubeconfigEncryptor
    private lateinit var fakeTester: ClusterConnectionTester
    private lateinit var repository: ClusterRepositoryImpl

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

    @Before
    fun setUp() {
        fakeDao = FakeClusterDao()
        val secretKey = AesGcmKubeconfigEncryptor.generateKey()
        encryptor = AesGcmKubeconfigEncryptor(secretKey)

        val fakeNativeBridge = FakeKubeNexusNativeBridge()

        fakeTester = object : ClusterConnectionTester(fakeNativeBridge) {
            override fun testConnection(parsed: ParsedKubeconfig): String =
                "Reachable & Healthy (HTTP 200 OK)"
        }

        repository = ClusterRepositoryImpl(
            clusterDao = fakeDao,
            connectionTester = fakeTester,
            nativeBridge = fakeNativeBridge,
            encryptor = encryptor,
            dispatcherProvider = testDispatcherProvider,
        )
    }

    @Test
    fun `addCluster encrypts kubeconfig before persisting to DAO`() = runTest(testDispatcher) {
        val result = repository.addCluster(
            kubeconfigRaw = sampleKubeconfig,
            customName = "Encrypted Cluster",
            setAsActive = true,
        )

        assertTrue(result is Result.Success)
        val addedCluster = (result as Result.Success).data
        assertEquals("Encrypted Cluster", addedCluster.name)
        // Domain model holds decrypted plaintext for immediate consumption
        assertEquals(sampleKubeconfig, addedCluster.rawKubeconfig)

        // Verify the persisted entity in DAO is encrypted
        val persistedEntity = fakeDao.getClusterById(addedCluster.id)
        assertNotNull(persistedEntity)
        assertTrue(persistedEntity!!.rawKubeconfig.startsWith("enc:v1:"))
        assertFalse(persistedEntity.rawKubeconfig.contains("secret-token-12345"))
    }

    @Test
    fun `adding an active cluster leaves it the only active one, and an inactive one changes nothing`() =
        runTest(testDispatcher) {
            val first = (repository.addCluster(sampleKubeconfig, "First", setAsActive = true) as Result.Success).data
            val second = (repository.addCluster(sampleKubeconfig, "Second", setAsActive = true) as Result.Success).data
            val third = (repository.addCluster(sampleKubeconfig, "Third", setAsActive = false) as Result.Success).data

            assertEquals(listOf(second.id), fakeDao.getAllClusters().filter { it.isActive }.map { it.id })
            assertFalse(fakeDao.getClusterById(first.id)!!.isActive)
            assertFalse(fakeDao.getClusterById(third.id)!!.isActive)
        }

    @Test
    fun `getClustersStream and getClusterById decrypt stored encrypted kubeconfig`() =
        runTest(testDispatcher) {
            val encryptedKubeconfig = encryptor.encrypt(sampleKubeconfig)
            fakeDao.insertCluster(
                ClusterEntity(
                    id = "c-100",
                    name = "Test Prod",
                    serverUrl = "https://k8s.example.com",
                    rawKubeconfig = encryptedKubeconfig,
                    contextName = "prod-ctx",
                    userName = "admin",
                    namespace = "default",
                    isActive = true,
                    createdAt = 1000L,
                    lastConnectedAt = null,
                    status = "CONNECTED",
                ),
            )

            val cluster = repository.getClusterById("c-100")
            assertNotNull(cluster)
            assertEquals(sampleKubeconfig, cluster!!.rawKubeconfig)

            val streamList = repository.getClustersStream().first()
            assertEquals(1, streamList.size)
            assertEquals(sampleKubeconfig, streamList[0].rawKubeconfig)
        }

    @Test
    fun `migratePlaintextClusters encrypts all plaintext records without losing data`() =
        runTest(testDispatcher) {
            // Insert legacy plaintext clusters
            val legacy1 = ClusterEntity(
                id = "c-legacy-1",
                name = "Legacy Cluster 1",
                serverUrl = "https://10.0.0.1:6443",
                rawKubeconfig = sampleKubeconfig,
                contextName = "ctx-1",
                userName = "user-1",
                namespace = "default",
                isActive = true,
                createdAt = 1000L,
                lastConnectedAt = null,
                status = "CONNECTED",
            )
            val legacy2 = ClusterEntity(
                id = "c-legacy-2",
                name = "Legacy Cluster 2",
                serverUrl = "https://10.0.0.2:6443",
                rawKubeconfig = sampleKubeconfig,
                contextName = "ctx-2",
                userName = "user-2",
                namespace = "kube-system",
                isActive = false,
                createdAt = 2000L,
                lastConnectedAt = null,
                status = "DISCONNECTED",
            )
            fakeDao.insertCluster(legacy1)
            fakeDao.insertCluster(legacy2)

            // Prior to migration, DAO stores plaintext
            assertEquals(sampleKubeconfig, fakeDao.getClusterById("c-legacy-1")!!.rawKubeconfig)

            // Run migration
            val migrationResult = repository.migratePlaintextClusters()
            assertTrue(migrationResult is Result.Success)
            assertEquals(2, (migrationResult as Result.Success).data)

            // After migration, both records in DAO are encrypted at rest
            val entity1 = fakeDao.getClusterById("c-legacy-1")!!
            val entity2 = fakeDao.getClusterById("c-legacy-2")!!
            assertTrue(entity1.rawKubeconfig.startsWith("enc:v1:"))
            assertTrue(entity2.rawKubeconfig.startsWith("enc:v1:"))

            // Verify repository reading still decrypts both properly
            val domain1 = repository.getClusterById("c-legacy-1")!!
            val domain2 = repository.getClusterById("c-legacy-2")!!
            assertEquals(sampleKubeconfig, domain1.rawKubeconfig)
            assertEquals(sampleKubeconfig, domain2.rawKubeconfig)

            // Running migration again is idempotent (0 records migrated)
            val secondMigration = repository.migratePlaintextClusters()
            assertTrue(secondMigration is Result.Success)
            assertEquals(0, (secondMigration as Result.Success).data)
        }

    @Test
    fun `checkClusterHealth queries health status successfully`() = runTest(testDispatcher) {
        val addResult = repository.addCluster(sampleKubeconfig, "Test Cluster")
        assertTrue(addResult is Result.Success)
        val clusterId = (addResult as Result.Success).data.id

        val healthResult = repository.checkClusterHealth(clusterId)
        assertTrue(healthResult is Result.Success)
        val health = (healthResult as Result.Success).data
        assertTrue(health.livez)
        assertTrue(health.readyz)
        assertEquals("v1.30.0", health.serverVersion)
        assertEquals("Ready", health.statusMessage)
    }

    @Test
    fun `checkClusterHealthByKubeconfig queries health directly`() = runTest(testDispatcher) {
        val healthResult = repository.checkClusterHealthByKubeconfig(sampleKubeconfig)
        assertTrue(healthResult is Result.Success)
        val health = (healthResult as Result.Success).data
        assertTrue(health.livez)
        assertEquals("Ready", health.statusMessage)
    }

    private class FakeClusterDao : ClusterDao() {
        private val storage = mutableMapOf<String, ClusterEntity>()
        private val clustersFlow = MutableStateFlow<List<ClusterEntity>>(emptyList())

        private fun notifyChanges() {
            clustersFlow.value = storage.values.toList()
        }

        override fun observeClusters(): Flow<List<ClusterEntity>> = clustersFlow.asStateFlow()

        override fun observeActiveCluster(): Flow<ClusterEntity?> =
            clustersFlow.map { list -> list.firstOrNull { it.isActive } }

        override suspend fun getClusterById(id: String): ClusterEntity? = storage[id]

        override suspend fun getAllClusters(): List<ClusterEntity> = storage.values.toList()

        override suspend fun insertCluster(cluster: ClusterEntity) {
            storage[cluster.id] = cluster
            notifyChanges()
        }

        override suspend fun updateCluster(cluster: ClusterEntity) {
            storage[cluster.id] = cluster
            notifyChanges()
        }

        override suspend fun deleteCluster(id: String) {
            storage.remove(id)
            notifyChanges()
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
            storage.replaceAll { _, v -> v.copy(isActive = false) }
            notifyChanges()
        }

        override suspend fun activateCluster(id: String) {
            storage[id]?.let {
                storage[id] = it.copy(isActive = true)
            }
            notifyChanges()
        }

        override suspend fun updateClusterName(id: String, name: String) {
            storage[id]?.let {
                storage[id] = it.copy(name = name)
            }
            notifyChanges()
        }

        override suspend fun updateStatus(id: String, status: String, lastConnectedAt: Long?) {
            storage[id]?.let {
                storage[id] = it.copy(status = status, lastConnectedAt = lastConnectedAt)
            }
            notifyChanges()
        }
    }
}
