package dev.hridaya.kubenexus.domain.fake

import dev.hridaya.kubenexus.core.common.result.Result
import dev.hridaya.kubenexus.domain.model.resource.KubeObjectSummary
import dev.hridaya.kubenexus.domain.model.resource.NamespaceScope
import dev.hridaya.kubenexus.domain.model.resource.ResourceKind
import dev.hridaya.kubenexus.domain.repository.ResourceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Records calls and answers with whatever a test has configured, success by default. */
class FakeResourceRepository : ResourceRepository {

    data class ListCall(
        val clusterId: String?,
        val kind: ResourceKind<*, *>,
        val scope: NamespaceScope,
        val labelSelector: String
    )

    data class SyncCall(
        val clusterId: String?,
        val kind: ResourceKind<*, *>,
        val scope: NamespaceScope
    )

    data class DeleteCall(
        val clusterId: String?,
        val kind: ResourceKind<*, *>,
        val namespace: String,
        val name: String
    )

    val listCalls = mutableListOf<ListCall>()
    val syncCalls = mutableListOf<SyncCall>()
    val deleteCalls = mutableListOf<DeleteCall>()

    private val listResults = mutableMapOf<ResourceKind<*, *>, Result<List<KubeObjectSummary>>>()
    var syncResult: Result<Unit> = Result.Success(Unit)
    var deleteResult: Result<Unit> = Result.Success(Unit)

    fun <S : KubeObjectSummary> givenList(kind: ResourceKind<S, *>, result: Result<List<S>>) {
        listResults[kind] = result
    }

    override fun <S : KubeObjectSummary> observe(
        clusterId: String?,
        kind: ResourceKind<S, *>,
        scope: NamespaceScope,
    ): Flow<List<S>> = flowOf(emptyList())

    override suspend fun sync(
        clusterId: String?,
        kind: ResourceKind<*, *>,
        scope: NamespaceScope
    ): Result<Unit> {
        syncCalls += SyncCall(clusterId, kind, scope)
        return syncResult
    }

    override fun observeLastSynced(clusterId: String?, kind: ResourceKind<*, *>): Flow<Long?> =
        flowOf(null)

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

    override suspend fun <D : Any> describe(
        clusterId: String?,
        kind: ResourceKind<*, D>,
        namespace: String,
        name: String,
    ): Result<D> = error("describe is not configured in this fake")

    override suspend fun delete(
        clusterId: String?,
        kind: ResourceKind<*, *>,
        namespace: String,
        name: String,
    ): Result<Unit> {
        deleteCalls += DeleteCall(clusterId, kind, namespace, name)
        return deleteResult
    }

    override suspend fun create(clusterId: String?, manifest: String): Result<Unit> =
        Result.Success(Unit)
}
