package dev.hridaya.kubenexus.domain.model.resource

/**
 * A resource the app models, together with the types it is presented as:
 * [Summary] for list rows and [Details] for the describe view.
 *
 * Repositories and use cases take a kind instead of exposing one method per
 * resource, and the type parameters keep that single code path type-safe:
 * observing [ResourceKinds.Services] yields `List<ServiceSummary>`.
 *
 * Equality is by [type] alone; two kinds for the same API resource cannot
 * coexist, which the data layer enforces when its codecs are registered.
 */
class ResourceKind<Summary : KubeObjectSummary, Details : Any>(val type: ResourceType) {

    override fun equals(other: Any?): Boolean = other is ResourceKind<*, *> && other.type == type

    override fun hashCode(): Int = type.hashCode()

    override fun toString(): String = "ResourceKind(${type.key})"
}
