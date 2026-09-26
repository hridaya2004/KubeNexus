package dev.hridaya.kubenexus.data.repository

import android.database.sqlite.SQLiteException
import android.util.Log
import dev.hridaya.kubenexus.core.common.dispatcher.DispatcherProvider
import dev.hridaya.kubenexus.core.common.result.AppError
import dev.hridaya.kubenexus.core.common.result.Result
import dev.hridaya.kubenexus.core.security.KubeconfigEncryptor
import dev.hridaya.kubenexus.core.security.LogSanitizer
import dev.hridaya.kubenexus.core.security.NoOpKubeconfigEncryptor
import dev.hridaya.kubenexus.data.mapper.toDomain
import dev.hridaya.kubenexus.data.mapper.toEntity
import dev.hridaya.kubenexus.data.nativebridge.KubeNexusNativeBridge
import dev.hridaya.kubenexus.data.nativebridge.NativeBridgeJsonParser
import dev.hridaya.kubenexus.data.source.local.dao.APIResourceDao
import dev.hridaya.kubenexus.data.source.local.dao.ClusterDao
import dev.hridaya.kubenexus.data.source.local.dao.ExplainedResourceDao
import dev.hridaya.kubenexus.data.source.local.dao.OpenApiSchemaDao
import dev.hridaya.kubenexus.data.source.local.entity.OpenApiSchemaEntity
import dev.hridaya.kubenexus.domain.model.APIResource
import dev.hridaya.kubenexus.domain.model.ResourceExplain
import dev.hridaya.kubenexus.domain.repository.ExploreRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.inject.Inject

