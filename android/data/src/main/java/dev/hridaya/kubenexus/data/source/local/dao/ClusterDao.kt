package dev.hridaya.kubenexus.data.source.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import dev.hridaya.kubenexus.data.source.local.entity.ClusterEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ClusterDao {

    @Query("SELECT * FROM clusters ORDER BY isActive DESC, createdAt DESC")
    abstract fun observeClusters(): Flow<List<ClusterEntity>>

    @Query("SELECT * FROM clusters WHERE isActive = 1 LIMIT 1")
    abstract fun observeActiveCluster(): Flow<ClusterEntity?>

    @Query("SELECT * FROM clusters WHERE id = :id LIMIT 1")
    abstract suspend fun getClusterById(id: String): ClusterEntity?

    @Query("SELECT * FROM clusters")
    abstract suspend fun getAllClusters(): List<ClusterEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertCluster(cluster: ClusterEntity)

    @Update
    abstract suspend fun updateCluster(cluster: ClusterEntity)

    @Query("DELETE FROM clusters WHERE id = :id")
    abstract suspend fun deleteCluster(id: String)

    @Query("DELETE FROM pods WHERE clusterId = :id")
    abstract suspend fun deletePodsForCluster(id: String)

    @Query("DELETE FROM namespaces WHERE clusterId = :id")
    abstract suspend fun deleteNamespacesForCluster(id: String)

    @Query("DELETE FROM api_resources WHERE clusterId = :id")
    abstract suspend fun deleteAPIResourcesForCluster(id: String)

    @Query("DELETE FROM explained_resources WHERE clusterId = :id")
    abstract suspend fun deleteExplainedResourcesForCluster(id: String)

    @Query("DELETE FROM open_api_schemas WHERE clusterId = :id")
    abstract suspend fun deleteOpenApiSchemaForCluster(id: String)

    @Query("DELETE FROM sync_metadata WHERE clusterId = :id")
    abstract suspend fun deleteSyncMetadataForCluster(id: String)

    @Query("DELETE FROM deployments WHERE clusterId = :id")
    abstract suspend fun deleteDeploymentsForCluster(id: String)

    @Query("DELETE FROM services WHERE clusterId = :id")
    abstract suspend fun deleteServicesForCluster(id: String)

    /**
     * Removes a cluster and everything cached against it.
     *
     * The child tables carry a plain `clusterId` column with no foreign key, so
     * nothing cascades automatically. Deleting only the cluster row orphaned its
     * cached data permanently; the OpenAPI schema blob in particular is the
     * largest row in the database.
     */
    @Transaction
    open suspend fun deleteClusterWithCachedData(id: String) {
        deleteOpenApiSchemaForCluster(id)
        deleteExplainedResourcesForCluster(id)
        deleteAPIResourcesForCluster(id)
        deleteNamespacesForCluster(id)
        deletePodsForCluster(id)
        deleteDeploymentsForCluster(id)
        deleteServicesForCluster(id)
        deleteSyncMetadataForCluster(id)
        deleteCluster(id)
    }

    @Query("DELETE FROM pods")
    abstract suspend fun deleteAllPods()

    @Query("DELETE FROM namespaces")
    abstract suspend fun deleteAllNamespaces()

    @Query("DELETE FROM deployments")
    abstract suspend fun deleteAllDeployments()

    @Query("DELETE FROM services")
    abstract suspend fun deleteAllServices()

    @Query("DELETE FROM api_resources")
    abstract suspend fun deleteAllAPIResources()

    @Query("DELETE FROM explained_resources")
    abstract suspend fun deleteAllExplainedResources()

    @Query("DELETE FROM open_api_schemas")
    abstract suspend fun deleteAllOpenApiSchemas()

    @Query("DELETE FROM sync_metadata")
    abstract suspend fun deleteAllSyncMetadata()

    /**
     * Removes everything cached from every cluster (workloads, discovery, explains, schemas
     * and sync timestamps) and keeps the clusters themselves, credentials included. It is
     * all re-fetched on the next refresh.
     */
    @Transaction
    open suspend fun clearCachedData() {
        deleteAllPods()
        deleteAllNamespaces()
        deleteAllDeployments()
        deleteAllServices()
        deleteAllAPIResources()
        deleteAllExplainedResources()
        deleteAllOpenApiSchemas()
        deleteAllSyncMetadata()
    }

    @Query("UPDATE clusters SET isActive = 0")
    abstract suspend fun deactivateAllClusters()

    @Query("UPDATE clusters SET isActive = 1 WHERE id = :id")
    abstract suspend fun activateCluster(id: String)

    @Transaction
    open suspend fun setActiveCluster(id: String) {
        deactivateAllClusters()
        activateCluster(id)
    }

    @Query("UPDATE clusters SET name = :name WHERE id = :id")
    abstract suspend fun updateClusterName(id: String, name: String)

    @Query("UPDATE clusters SET status = :status, lastConnectedAt = :lastConnectedAt WHERE id = :id")
    abstract suspend fun updateStatus(id: String, status: String, lastConnectedAt: Long?)
}
