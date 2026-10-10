package dev.hridaya.kubenexus.presentation.resources

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.ui.graphics.vector.ImageVector
import dev.hridaya.kubenexus.domain.model.resource.ResourceType
import dev.hridaya.kubenexus.presentation.resources.kinds.DeploymentsPresentation
import dev.hridaya.kubenexus.presentation.resources.kinds.PodsPresentation
import dev.hridaya.kubenexus.presentation.resources.kinds.ServicesPresentation

/** A titled group of kinds, as Home lists them. */
data class ResourceGroup(
    val title: String,
    val resources: List<ResourcePresentation<*>>,
    /** Kinds announced in the group but not supported yet. */
    val upcoming: List<UpcomingResource> = emptyList(),
)

data class UpcomingResource(
    val title: String,
    val description: String,
    val icon: ImageVector,
)

/** Every resource kind the app presents. Adding a kind to the UI starts here. */
object ResourceCatalog {

    val groups: List<ResourceGroup> = listOf(
        ResourceGroup(
            title = "Workloads",
            resources = listOf(PodsPresentation, DeploymentsPresentation, ServicesPresentation),
            upcoming = listOf(
                UpcomingResource(
                    title = "ReplicaSets",
                    description = "Maintain stable set of replica Pods",
                    icon = Icons.Outlined.Widgets,
                ),
            ),
        ),
    )

    val all: List<ResourcePresentation<*>> = groups.flatMap { it.resources }

    private val byKey: Map<String, ResourcePresentation<*>> = all.associateBy { it.kind.type.key }

    /** The presentation for [ResourceType.key]; navigation routes carry the key. */
    fun of(typeKey: String): ResourcePresentation<*> =
        checkNotNull(byKey[typeKey]) { "No presentation is registered for '$typeKey'" }
}
