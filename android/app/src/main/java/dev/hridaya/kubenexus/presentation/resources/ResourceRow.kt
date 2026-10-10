package dev.hridaya.kubenexus.presentation.resources

import androidx.compose.runtime.Immutable

/**
 * What a list shows for one object, whatever its kind.
 *
 * Each kind's [ResourcePresentation] maps its summary into this small, fixed
 * vocabulary, so one card and one list screen render every kind.
 */
@Immutable
data class ResourceRow(
    val id: String,
    val name: String,
    /** Empty for cluster-scoped objects. */
    val namespace: String,
    /** Drawn as a colored dot before the name; null for kinds without a health notion. */
    val status: StatusTone? = null,
    /** A short label beside the name, such as a Service's type. */
    val badge: String? = null,
    val details: List<RowDetail> = emptyList(),
    val createdAtMillis: Long? = null,
    /** Text the search box matches besides [name] and [namespace]. */
    val searchTerms: List<String> = emptyList(),
) {
    fun matches(query: String): Boolean {
        val needle = query.trim()
        if (needle.isEmpty()) return true
        return sequenceOf(name, namespace).plus(searchTerms)
            .any { it.contains(needle, ignoreCase = true) }
    }
}

/** One line under a row's name, optionally led by a [label] such as "ClusterIP:". */
@Immutable
data class RowDetail(
    val text: String,
    val label: String? = null,
    /** For addresses, images and other machine values. */
    val monospace: Boolean = false,
)

/** The health a row's status dot conveys; mapped to theme colors when drawn. */
enum class StatusTone {
    Healthy,
    Pending,
    Completed,
    Failed,
    Unknown,
}