class ExploreRepositoryImpl @Inject constructor(
    private val clusterDao: ClusterDao,
    private val apiResourceDao: APIResourceDao,
    private val explainedResourceDao: ExplainedResourceDao,
    private val openApiSchemaDao: OpenApiSchemaDao,
    private val nativeBridge: KubeNexusNativeBridge,
    private val jsonParser: NativeBridgeJsonParser,
    private val encryptor: KubeconfigEncryptor = NoOpKubeconfigEncryptor,
    private val dispatcherProvider: DispatcherProvider,
) : ExploreRepository {

    /** A schema too large to store, kept for the current session: (clusterId, json). */
    @Volatile
    private var oversizedSchema: Pair<String, String>? = null

    companion object {
        /** Leaves headroom under Android's 2 MB CursorWindow for the rest of the row. */
        private const val MAX_STORED_SCHEMA_BYTES = 1_800_000

        private const val TAG = "ExploreRepositoryImpl"
        private const val OFFLINE_CLUSTER_ID = "offline"
    }

    override fun getAPIResourcesStream(clusterId: String?): Flow<List<APIResource>> {
        val resolvedClusterId = clusterId ?: OFFLINE_CLUSTER_ID
        return apiResourceDao.getAPIResourcesStream(resolvedClusterId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getLastRefreshedStream(clusterId: String?): Flow<Long?> {
        val resolvedClusterId = clusterId ?: OFFLINE_CLUSTER_ID
        val key = "${resolvedClusterId}_api_resources"
        return apiResourceDao.getSyncMetadataStream(key)
    }

    override suspend fun fetchAPIResources(clusterId: String?): Result<List<APIResource>> =
        withContext(dispatcherProvider.io) {
            val resolvedClusterId = clusterId ?: OFFLINE_CLUSTER_ID
            val decryptedKubeconfig = if (clusterId != null) {
                val cluster = clusterDao.getClusterById(clusterId)
                if (cluster != null) encryptor.decryptOrEmpty(cluster.rawKubeconfig) else ""
            } else ""

            try {
                val nativeResult = nativeBridge.listAPIResources(decryptedKubeconfig)
                if (nativeResult.isSuccess) {
                    val resources = nativeResult.getOrNull() ?: emptyList()
                    val entities = resources.map { it.toEntity(resolvedClusterId) }
                    apiResourceDao.syncAPIResources(
                        clusterId = resolvedClusterId,
                        resources = entities,
                        timestamp = System.currentTimeMillis(),
                    )

                    val activeIdentifiers = resources.flatMap {
                        listOf(
                            it.name.lowercase(),
                            it.kind.lowercase(),
                            it.singularName.lowercase()
                        )
                    }.filter { it.isNotBlank() }.distinct()

                    if (activeIdentifiers.isEmpty()) {
                        explainedResourceDao.deleteExplainedResourcesForCluster(resolvedClusterId)
                    } else {
                        explainedResourceDao.deleteOrphanedExplainedResources(
                            resolvedClusterId,
                            activeIdentifiers
                        )
                    }

                    Result.Success(resources)
                } else {
                    val error = nativeResult.exceptionOrNull()
                    val sanitizedMsg = LogSanitizer.sanitize(error?.message)
                    Result.Error(AppError.Network(sanitizedMsg.ifEmpty { "Failed to discover API resources" }))
                }
            } catch (t: Throwable) {
                val sanitizedMsg = LogSanitizer.sanitize(t.message)
                Log.e(TAG, LogSanitizer.withStackTrace("Failed to fetch API resources: $sanitizedMsg", t))
                Result.Error(AppError.Network(sanitizedMsg.ifEmpty { "Failed to discover API resources" }))
            }
        }

    override fun getExplainedResourceStream(
        clusterId: String?,
        resourceOrKind: String,
        groupVersion: String,
    ): Flow<ResourceExplain?> {
        val resolvedClusterId = clusterId ?: OFFLINE_CLUSTER_ID
        val normalizedResourceOrKind = resourceOrKind.trim().lowercase()
        return explainedResourceDao.getExplainedResourceStream(
            clusterId = resolvedClusterId,
            resourceOrKind = normalizedResourceOrKind,
            groupVersion = groupVersion,
        ).map { entity -> entity?.toDomain() }
    }

    override suspend fun getCachedExplainedResource(
        clusterId: String?,
        resourceOrKind: String,
        groupVersion: String,
    ): ResourceExplain? = withContext(dispatcherProvider.io) {
        val resolvedClusterId = clusterId ?: OFFLINE_CLUSTER_ID
        val normalizedResourceOrKind = resourceOrKind.trim().lowercase()
        explainedResourceDao.getExplainedResource(
            clusterId = resolvedClusterId,
            resourceOrKind = normalizedResourceOrKind,
            groupVersion = groupVersion,
        )?.toDomain()
    }

    override suspend fun explainResource(
        clusterId: String?,
        resourceOrKind: String,
        groupVersion: String,
        forceRefresh: Boolean,
    ): Result<ResourceExplain> = withContext(dispatcherProvider.io) {
        // The schema is a multi-megabyte document from the cluster; a malformed one, or one
        // too large to parse on this device, must end in an error, not a crash.
        try {
            explainResourceUnchecked(clusterId, resourceOrKind, groupVersion, forceRefresh)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, LogSanitizer.withStackTrace("Explaining $resourceOrKind failed", e))
            Result.Error(AppError.Unknown("Couldn't read the API documentation for $resourceOrKind.", e))
        } catch (e: OutOfMemoryError) {
            Result.Error(AppError.Unknown("The cluster's API documentation is too large to load on this device."))
        }
    }

    private suspend fun explainResourceUnchecked(
        clusterId: String?,
        resourceOrKind: String,
        groupVersion: String,
        forceRefresh: Boolean,
    ): Result<ResourceExplain> {
        val resolvedClusterId = clusterId ?: OFFLINE_CLUSTER_ID
        val normalizedResourceOrKind = resourceOrKind.trim().lowercase()

        // kubectl-style resolution: discovery maps the name, singular name or
        // kind to an exact GVK, which handles irregular plurals ("policies" ->
        // Policy) and every custom resource installed on this cluster.
        val resolvedGVK = apiResourceDao.getAPIResourcesList(resolvedClusterId)
            .firstOrNull {
                it.name.lowercase() == normalizedResourceOrKind ||
                        it.kind.lowercase() == normalizedResourceOrKind ||
                        it.singularName.lowercase() == normalizedResourceOrKind
            }

        fun locate(schemaJson: String?): ResourceExplain? {
            // Parsed once: the GVK lookup and the name-based fallback below would
            // otherwise each re-parse a multi-megabyte document.
            val definitions = schemaJson?.let { jsonParser.parseDefinitions(it) } ?: return null
            return resolvedGVK?.let {
                jsonParser.findDefinitionByGVK(definitions, it.group, it.version, it.kind)
            } ?: jsonParser.findDefinition(definitions, resourceOrKind, groupVersion)
        }

        var hadStoredSchema = false
        var schemaJson: String? = null
        if (!forceRefresh) {
            schemaJson = storedSchema(resolvedClusterId)
            hadStoredSchema = schemaJson != null
        }
        var explain = locate(schemaJson)

        if (explain == null) {
            when (val fetched = fetchFreshSchema(clusterId, resolvedClusterId)) {
                is Result.Success -> {
                    schemaJson = fetched.data
                    explain = locate(schemaJson)
                }

                is Result.Error -> if (!hadStoredSchema) {
                    val sanitizedMsg = LogSanitizer.sanitize(fetched.error.message)
                    return Result.Error(
                        AppError.Network(sanitizedMsg.ifEmpty { "Failed to fetch schema for $resourceOrKind" })
                    )
                }

                is Result.Loading -> Unit
            }
        }

        if (explain == null) {
            // The schema was retrieved but publishes no definition for this
            // resource, which is the normal case for a CRD whose OpenAPI
            // definitions the API server does not expose. Return the generic
            // object shape, which states in its description that documentation
            // was unavailable, rather than failing outright.
            //
            // Deliberately not persisted: caching a stub would shadow the real
            // documentation if it later becomes available.
            return Result.Success(
                jsonParser.buildFallbackExplain(resourceOrKind, groupVersion),
            )
        }

        explainedResourceDao.insertExplainedResource(
            explain.toEntity(resolvedClusterId, normalizedResourceOrKind),
        )
        return Result.Success(explain)
    }

    private suspend fun fetchFreshSchema(
        clusterId: String?,
        resolvedClusterId: String,
    ): Result<String> = withContext(dispatcherProvider.io) {
        val decryptedKubeconfig = if (clusterId != null) {
            clusterDao.getClusterById(clusterId)?.let { encryptor.decryptOrEmpty(it.rawKubeconfig) } ?: ""
        } else ""

        when (val nativeResult = nativeBridge.openAPISchemaJSON(decryptedKubeconfig)) {
            is Result.Success -> {
                val compressed = gzip(nativeResult.data)
                if (compressed.size <= MAX_STORED_SCHEMA_BYTES) {
                    openApiSchemaDao.upsert(
                        OpenApiSchemaEntity(
                            clusterId = resolvedClusterId,
                            schemaGzip = compressed,
                            fetchedAt = System.currentTimeMillis(),
                        )
                    )
                } else {
                    // Too big for a CursorWindow even compressed (CRD-heavy clusters): a stored
                    // row could be written but never read back. Keep it for this session only.
                    openApiSchemaDao.deleteForCluster(resolvedClusterId)
                    oversizedSchema = resolvedClusterId to nativeResult.data
                }
                Result.Success(nativeResult.data)
            }

            is Result.Error -> {
                val sanitizedMsg = LogSanitizer.sanitize(nativeResult.error.message)
                Result.Error(AppError.Network(sanitizedMsg.ifEmpty { "Failed to fetch OpenAPI schema" }))
            }

            is Result.Loading -> Result.Error(AppError.Network("Schema fetch in progress"))
        }
    }

    /**
     * The cached schema for [clusterId], or null. A row that cannot be read back (larger than
     * the 2 MB CursorWindow, or corrupt) is deleted, so the schema is fetched again instead of
     * failing on every explain.
     */
    private suspend fun storedSchema(clusterId: String): String? {
        oversizedSchema?.let { (id, json) -> if (id == clusterId) return json }
        return try {
            openApiSchemaDao.getForCluster(clusterId)?.let { gunzip(it.schemaGzip) }
        } catch (e: SQLiteException) {
            Log.w(TAG, "Dropping an unreadable cached OpenAPI schema: ${LogSanitizer.sanitize(e.message)}")
            openApiSchemaDao.deleteForCluster(clusterId)
            null
        } catch (e: IOException) {
            openApiSchemaDao.deleteForCluster(clusterId)
            null
        }
    }

    private fun gzip(text: String): ByteArray = ByteArrayOutputStream().also { out ->
        GZIPOutputStream(out).use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }.toByteArray()

    private fun gunzip(bytes: ByteArray): String =
        GZIPInputStream(bytes.inputStream()).use { it.readBytes().toString(Charsets.UTF_8) }
}
