package dev.hridaya.kubenexus.domain.model.resource

/**
 * The fields every list-row model shares, whatever its kind.
 *
 * Generic screens and use cases rely only on this, so they can key, search and
 * open any resource without knowing its concrete type.
 */
interface KubeObjectSummary {
    /** Stable within one cluster; suitable as a list key. */
    val id: String
    val name: String

    /** Empty for cluster-scoped objects. */
    val namespace: String

    /** Epoch millis; null when the API server omitted it. */
    val creationTimestampMillis: Long?
}
