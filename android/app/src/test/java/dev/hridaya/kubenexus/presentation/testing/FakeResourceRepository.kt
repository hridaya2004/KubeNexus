package dev.hridaya.kubenexus.presentation.testing

import dev.hridaya.kubenexus.core.common.result.AppError
import dev.hridaya.kubenexus.core.common.result.Result
import dev.hridaya.kubenexus.domain.model.resource.KubeObjectSummary
import dev.hridaya.kubenexus.domain.model.resource.NamespaceScope
import dev.hridaya.kubenexus.domain.model.resource.ResourceKind
import dev.hridaya.kubenexus.domain.repository.ResourceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * An in-memory [ResourceRepository]: a per-kind cache tests fill with
 * [givenCached], configurable results, and a record of every call.
 */
class FakeResourceRepository : ResourceRepository {

    data class SyncCall(
        val clusterId: String?,
        val kind: ResourceKind<*, *>,
        val scope: NamespaceScope
    )

    data class ListCall(
        val clusterId: String?,
        val kind: ResourceKind<*, *>,
        val scope: NamespaceScope,
        val labelSelector: String
    )

    data class ObjectCall(
        val clusterId: String?,
        val kind: ResourceKind<*, *>,
        val namespace: String,
        val name: String
    )

    data class CreateCall(val clusterId: String?, val manifest: String)

    private val cache =
        mutableMapOf<ResourceKind<*, *>, MutableStateFlow<List<KubeObjectSummary>>>()
    private val listResults = mutableMapOf<ResourceKind<*, *>, Result<List<KubeObjectSummary>>>()
    private val describeResults = mutableMapOf<ResourceKind<*, *>, Result<Any>>()

    val lastSynced = MutableStateFlow<Long?>(null)
    var syncResult: Result<Unit> = Result.Success(Unit)

    /** Answers each sync; replace it to delay or vary the answer per call. */
    var onSync: suspend (SyncCall) -> Result<Unit> = { syncResult }
    var deleteResult: Result<Unit> = Result.Success(Unit)
    var createResult: Result<Unit> = Result.Success(Unit)

    val syncCalls = mutableListOf<SyncCall>()
    val listCalls = mutableListOf<ListCall>()
    val describeCalls = mutableListOf<ObjectCall>()
    val deleteCalls = mutableListOf<ObjectCall>()
    val createCalls = mutableListOf<CreateCall>()

    fun <S : KubeObjectSummary> givenCached(kind: ResourceKind<S, *>, items: List<S>) {
        cacheOf(kind).value = items
    }

    fun <S : KubeObjectSummary> givenList(kind: ResourceKind<S, *>, result: Result<List<S>>) {
        listResults[kind] = result
    }

    fun <D : Any> givenDescribe(kind: ResourceKind<*, D>, result: Result<D>) {
        describeResults[kind] = result
    }

    @Suppress("UNCHECKED_CAST")
    override fun <S : KubeObjectSummary> observe(
        clusterId: String?,
        kind: ResourceKind<S, *>,
        scope: NamespaceScope,
    ): Flow<List<S>> = cacheOf(kind).map { items ->
        when (scope) {
            NamespaceScope.All -> items
            is NamespaceScope.Only -> items.filter { it.namespace == scope.namespace }
        } as List<S>
    }

    override suspend fun sync(
        clusterId: String?,
        kind: ResourceKind<*, *>,
        scope: NamespaceScope
    ): Result<Unit> {
        val call = SyncCall(clusterId, kind, scope)
        syncCalls += call
        return onSync(call)
    }

    override fun observeLastSynced(clusterId: String?, kind: ResourceKind<*, *>): Flow<Long?> =
        lastSynced

    @Suppress("UNCHECKED_CAST")
    override suspend fun <S : KubeObjectSummary> list(
        clusterId: String?,
        kind: ResourceKind<S, *>,
        scope: NamespaceScope,
        labelSelector: String,
    ): Result<List<S>> {
        listCalls += ListCall(clusterId, kind, scope, labelSelector)
        return (listResults[kind] ?: Result.Success(emptyList())) as Result<List<S>>
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun <D : Any> describe(
        clusterId: String?,
        kind: ResourceKind<*, D>,
        namespace: String,
        name: String,
    ): Result<D> {
        describeCalls += ObjectCall(clusterId, kind, namespace, name)
        return (describeResults[kind]
            ?: Result.Error(AppError.NotFound("$name not found"))) as Result<D>
    }

    override suspend fun delete(
        clusterId: String?,
        kind: ResourceKind<*, *>,
        namespace: String,
        name: String
    ): Result<Unit> {
        deleteCalls += ObjectCall(clusterId, kind, namespace, name)
        return deleteResult
    }

    override suspend fun create(clusterId: String?, manifest: String): Result<Unit> {
        createCalls += CreateCall(clusterId, manifest)
        return createResult
    }

    private fun cacheOf(kind: ResourceKind<*, *>) =
        cache.getOrPut(kind) { MutableStateFlow(emptyList()) }
}
