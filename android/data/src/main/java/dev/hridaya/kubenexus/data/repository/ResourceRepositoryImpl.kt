package dev.hridaya.kubenexus.data.repository

import android.util.Log
import dev.hridaya.kubenexus.core.common.dispatcher.DispatcherProvider
import dev.hridaya.kubenexus.core.common.log.APP_LOG_TAG_PREFIX
import dev.hridaya.kubenexus.core.common.result.AppError
import dev.hridaya.kubenexus.core.common.result.Result
import dev.hridaya.kubenexus.core.common.result.flatMap
import dev.hridaya.kubenexus.core.common.result.map
import dev.hridaya.kubenexus.core.common.result.onSuccess
import dev.hridaya.kubenexus.core.security.LogSanitizer
import dev.hridaya.kubenexus.data.nativebridge.KubeNexusNativeBridge
import dev.hridaya.kubenexus.data.resource.CachedObject
import dev.hridaya.kubenexus.data.resource.ManifestParser
import dev.hridaya.kubenexus.data.resource.ResourceCodec
import dev.hridaya.kubenexus.data.resource.ResourceCodecRegistry
import dev.hridaya.kubenexus.data.source.local.dao.ResourceCacheDao
import dev.hridaya.kubenexus.data.source.local.entity.CachedResourceEntity
import dev.hridaya.kubenexus.data.source.local.entity.SyncMetadataEntity
import dev.hridaya.kubenexus.data.source.remote.dto.EventDto
import dev.hridaya.kubenexus.data.source.remote.dto.EventListDto
import dev.hridaya.kubenexus.data.source.remote.dto.K8sJson
import dev.hridaya.kubenexus.domain.model.resource.KubeObjectSummary
import dev.hridaya.kubenexus.domain.model.resource.NamespaceScope
import dev.hridaya.kubenexus.domain.model.resource.ResourceKind
import dev.hridaya.kubenexus.domain.model.resource.ResourceType
import dev.hridaya.kubenexus.domain.repository.ResourceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import javax.inject.Inject

/**
 * The single [ResourceRepository] implementation for every kind.
 *
 * Per-kind knowledge lives in the [ResourceCodec]s held by [codecs]; this class
 * only moves JSON between the bridge, the cache and those codecs.
 */
