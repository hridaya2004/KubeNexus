package dev.hridaya.kubenexus.data.resource

import dev.hridaya.kubenexus.data.source.remote.dto.K8sJson
import dev.hridaya.kubenexus.data.source.remote.dto.K8sListDto
import dev.hridaya.kubenexus.data.source.remote.dto.KubeObjectDto
import dev.hridaya.kubenexus.domain.model.resource.KubeObjectSummary
import dev.hridaya.kubenexus.domain.model.resource.ResourceKind
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * A [ResourceCodec] whose list rows are decoded into a kotlinx.serialization
 * [ListDto] and mapped from there.
 *
 * [listSerializer] is also the cache format: an object is re-encoded from the
 * fields [ListDto] declares, so cached rows hold only what the summary needs.
 * That makes the projection a privacy boundary as much as a size one; a kind
 * whose objects carry secrets keeps them out of [ListDto] and they never reach
 * disk. Annotations are dropped too: `last-applied-configuration` holds the whole manifest.
 */
abstract class JsonResourceCodec<ListDto : KubeObjectDto, Summary : KubeObjectSummary, Details : Any>(
    final override val kind: ResourceKind<Summary, Details>,
    private val listSerializer: KSerializer<ListDto>,
) : ResourceCodec<Summary, Details> {

    override val showsEvents: Boolean = true

    protected abstract fun toSummary(dto: ListDto): Summary

    final override fun decodeSummaries(listJson: String): List<Summary> =
        decodeItems(listJson).map(::toSummary)

    final override fun decodeForCache(listJson: String): List<CachedObject> =
        decodeItems(listJson).map { dto ->
            CachedObject(
                namespace = namespaceOf(dto),
                name = dto.metadata.name,
                payload = encodeForCache(dto),
            )
        }

    final override fun summaryFromCache(payload: String): Summary =
        toSummary(K8sJson.decodeFromString(listSerializer, payload))

    private fun encodeForCache(dto: ListDto): String {
        val json = K8sJson.encodeToJsonElement(listSerializer, dto).jsonObject
        val metadata = json[METADATA]?.jsonObject ?: return json.toString()
        return JsonObject(json + (METADATA to JsonObject(metadata - ANNOTATIONS))).toString()
    }

    private fun decodeItems(listJson: String): List<ListDto> =
        K8sJson.decodeFromString(K8sListDto.serializer(listSerializer), listJson).items

    /** Namespaced objects missing a namespace live in "default", as on the API server. */
    private fun namespaceOf(dto: ListDto): String =
        if (kind.type.namespaced) dto.metadata.namespace.ifBlank { DEFAULT_NAMESPACE } else ""

    private companion object {
        const val DEFAULT_NAMESPACE = "default"
        const val METADATA = "metadata"
        const val ANNOTATIONS = "annotations"
    }
}
