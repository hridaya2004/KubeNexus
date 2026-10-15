package dev.hridaya.kubenexus.presentation.resources

import androidx.compose.ui.graphics.vector.ImageVector
import dev.hridaya.kubenexus.domain.model.resource.KubeObjectSummary
import dev.hridaya.kubenexus.domain.model.resource.ResourceKind

/**
 * How one resource kind appears in the shared screens: its names, its icon and
 * how a summary becomes a list row.
 *
 * This is the presentation counterpart of the data layer's codec. A kind with
 * one of these is listed, searched, filtered and refreshed by the shared list
 * screen without any screen code of its own.
 */
interface ResourcePresentation<Summary : KubeObjectSummary> {

    val kind: ResourceKind<Summary, *>

    /** Plural, as used for screen titles: "Services". */
    val title: String

    /** Singular, as used in actions and messages: "Service". */
    val singularTitle: String
        get() = kind.type.kind

    /** One line on what the kind is for, shown on Home. */
    val description: String

    val icon: ImageVector

    /** Placeholder for the list's search box. */
    val searchHint: String
        get() = "Search"

    fun row(item: Summary): ResourceRow
}
