package dev.hridaya.kubenexus.data.testing

import dev.hridaya.kubenexus.data.source.local.dao.NamespaceDao
import dev.hridaya.kubenexus.data.source.local.entity.NamespaceEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class InMemoryNamespaceDao : NamespaceDao {

    val namespaces = MutableStateFlow<List<NamespaceEntity>>(emptyList())

    override fun getNamespacesStream(clusterId: String): Flow<List<NamespaceEntity>> =
        namespaces.map { all -> all.filter { it.clusterId == clusterId }.sortedBy { it.name } }

    override suspend fun insertNamespaces(namespaces: List<NamespaceEntity>) {
        this.namespaces.value += namespaces
    }

    override suspend fun deleteNamespacesForCluster(clusterId: String) {
        namespaces.value = namespaces.value.filterNot { it.clusterId == clusterId }
    }

    override suspend fun deleteNamespace(clusterId: String, name: String) {
        namespaces.value = namespaces.value.filterNot { it.clusterId == clusterId && it.name == name }
    }
}
