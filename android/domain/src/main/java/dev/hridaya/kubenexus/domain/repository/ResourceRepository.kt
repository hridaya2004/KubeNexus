package dev.hridaya.kubenexus.domain.repository

import dev.hridaya.kubenexus.core.common.result.Result
import dev.hridaya.kubenexus.domain.model.resource.KubeObjectSummary
import dev.hridaya.kubenexus.domain.model.resource.NamespaceScope
import dev.hridaya.kubenexus.domain.model.resource.ResourceKind
import kotlinx.coroutines.flow.Flow

/**
 * Generic access to any Kubernetes resource the app models.
 *
 * One implementation serves every [ResourceKind]: listing, the offline-first
 * cache, describe, create and delete are written once rather than per resource.
 * Behavior that only exists for some kinds (logs, exec, scale) stays in the
 * dedicated repositories.
 */
interface ResourceRepository {

    /**
     * Cold stream of cached objects in [scope], refreshed by [sync].
     *
     * Offline-first: emits the cache immediately and again after every sync, so
     * screens render without waiting on the cluster.
     */
    fun <S : KubeObjectSummary> observe(
        clusterId: String?,
        kind: ResourceKind<S, *>,
        scope: NamespaceScope,
    ): Flow<List<S>>

    /**
     * Pulls the live list for [scope] and replaces that part of the cache in one
     * transaction, recording the sync time.
     */
    suspend fun sync(
        clusterId: String?,
        kind: ResourceKind<*, *>,
        scope: NamespaceScope,
    ): Result<Unit>

    /** Epoch millis of the last successful [sync] of [kind] on this cluster. */
    fun observeLastSynced(clusterId: String?, kind: ResourceKind<*, *>): Flow<Long?>

    /**
     * Lists live objects without touching the cache, optionally filtered by a
     * Kubernetes [labelSelector] such as "app=web,tier=frontend".
     */
    suspend fun <S : KubeObjectSummary> list(
        clusterId: String?,
        kind: ResourceKind<S, *>,
        scope: NamespaceScope,
        labelSelector: String = "",
    ): Result<List<S>>

    /**
     * Describes one object. Events are best-effort: failing to load them never
     * fails the describe itself.
     */
    suspend fun <D : Any> describe(
        clusterId: String?,
        kind: ResourceKind<*, D>,
        namespace: String,
        name: String,
    ): Result<D>

    /** Deletes one object from the cluster and drops it from the cache. */
    suspend fun delete(
        clusterId: String?,
        kind: ResourceKind<*, *>,
        namespace: String,
        name: String,
    ): Result<Unit>

    /**
     * Creates every object in [manifest] (YAML or JSON, one or more documents),
     * each in the namespace its own metadata names. The kind of each document is
     * read from the manifest; creation stops at the first failure.
     */
    suspend fun create(clusterId: String?, manifest: String): Result<Unit>
}
