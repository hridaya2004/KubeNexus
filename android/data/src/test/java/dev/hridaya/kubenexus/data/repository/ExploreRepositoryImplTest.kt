package dev.hridaya.kubenexus.data.repository

import dev.hridaya.kubenexus.core.common.dispatcher.DispatcherProvider
import dev.hridaya.kubenexus.core.common.result.AppError
import dev.hridaya.kubenexus.core.common.result.Result
import dev.hridaya.kubenexus.data.nativebridge.FakeKubeNexusNativeBridge
import dev.hridaya.kubenexus.data.nativebridge.NativeBridgeJsonParser
import dev.hridaya.kubenexus.data.source.local.dao.APIResourceDao
import dev.hridaya.kubenexus.data.source.local.dao.ClusterDao
import dev.hridaya.kubenexus.data.source.local.dao.ExplainedResourceDao
import dev.hridaya.kubenexus.data.source.local.dao.OpenApiSchemaDao
import dev.hridaya.kubenexus.data.source.local.entity.APIResourceEntity
import dev.hridaya.kubenexus.data.source.local.entity.ClusterEntity
import dev.hridaya.kubenexus.data.source.local.entity.ExplainedResourceEntity
import dev.hridaya.kubenexus.data.source.local.entity.OpenApiSchemaEntity
import dev.hridaya.kubenexus.data.source.local.entity.SyncMetadataEntity
import dev.hridaya.kubenexus.domain.model.APIResource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ExploreRepositoryImplTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testDispatcherProvider = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
        override val unconfined: CoroutineDispatcher = testDispatcher
    }

    private lateinit var fakeClusterDao: FakeClusterDao
    private lateinit var fakeAPIResourceDao: FakeAPIResourceDao
    private lateinit var fakeExplainedResourceDao: FakeExplainedResourceDao
    private lateinit var fakeOpenApiSchemaDao: FakeOpenApiSchemaDao
    private lateinit var fakeNativeBridge: FakeNativeBridge
    private lateinit var repository: ExploreRepositoryImpl

    @Before
    fun setUp() {
        fakeClusterDao = FakeClusterDao()
        fakeAPIResourceDao = FakeAPIResourceDao()
        fakeExplainedResourceDao = FakeExplainedResourceDao()
        fakeOpenApiSchemaDao = FakeOpenApiSchemaDao()
        fakeNativeBridge = FakeNativeBridge()

        repository = ExploreRepositoryImpl(
            clusterDao = fakeClusterDao,
            apiResourceDao = fakeAPIResourceDao,
            explainedResourceDao = fakeExplainedResourceDao,
            openApiSchemaDao = fakeOpenApiSchemaDao,
            nativeBridge = fakeNativeBridge,
            jsonParser = NativeBridgeJsonParser(),
            dispatcherProvider = testDispatcherProvider,
        )
    }

    @Test
    fun `fetchAPIResources saves items to room and deletes removed items on subsequent fetch`() =
        runTest(testDispatcher) {
            val clusterId = "cluster-1"
            fakeClusterDao.insertCluster(
                ClusterEntity(
                    id = clusterId,
                    name = "test-cluster",
                    serverUrl = "https://127.0.0.1:6443",
                    rawKubeconfig = "raw",
                    contextName = "ctx",
                    userName = "user",
                    namespace = "default",
                    isActive = true,
                    createdAt = 1000L,
                    lastConnectedAt = 1000L,
                    status = "Connected",
                ),
            )

            // First fetch with 3 resources
            fakeNativeBridge.mockResources = listOf(
                APIResource(name = "pods", kind = "Pod", groupVersion = "v1"),
                APIResource(name = "services", kind = "Service", groupVersion = "v1"),
                APIResource(name = "deployments", kind = "Deployment", groupVersion = "apps/v1"),
            )

            val result1 = repository.fetchAPIResources(clusterId)
            assertTrue(result1 is Result.Success)

            val streamed1 = repository.getAPIResourcesStream(clusterId).first()
            assertEquals(3, streamed1.size)
            assertEquals(listOf("pods", "services", "deployments"), streamed1.map { it.name })

            val lastRefreshed1 = repository.getLastRefreshedStream(clusterId).first()
            assertNotNull(lastRefreshed1)

            // Also explain "services" and "pods"
            val explainResult = repository.explainResource(clusterId, "services", "v1")
            assertTrue(explainResult is Result.Success)
            assertNotNull(
                fakeExplainedResourceDao.getExplainedResource(
                    clusterId,
                    "services",
                    "v1"
                )
            )

            // Second fetch where "services" is removed
            fakeNativeBridge.mockResources = listOf(
                APIResource(name = "pods", kind = "Pod", groupVersion = "v1"),
                APIResource(name = "deployments", kind = "Deployment", groupVersion = "apps/v1"),
            )

            val result2 = repository.fetchAPIResources(clusterId)
            assertTrue(result2 is Result.Success)

            val streamed2 = repository.getAPIResourcesStream(clusterId).first()
            assertEquals(2, streamed2.size)
            assertEquals(listOf("pods", "deployments"), streamed2.map { it.name })

            // "services" explained resource should now be deleted from Room DB
            assertNull(fakeExplainedResourceDao.getExplainedResource(clusterId, "services", "v1"))
        }

    @Test
    fun `explainResource caches in room and returns error on bridge failure`() =
        runTest(testDispatcher) {
            val clusterId = "cluster-1"
            fakeClusterDao.insertCluster(
                ClusterEntity(
                    id = clusterId,
                    name = "test-cluster",
                    serverUrl = "https://127.0.0.1:6443",
                    rawKubeconfig = "raw",
                    contextName = "ctx",
                    userName = "user",
                    namespace = "default",
                    isActive = true,
                    createdAt = 1000L,
                    lastConnectedAt = 1000L,
                    status = "Connected",
                ),
            )

            val result = repository.explainResource(clusterId, "pods", "v1")
            assertTrue(result is Result.Success)
            val explainData = (result as Result.Success).data
            assertEquals("Pod", explainData.kind)
            assertEquals(1, explainData.fields.size)

            // Verify stored in Room
            val cached = fakeExplainedResourceDao.getExplainedResource(clusterId, "pods", "v1")
            assertNotNull(cached)
            assertEquals("Pod", cached!!.kind)

            // Now simulate a schema fetch failure on force refresh: explainResource should
            // return Error so failed refresh is not counted as success, and the cached
            // explanation must survive untouched.
            fakeNativeBridge.shouldFailSchema = true
            val failedResult =
                repository.explainResource(clusterId, "pods", "v1", forceRefresh = true)
            assertTrue(failedResult is Result.Error)

            // Test direct getCachedExplainedResource returns cached schema
            val directCached = repository.getCachedExplainedResource(clusterId, "pods", "v1")
            assertNotNull(directCached)
            assertEquals("Pod", directCached!!.kind)
            assertEquals("Pod is a collection of containers", directCached.description)
            assertEquals("spec", directCached.fields.first().name)
        }

    private class FakeExplainedResourceDao : ExplainedResourceDao {
        private val storage = mutableMapOf<String, ExplainedResourceEntity>()

        override suspend fun getExplainedResource(
            clusterId: String,
            resourceOrKind: String,
            groupVersion: String,
        ): ExplainedResourceEntity? {
            val rk = resourceOrKind.lowercase()
            return storage.values.firstOrNull { entity ->
                entity.clusterId == clusterId &&
                        (entity.resourceOrKind == rk || entity.kind.lowercase() == rk) &&
                        (groupVersion.isEmpty() || entity.groupVersion == groupVersion)
            }
        }

        override fun getExplainedResourceStream(
            clusterId: String,
            resourceOrKind: String,
            groupVersion: String,
        ): Flow<ExplainedResourceEntity?> {
            val rk = resourceOrKind.lowercase()
            val match = storage.values.firstOrNull { entity ->
                entity.clusterId == clusterId &&
                        (entity.resourceOrKind == rk || entity.kind.lowercase() == rk) &&
                        (groupVersion.isEmpty() || entity.groupVersion == groupVersion)
            }
            return MutableStateFlow(match).asStateFlow()
        }

        override suspend fun insertExplainedResource(explained: ExplainedResourceEntity) {
            storage[explained.id] = explained
        }

        override suspend fun deleteExplainedResourcesForCluster(clusterId: String) {
            storage.entries.removeIf { it.value.clusterId == clusterId }
        }

        override suspend fun deleteOrphanedExplainedResources(
            clusterId: String,
            activeResources: List<String>,
        ) {
            val activeSet = activeResources.map { it.lowercase() }.toSet()
            storage.entries.removeIf { entry ->
                entry.value.clusterId == clusterId &&
                        entry.value.resourceOrKind !in activeSet &&
                        entry.value.kind.lowercase() !in activeSet
            }
        }
    }

    private class FakeAPIResourceDao : APIResourceDao {
        private val resourcesMap = mutableMapOf<String, List<APIResourceEntity>>()
        private val resourcesFlow =
            MutableStateFlow<Map<String, List<APIResourceEntity>>>(emptyMap())
        private val metadataMap = mutableMapOf<String, Long>()
        private val metadataFlow = MutableStateFlow<Map<String, Long>>(emptyMap())

        override fun getAPIResourcesStream(clusterId: String): Flow<List<APIResourceEntity>> {
            return resourcesFlow.map { it[clusterId] ?: emptyList() }
        }

        override suspend fun getAPIResourcesList(clusterId: String): List<APIResourceEntity> {
            return resourcesMap[clusterId] ?: emptyList()
        }

        override suspend fun getResourceIdsForCluster(clusterId: String): List<String> {
            return resourcesMap[clusterId]?.map { it.id } ?: emptyList()
        }

        override suspend fun insertAPIResources(resources: List<APIResourceEntity>) {
            val clusterId = resources.firstOrNull()?.clusterId ?: return
            val existing = resourcesMap[clusterId]?.toMutableList() ?: mutableListOf()
            existing.addAll(resources)
            resourcesMap[clusterId] = existing
            resourcesFlow.value = resourcesMap.toMap()
        }

        override suspend fun deleteAPIResourcesForCluster(clusterId: String) {
            resourcesMap.remove(clusterId)
            resourcesFlow.value = resourcesMap.toMap()
        }

        override suspend fun deleteAPIResourcesByIds(ids: List<String>) {
            val idSet = ids.toSet()
            resourcesMap.keys.forEach { clusterId ->
                val list = resourcesMap[clusterId]?.filter { it.id !in idSet } ?: emptyList()
                resourcesMap[clusterId] = list
            }
            resourcesFlow.value = resourcesMap.toMap()
        }

        override suspend fun syncAPIResources(
            clusterId: String,
            resources: List<APIResourceEntity>,
            timestamp: Long,
            chunkSize: Int,
        ) {
            resourcesMap[clusterId] = resources
            resourcesFlow.value = resourcesMap.toMap()

            metadataMap["${clusterId}_api_resources"] = timestamp
            metadataFlow.value = metadataMap.toMap()
        }

        override fun getSyncMetadataStream(key: String): Flow<Long?> {
            return metadataFlow.map { it[key] }
        }

        override suspend fun getLastRefreshedTime(key: String): Long? = metadataMap[key]

        override suspend fun insertSyncMetadata(metadata: SyncMetadataEntity) {
            metadataMap[metadata.key] = metadata.lastRefreshedAt
            metadataFlow.value = metadataMap.toMap()
        }
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

    @Test
    fun `explainResource resolves custom resources through discovery GVKs`() =
        runTest(testDispatcher) {
            val clusterId = "cluster-1"
            fakeClusterDao.insertCluster(
                ClusterEntity(
                    id = clusterId,
                    name = "test-cluster",
                    serverUrl = "https://127.0.0.1:6443",
                    rawKubeconfig = "raw",
                    contextName = "ctx",
                    userName = "user",
                    namespace = "default",
                    isActive = true,
                    createdAt = 1000L,
                    lastConnectedAt = 1000L,
                    status = "Connected",
                ),
            )

            fakeNativeBridge.mockResources = listOf(
                APIResource(
                    name = "policies",
                    kind = "Policy",
                    group = "kyverno.io",
                    version = "v1",
                    groupVersion = "kyverno.io/v1",
                ),
            )
            assertTrue(repository.fetchAPIResources(clusterId) is Result.Success)

            // Irregular plural: only the discovery GVK path can resolve this.
            val result = repository.explainResource(clusterId, "policies", "kyverno.io/v1")
            assertTrue(result is Result.Success)
            val explain = (result as Result.Success).data
            assertEquals("Policy", explain.kind)
            assertEquals("Kyverno policy rule set.", explain.description)
        }

    private class FakeNativeBridge : FakeKubeNexusNativeBridge() {
        var mockResources: List<APIResource> = emptyList()
        var mockSchemaJson: String = loadSchemaFixture()
        var shouldFailSchema: Boolean = false

        override fun listAPIResources(rawKubeconfig: String): Result<List<APIResource>> =
            Result.Success(mockResources)

        override fun openAPISchemaJSON(rawKubeconfig: String): Result<String> {
            if (shouldFailSchema) {
                return Result.Error(AppError.Network("Network error"))
            }
            return Result.Success(mockSchemaJson)
        }

        private fun loadSchemaFixture(): String =
            javaClass.classLoader!!
                .getResourceAsStream("openapi-schema-test.json")!!
                .bufferedReader()
                .readText()
    }

    private class FakeOpenApiSchemaDao : OpenApiSchemaDao {
        private val storage = mutableMapOf<String, OpenApiSchemaEntity>()

        override suspend fun getForCluster(clusterId: String): OpenApiSchemaEntity? =
            storage[clusterId]

        override suspend fun upsert(schema: OpenApiSchemaEntity) {
            storage[schema.clusterId] = schema
        }

        override suspend fun deleteForCluster(clusterId: String) {
            storage.remove(clusterId)
        }
    }
}
