package dev.hridaya.kubenexus.presentation.testing

import dev.hridaya.kubenexus.core.common.result.Result
import dev.hridaya.kubenexus.domain.repository.NamespaceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeNamespaceRepository(names: List<String> = listOf("default", "kube-system")) : NamespaceRepository {

    val names = MutableStateFlow(names)
    val syncedClusters = mutableListOf<String?>()
    val created = mutableListOf<Pair<String?, String>>()
    val deleted = mutableListOf<String>()
    var createResult: Result<Unit> = Result.Success(Unit)
    var deleteResult: Result<Unit> = Result.Success(Unit)

    override fun observeNames(clusterId: String?): Flow<List<String>> = names

    override suspend fun sync(clusterId: String?): Result<Unit> {
        syncedClusters += clusterId
        return Result.Success(Unit)
    }

    override suspend fun create(clusterId: String?, name: String): Result<Unit> {
        created += clusterId to name
        return createResult
    }

    override suspend fun delete(clusterId: String?, name: String): Result<Unit> {
        deleted += name
        return deleteResult
    }
}
