package dev.hridaya.kubenexus.data.resource

import dev.hridaya.kubenexus.domain.model.resource.KubeObjectSummary
import dev.hridaya.kubenexus.domain.model.resource.ResourceKind
import dev.hridaya.kubenexus.domain.model.resource.ResourceType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Looks up the [ResourceCodec] for a kind, or for a manifest's apiVersion and kind.
 *
 * Codecs are contributed into a Hilt set (see `ResourceCodecModule`), so adding
 * a kind never edits this class.
 */
@Singleton
class ResourceCodecRegistry @Inject constructor(
    codecs: Set<@JvmSuppressWildcards ResourceCodec<*, *>>,
) {

    private val codecsByType: Map<ResourceType, ResourceCodec<*, *>> =
        codecs.associateBy { it.kind.type }

    private val typesByManifestKind: Map<ManifestKind, ResourceType> =
        codecsByType.keys.associateBy { ManifestKind(it.apiVersion, it.kind) }

    init {
        require(codecsByType.size == codecs.size) {
            "More than one codec is registered for the same resource type"
        }
    }

    /**
     * The codec for [kind].
     *
     * The cast is safe because codecs are keyed by [ResourceType] and a
     * [ResourceKind] is identified by its type alone: the registered codec's kind
     * is the very kind that declared [Summary] and [Details].
     */
    @Suppress("UNCHECKED_CAST")
    fun <Summary : KubeObjectSummary, Details : Any> codecFor(
        kind: ResourceKind<Summary, Details>,
    ): ResourceCodec<Summary, Details> =
        checkNotNull(codecsByType[kind.type]) { "No codec is registered for $kind" }
            as ResourceCodec<Summary, Details>

    /** The resource a manifest of [apiVersion] and [kind] creates, if the app models it. */
    fun typeOf(apiVersion: String, kind: String): ResourceType? =
        typesByManifestKind[ManifestKind(apiVersion, kind)]

    private data class ManifestKind(val apiVersion: String, val kind: String)
}