class ResourceRepositoryImpl @Inject constructor(
    private val clusterCalls: ClusterCallRunner,
    private val nativeBridge: KubeNexusNativeBridge,
    private val cacheDao: ResourceCacheDao,
    private val codecs: ResourceCodecRegistry,
    private val dispatcherProvider: DispatcherProvider,
) : ResourceRepository {

    override fun <S : KubeObjectSummary> observe(
        clusterId: String?,
        kind: ResourceKind<S, *>,
        scope: NamespaceScope,
    ): Flow<List<S>> {
        if (clusterId == null) return flowOf(emptyList())
        val codec = codecs.codecFor(kind)
        val resourceKey = kind.type.key
        val payloads = when (val namespace = scope.namespaceFor(kind.type)) {
            null -> cacheDao.observePayloads(clusterId, resourceKey)
            else -> cacheDao.observePayloadsInNamespace(clusterId, resourceKey, namespace)
        }
        // Room re-emits on any write to the table.
        return payloads
            .distinctUntilChanged()
            .map { rows -> rows.mapNotNull { payload -> codec.summaryFromCacheOrNull(payload) } }
            .flowOn(dispatcherProvider.io)
    }

    override suspend fun sync(
        clusterId: String?,
        kind: ResourceKind<*, *>,
        scope: NamespaceScope,
    ): Result<Unit> =
        clusterCalls.run(clusterId, "Failed to sync ${kind.type.plural}") { cluster ->
            val namespace = scope.namespaceFor(kind.type)
            nativeBridge.listResources(cluster.kubeconfig, kind.type, namespace.orEmpty())
                .map { listJson -> codecs.codecFor(kind).decodeForCache(listJson) }
                .onSuccess { objects -> replaceCache(cluster.id, kind.type, namespace, objects) }
                .map { }
        }

    override fun observeLastSynced(clusterId: String?, kind: ResourceKind<*, *>): Flow<Long?> {
        if (clusterId == null) return flowOf(null)
        return cacheDao.observeLastSynced(syncKey(clusterId, kind.type))
            .flowOn(dispatcherProvider.io)
    }

    override suspend fun <S : KubeObjectSummary> list(
        clusterId: String?,
        kind: ResourceKind<S, *>,
        scope: NamespaceScope,
        labelSelector: String,
    ): Result<List<S>> =
        clusterCalls.run(clusterId, "Failed to list ${kind.type.plural}") { cluster ->
            val namespace = scope.namespaceFor(kind.type).orEmpty()
            nativeBridge.listResources(cluster.kubeconfig, kind.type, namespace, labelSelector)
                .map { listJson -> codecs.codecFor(kind).decodeSummaries(listJson) }
        }

    override suspend fun <D : Any> describe(
        clusterId: String?,
        kind: ResourceKind<*, D>,
        namespace: String,
        name: String,
    ): Result<D> =
        clusterCalls.run(clusterId, "Failed to describe ${kind.type.kind} '$name'") { cluster ->
            val codec = codecs.codecFor(kind)
            nativeBridge.getResource(cluster.kubeconfig, kind.type, namespace, name)
                .map { objectJson ->
                    val events = if (codec.showsEvents) {
                        eventsFor(cluster.kubeconfig, kind.type, namespace, name)
                    } else {
                        emptyList()
                    }
                    codec.toDetails(objectJson, events)
                }
        }

    override suspend fun delete(
        clusterId: String?,
        kind: ResourceKind<*, *>,
        namespace: String,
        name: String,
    ): Result<Unit> =
        clusterCalls.run(clusterId, "Failed to delete ${kind.type.kind} '$name'") { cluster ->
            nativeBridge.deleteResource(cluster.kubeconfig, kind.type, namespace, name).onSuccess {
                val cachedNamespace = if (kind.type.namespaced) namespace else ""
                cacheDao.deleteById(
                    CachedResourceEntity.idOf(
                        cluster.id,
                        kind.type.key,
                        cachedNamespace,
                        name
                    )
                )
            }
        }

    override suspend fun create(clusterId: String?, manifest: String): Result<Unit> =
        ManifestParser.parse(manifest).flatMap { documents ->
            val types = documents.map { document ->
                codecs.typeOf(document.apiVersion, document.kind)
                    ?: return@flatMap Result.Error(
                        AppError.Validation("Creating '${document.kind}' (${document.apiVersion}) is not supported"),
                    )
            }
            clusterCalls.run(clusterId, "Failed to create resources") { cluster ->
                for ((index, pair) in documents.zip(types).withIndex()) {
                    val (document, type) = pair
                    val created =
                        nativeBridge.createResource(cluster.kubeconfig, type, document.text)
                    if (created is Result.Error && index > 0) {
                        return@run Result.Error(
                            AppError.Unknown(
                                "Created ${documents.take(index).joinToString { it.label }}, " +
                                        "but ${document.label} failed: ${created.error.message}",
                            ),
                        )
                    }
                    if (created is Result.Error) return@run created
                }
                Result.Success(Unit)
            }
        }

    private suspend fun replaceCache(
        clusterId: String,
        type: ResourceType,
        namespace: String?,
        objects: List<CachedObject>,
    ) {
        val now = System.currentTimeMillis()
        cacheDao.replace(
            clusterId = clusterId,
            resourceKey = type.key,
            namespace = namespace,
            rows = objects.map { cached ->
                CachedResourceEntity(
                    id = CachedResourceEntity.idOf(
                        clusterId,
                        type.key,
                        cached.namespace,
                        cached.name
                    ),
                    clusterId = clusterId,
                    resourceKey = type.key,
                    namespace = cached.namespace,
                    name = cached.name,
                    payload = cached.payload,
                    lastUpdated = now,
                )
            },
            syncMetadata = SyncMetadataEntity(
                key = syncKey(clusterId, type),
                clusterId = clusterId,
                resourceType = type.key,
                lastRefreshedAt = now,
            ),
        )
    }

    /**
     * Events are a separate, field-selected list. Failing to load them must not
     * lose the object itself, which is the primary payload.
     */
    private fun eventsFor(
        kubeconfig: String,
        type: ResourceType,
        namespace: String,
        name: String
    ): List<EventDto> =
        when (val events = nativeBridge.listEvents(kubeconfig, namespace, type.kind, name)) {
            is Result.Success -> try {
                K8sJson.decodeFromString(EventListDto.serializer(), events.data).items
            } catch (e: SerializationException) {
                Log.w(
                    TAG,
                    "Unreadable events for ${type.kind} '$name': ${LogSanitizer.sanitize(e.message)}"
                )
                emptyList()
            }

            is Result.Error -> {
                Log.w(
                    TAG,
                    "Failed to load events for ${type.kind} '$name': ${LogSanitizer.sanitize(events.error.message)}"
                )
                emptyList()
            }

            is Result.Loading -> emptyList()
        }

    /**
     * A row written by an older projection can stop decoding after an update.
     * It is skipped rather than failing the whole list; the next sync rewrites it.
     */
    private fun <S : KubeObjectSummary> ResourceCodec<S, *>.summaryFromCacheOrNull(payload: String): S? =
        try {
            summaryFromCache(payload)
        } catch (e: SerializationException) {
            Log.w(
                TAG,
                "Skipping unreadable cached ${kind.type.plural}: ${LogSanitizer.sanitize(e.message)}"
            )
            null
        }

    /** The namespace to list, or null for every namespace (always so for cluster-scoped types). */
    private fun NamespaceScope.namespaceFor(type: ResourceType): String? = when {
        !type.namespaced -> null
        this is NamespaceScope.Only -> namespace
        else -> null
    }

    private fun syncKey(clusterId: String, type: ResourceType): String = "${clusterId}_${type.key}"

    private companion object {
        const val TAG = "$APP_LOG_TAG_PREFIX/ResourceRepositoryImpl"
    }
}
