package dev.hridaya.kubenexus.presentation.common

import dev.hridaya.kubenexus.domain.model.resource.NamespaceScope

/** The namespace filter chip that lists every namespace. Always offered first. */
const val ALL_NAMESPACES_FILTER = "All Namespaces"

/** The chips for a namespace filter: [ALL_NAMESPACES_FILTER] followed by [namespaces]. */
fun namespaceFilterOptions(namespaces: List<String>): List<String> =
    listOf(ALL_NAMESPACES_FILTER) + namespaces.filter { it.isNotBlank() && it != ALL_NAMESPACES_FILTER }
        .distinct()

/** The scope a namespace filter chip selects. */
fun namespaceScopeOf(filter: String): NamespaceScope =
    if (isAllNamespaces(filter)) NamespaceScope.All else NamespaceScope.of(filter)

/** The namespace a filter chip names, or null for [ALL_NAMESPACES_FILTER]. */
fun namespaceOrNull(filter: String): String? = filter.takeUnless(::isAllNamespaces)

fun isAllNamespaces(filter: String): Boolean =
    filter.isBlank() || filter.equals(ALL_NAMESPACES_FILTER, ignoreCase = true)
