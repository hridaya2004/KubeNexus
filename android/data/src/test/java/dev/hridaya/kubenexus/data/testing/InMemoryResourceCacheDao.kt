package dev.hridaya.kubenexus.data.testing

import dev.hridaya.kubenexus.data.source.local.dao.ResourceCacheDao
import dev.hridaya.kubenexus.data.source.local.entity.CachedResourceEntity
import dev.hridaya.kubenexus.data.source.local.entity.SyncMetadataEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Implements the DAO's queries over in-memory tables, so the real
 * [ResourceCacheDao.replace] transaction logic runs against them.
 */
class InMemoryResourceCacheDao : ResourceCacheDao {

    val rows = MutableStateFlow<Map<String, CachedResourceEntity>>(emptyMap())
    val syncMetadata = MutableStateFlow<Map<String, SyncMetadataEntity>>(emptyMap())

    override fun observePayloads(clusterId: String, resourceKey: String): Flow<List<String>> =
        rows.map { table ->
            table.values
                .filter { it.clusterId == clusterId && it.resourceKey == resourceKey }
                .sortedWith(compareBy({ it.namespace }, { it.name }))
                .map { it.payload }
        }

    override fun observePayloadsInNamespace(
        clusterId: String,
        resourceKey: String,
        namespace: String,
    ): Flow<List<String>> =
        rows.map { table ->
            table.values
                .filter { it.clusterId == clusterId && it.resourceKey == resourceKey && it.namespace == namespace }
                .sortedBy { it.name }
                .map { it.payload }
        }

    override suspend fun getIds(clusterId: String, resourceKey: String): List<String> =
        rows.value.values.filter { it.clusterId == clusterId && it.resourceKey == resourceKey }
            .map { it.id }

    override suspend fun getIdsInNamespace(
        clusterId: String,
        resourceKey: String,
        namespace: String
    ): List<String> =
        rows.value.values
            .filter { it.clusterId == clusterId && it.resourceKey == resourceKey && it.namespace == namespace }
            .map { it.id }

    override suspend fun upsert(rows: List<CachedResourceEntity>) {
        this.rows.value += rows.associateBy { it.id }
    }

    override suspend fun deleteByIds(ids: List<String>) {
        rows.value -= ids.toSet()
    }

    override suspend fun deleteById(id: String) {
        rows.value -= id
    }

    override suspend fun deleteNamespace(clusterId: String, namespace: String) {
        rows.value =
            rows.value.filterValues { !(it.clusterId == clusterId && it.namespace == namespace) }
    }

    override fun observeLastSynced(key: String): Flow<Long?> =
        syncMetadata.map { it[key]?.lastRefreshedAt }

    override suspend fun insertSyncMetadata(metadata: SyncMetadataEntity) {
        syncMetadata.value += metadata.key to metadata
    }
}
