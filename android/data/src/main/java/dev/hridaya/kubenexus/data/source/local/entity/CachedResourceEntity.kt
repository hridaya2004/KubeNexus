package dev.hridaya.kubenexus.data.source.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One cached Kubernetes object of any kind, for offline-first lists.
 *
 * Replaces the per-kind `pods`, `deployments` and `services` tables, which each
 * needed their own entity, DAO, mapper and migration. Rows are keyed by
 * [resourceKey] (e.g. "apps/v1/deployments") and hold the object as JSON in
 * [payload].
 *
 * The payload is the kind's list projection, re-encoded from the fields its
 * codec declares, not the verbatim API object. Rows stay small, and anything a
 * codec leaves out of its projection is never written to disk.
 */
@Entity(
    tableName = "cached_resources",
    indices = [
        Index(value = ["clusterId", "resourceKey", "namespace"]),
    ],
)
data class CachedResourceEntity(
    /** `clusterId/resourceKey/namespace/name`; see [idOf]. */
    @PrimaryKey
    val id: String,
    val clusterId: String,
    val resourceKey: String,
    /** Empty for cluster-scoped objects. */
    val namespace: String,
    val name: String,
    val payload: String,
    val lastUpdated: Long,
) {
    companion object {
        fun idOf(clusterId: String, resourceKey: String, namespace: String, name: String): String =
            "$clusterId/$resourceKey/$namespace/$name"
    }
}
