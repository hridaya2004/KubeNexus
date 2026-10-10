package dev.hridaya.kubenexus.data.source.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import dev.hridaya.kubenexus.data.source.local.entity.CachedResourceEntity
import dev.hridaya.kubenexus.data.source.local.entity.SyncMetadataEntity
import kotlinx.coroutines.flow.Flow

/** Stays well under SQLite's bound-variable limit on older Android releases. */
private const val SQLITE_SAFE_BATCH_SIZE = 250

/** The offline cache behind every resource list, whatever its kind. */
@Dao
interface ResourceCacheDao {

    @Query(
        "SELECT payload FROM cached_resources WHERE clusterId = :clusterId AND resourceKey = :resourceKey " +
            "ORDER BY namespace ASC, name ASC",
    )
    fun observePayloads(clusterId: String, resourceKey: String): Flow<List<String>>

    @Query(
        "SELECT payload FROM cached_resources WHERE clusterId = :clusterId AND resourceKey = :resourceKey " +
            "AND namespace = :namespace ORDER BY name ASC",
    )
    fun observePayloadsInNamespace(clusterId: String, resourceKey: String, namespace: String): Flow<List<String>>

    @Query("SELECT id FROM cached_resources WHERE clusterId = :clusterId AND resourceKey = :resourceKey")
    suspend fun getIds(clusterId: String, resourceKey: String): List<String>

    @Query(
        "SELECT id FROM cached_resources WHERE clusterId = :clusterId AND resourceKey = :resourceKey " +
            "AND namespace = :namespace",
    )
    suspend fun getIdsInNamespace(clusterId: String, resourceKey: String, namespace: String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rows: List<CachedResourceEntity>)

    @Query("DELETE FROM cached_resources WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM cached_resources WHERE id = :id")
    suspend fun deleteById(id: String)

    /** Drops every kind cached inside [namespace], e.g. after the namespace is deleted. */
    @Query("DELETE FROM cached_resources WHERE clusterId = :clusterId AND namespace = :namespace")
    suspend fun deleteNamespace(clusterId: String, namespace: String)

    @Query("SELECT lastRefreshedAt FROM sync_metadata WHERE `key` = :key LIMIT 1")
    fun observeLastSynced(key: String): Flow<Long?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyncMetadata(metadata: SyncMetadataEntity)

    /**
     * Replaces the cached [resourceKey] objects of [namespace] (or of the whole
     * cluster when null) with [rows] in one transaction: rows the live list no
     * longer contains are deleted, the rest upserted, and the sync recorded. All
     * or nothing, so an interrupted sync cannot leave a half-stale cache.
     */
    @Transaction
    suspend fun replace(
        clusterId: String,
        resourceKey: String,
        namespace: String?,
        rows: List<CachedResourceEntity>,
        syncMetadata: SyncMetadataEntity,
    ) {
        val existingIds = if (namespace == null) {
            getIds(clusterId, resourceKey)
        } else {
            getIdsInNamespace(clusterId, resourceKey, namespace)
        }
        val liveIds = rows.mapTo(HashSet()) { it.id }
        existingIds.filterNot { it in liveIds }
            .chunked(SQLITE_SAFE_BATCH_SIZE)
            .forEach { deleteByIds(it) }
        rows.chunked(SQLITE_SAFE_BATCH_SIZE).forEach { upsert(it) }
        insertSyncMetadata(syncMetadata)
    }
}
