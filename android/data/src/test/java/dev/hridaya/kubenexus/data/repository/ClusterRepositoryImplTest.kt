package dev.hridaya.kubenexus.data.repository

import dev.hridaya.kubenexus.core.common.dispatcher.DispatcherProvider
import dev.hridaya.kubenexus.core.common.result.Result
import dev.hridaya.kubenexus.data.kubeconfig.ClusterConnectionTester
import dev.hridaya.kubenexus.data.kubeconfig.ParsedKubeconfig
import dev.hridaya.kubenexus.data.nativebridge.FakeKubeNexusNativeBridge
import dev.hridaya.kubenexus.data.source.local.dao.ClusterDao
import dev.hridaya.kubenexus.data.security.TinkKubeconfigEncryptor
import dev.hridaya.kubenexus.data.source.local.entity.ClusterEntity
import dev.hridaya.kubenexus.data.testing.testKubeconfigEncryptor
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
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class ClusterRepositoryImplTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testDispatcherProvider = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
        override val unconfined: CoroutineDispatcher = testDispatcher
    }

    private lateinit var fakeDao: FakeClusterDao
    private val legacyKey: SecretKey =
        KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private lateinit var encryptor: TinkKubeconfigEncryptor
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
        encryptor = testKubeconfigEncryptor(legacyKey)

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

        // Verify the persisted entity in DAO is encrypted
        val persistedEntity = fakeDao.getClusterById(addedCluster.id)
        assertNotNull(persistedEntity)
        assertTrue(persistedEntity!!.rawKubeconfig.startsWith("enc:v2:"))
        assertFalse(persistedEntity.rawKubeconfig.contains("secret-token-12345"))
        assertEquals(sampleKubeconfig, encryptor.decrypt(persistedEntity.rawKubeconfig, addedCluster.id))
    }

    @Test
    fun `adding an active cluster leaves it the only active one, and an inactive one changes nothing`() =
        runTest(testDispatcher) {
            val first = (repository.addCluster(
                sampleKubeconfig,
                "First",
                setAsActive = true
            ) as Result.Success).data
            val second = (repository.addCluster(
                sampleKubeconfig,
                "Second",
                setAsActive = true
            ) as Result.Success).data
            val third = (repository.addCluster(
                sampleKubeconfig,
                "Third",
                setAsActive = false
            ) as Result.Success).data

            assertEquals(
                listOf(second.id),
                fakeDao.getAllClusters().filter { it.isActive }.map { it.id })
            assertFalse(fakeDao.getClusterById(first.id)!!.isActive)
            assertFalse(fakeDao.getClusterById(third.id)!!.isActive)
        }

    @Test
    fun `getClustersStream and getClusterById decrypt stored encrypted kubeconfig`() =
        runTest(testDispatcher) {
            val encryptedKubeconfig = encryptor.encrypt(sampleKubeconfig, "c-100")
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
            assertFalse(cluster!!.credentialsUnavailable)

            val streamList = repository.getClustersStream().first()
            assertEquals(1, streamList.size)
            assertFalse(streamList[0].credentialsUnavailable)
        }

    @Test
    fun `migrateStoredKubeconfigs encrypts all plaintext records without losing data`() =
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
            val migrationResult = repository.migrateStoredKubeconfigs()
            assertTrue(migrationResult is Result.Success)
            assertEquals(2, (migrationResult as Result.Success).data)

            // After migration, both records in DAO are encrypted at rest
            val entity1 = fakeDao.getClusterById("c-legacy-1")!!
            val entity2 = fakeDao.getClusterById("c-legacy-2")!!
            assertTrue(entity1.rawKubeconfig.startsWith("enc:v2:"))
            assertTrue(entity2.rawKubeconfig.startsWith("enc:v2:"))

            assertEquals(sampleKubeconfig, encryptor.decrypt(entity1.rawKubeconfig, entity1.id))
            assertEquals(sampleKubeconfig, encryptor.decrypt(entity2.rawKubeconfig, entity2.id))

            // Running migration again is idempotent (0 records migrated)
            val secondMigration = repository.migrateStoredKubeconfigs()
            assertTrue(secondMigration is Result.Success)
            assertEquals(0, (secondMigration as Result.Success).data)
        }

    @Test
    fun `migrateStoredKubeconfigs rewrites enc v1 records and leaves unreadable ones alone`() =
        runTest(testDispatcher) {
            val readable = clusterEntity("c-v1", legacyEncrypt(sampleKubeconfig, legacyKey))
            val unreadableKubeconfig = "enc:v1:" + Base64.getEncoder().encodeToString(ByteArray(64))
            val unreadable = clusterEntity("c-lost", unreadableKubeconfig)
            fakeDao.insertCluster(readable)
            fakeDao.insertCluster(unreadable)

            val result = repository.migrateStoredKubeconfigs()

            assertEquals(1, (result as Result.Success).data)
            val migrated = fakeDao.getClusterById("c-v1")!!.rawKubeconfig
            assertTrue(migrated.startsWith("enc:v2:"))
            assertEquals(sampleKubeconfig, encryptor.decrypt(migrated, "c-v1"))
            assertEquals(unreadableKubeconfig, fakeDao.getClusterById("c-lost")!!.rawKubeconfig)
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

    private fun clusterEntity(id: String, rawKubeconfig: String) = ClusterEntity(
        id = id,
        name = id,
        serverUrl = "https://10.0.0.1:6443",
        rawKubeconfig = rawKubeconfig,
        contextName = "ctx",
        userName = "user",
        namespace = "default",
        isActive = false,
        createdAt = 1000L,
        lastConnectedAt = null,
        status = "DISCONNECTED",
    )

    /** The `enc:v1:` format written by earlier versions. */
    private fun legacyEncrypt(plainText: String, key: SecretKey): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val payload = cipher.iv + cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return "enc:v1:" + Base64.getEncoder().encodeToString(payload)
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

        override suspend fun deleteCachedResourcesForCluster(id: String) = Unit
        override suspend fun deleteNamespacesForCluster(id: String) = Unit
        override suspend fun deleteAPIResourcesForCluster(id: String) = Unit
        override suspend fun deleteExplainedResourcesForCluster(id: String) = Unit
        override suspend fun deleteOpenApiSchemaForCluster(id: String) = Unit
        override suspend fun deleteSyncMetadataForCluster(id: String) = Unit
        override suspend fun deleteAllCachedResources() = Unit
        override suspend fun deleteAllNamespaces() = Unit
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

        override suspend fun updateKubeconfig(id: String, rawKubeconfig: String) {
            storage[id]?.let {
                storage[id] = it.copy(rawKubeconfig = rawKubeconfig)
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
