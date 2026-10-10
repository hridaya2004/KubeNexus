package dev.hridaya.kubenexus.domain.model.resource

/**
 * Which namespaces a list covers.
 *
 * Replaces the "All Namespaces" / blank / "all" string conventions that every
 * repository used to re-check on its own.
 */
sealed interface NamespaceScope {

    /** Every namespace, or the whole cluster for cluster-scoped resources. */
    data object All : NamespaceScope

    data class Only(val namespace: String) : NamespaceScope

    companion object {
        /** A blank [namespace] means [All]; anything else is that one namespace. */
        fun of(namespace: String?): NamespaceScope =
            namespace?.trim()?.takeIf { it.isNotEmpty() }?.let(::Only) ?: All
    }
}
