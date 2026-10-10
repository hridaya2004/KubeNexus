package dev.hridaya.kubenexus.data.resource

import dev.hridaya.kubenexus.data.source.remote.dto.EventDto
import dev.hridaya.kubenexus.domain.model.resource.KubeObjectSummary
import dev.hridaya.kubenexus.domain.model.resource.ResourceKind

/**
 * Translates one resource kind between the API server's JSON, the offline
 * cache and its domain models.
 *
 * A codec is the only per-kind code the data layer needs: listing, caching,
 * describing, creating and deleting are done once by
 * [dev.hridaya.kubenexus.data.repository.ResourceRepositoryImpl]. Extend
 * [JsonResourceCodec] rather than implementing this directly.
 */
interface ResourceCodec<Summary : KubeObjectSummary, Details : Any> {

    val kind: ResourceKind<Summary, Details>

    /** Whether the describe view shows events, so they are only fetched when used. */
    val showsEvents: Boolean

    /** Decodes a Kubernetes list response into summaries. */
    fun decodeSummaries(listJson: String): List<Summary>

    /** Decodes a Kubernetes list response into the rows the cache stores. */
    fun decodeForCache(listJson: String): List<CachedObject>

    /** Rebuilds a summary from a payload produced by [decodeForCache]. */
    fun summaryFromCache(payload: String): Summary

    /** Builds the describe view from one object's verbatim JSON and its events. */
    fun toDetails(objectJson: String, events: List<EventDto>): Details
}

/** One object as the cache stores it; see [ResourceCodec.decodeForCache]. */
data class CachedObject(
    val namespace: String,
    val name: String,
    val payload: String,
)
