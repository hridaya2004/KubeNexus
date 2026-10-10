package dev.hridaya.kubenexus.domain.model.resource

/**
 * Identifies a Kubernetes API resource the way the API server addresses it:
 * group, version and plural resource name, plus the object kind it serves.
 *
 * This is the only per-kind information the generic data layer needs to list,
 * describe, create or delete objects, so adding a resource to the app starts by
 * declaring one of these rather than a new set of bridge methods.
 */
data class ResourceType(
    /** API group; empty for the core group (pods, services, configmaps). */
    val group: String,
    val version: String,
    /** Lowercase plural resource name, e.g. "deployments". */
    val plural: String,
    /** Object kind, e.g. "Deployment", as it appears in manifests and events. */
    val kind: String,
    val namespaced: Boolean = true,
) {

    /** The manifest `apiVersion`, e.g. "v1" or "apps/v1". */
    val apiVersion: String
        get() = if (group.isEmpty()) version else "$group/$version"

    /** Stable identifier such as "apps/v1/deployments", used for cache rows and navigation. */
    val key: String
        get() = "$apiVersion/$plural"
}
