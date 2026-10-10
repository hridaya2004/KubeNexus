package dev.hridaya.kubenexus.domain.repository

import dev.hridaya.kubenexus.core.common.result.Result
import kotlinx.coroutines.flow.Flow

/** The namespaces of a cluster, cached for the namespace filters and pickers. */
interface NamespaceRepository {

    /**
     * Cold stream of cached namespace names, sorted. Falls back to the
     * namespaces every cluster has while nothing is cached.
     */
    fun observeNames(clusterId: String?): Flow<List<String>>

    /** Replaces the cached namespaces with the live list. */
    suspend fun sync(clusterId: String?): Result<Unit>

    /**
     * Creates an empty namespace. The name must be a valid DNS-1123 label;
     * callers validate UX-side, the API server has final say.
     */
    suspend fun create(clusterId: String?, name: String): Result<Unit>

    /** Deletes a namespace and drops everything cached inside it. */
    suspend fun delete(clusterId: String?, name: String): Result<Unit>
}
