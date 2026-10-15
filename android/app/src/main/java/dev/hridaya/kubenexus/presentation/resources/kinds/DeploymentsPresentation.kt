package dev.hridaya.kubenexus.presentation.resources.kinds

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.ui.graphics.vector.ImageVector
import dev.hridaya.kubenexus.domain.model.DeploymentSummary
import dev.hridaya.kubenexus.domain.model.resource.ResourceKind
import dev.hridaya.kubenexus.domain.model.resource.ResourceKinds
import dev.hridaya.kubenexus.presentation.resources.ResourcePresentation
import dev.hridaya.kubenexus.presentation.resources.ResourceRow
import dev.hridaya.kubenexus.presentation.resources.RowDetail
import dev.hridaya.kubenexus.presentation.resources.StatusTone

object DeploymentsPresentation : ResourcePresentation<DeploymentSummary> {

    override val kind: ResourceKind<DeploymentSummary, *> = ResourceKinds.Deployments
    override val title = "Deployments"
    override val description = "Declarative updates for Pods and ReplicaSets"
    override val icon: ImageVector = Icons.Outlined.Apps
    override val searchHint = "Search"

    override fun row(item: DeploymentSummary): ResourceRow {
        val readiness = "${item.readyReplicas}/${item.desiredReplicas}"
        return ResourceRow(
            id = item.id,
            name = item.name,
            namespace = item.namespace,
            status = if (item.isHealthy) StatusTone.Healthy else StatusTone.Failed,
            details = listOf(RowDetail("$readiness ready")) +
                    item.images.map { image -> RowDetail(image, monospace = true) },
            createdAtMillis = item.creationTimestampMillis,
            searchTerms = item.images + readiness,
        )
    }
}
