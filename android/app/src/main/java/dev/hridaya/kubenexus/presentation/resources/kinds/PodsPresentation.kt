package dev.hridaya.kubenexus.presentation.resources.kinds

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.ui.graphics.vector.ImageVector
import dev.hridaya.kubenexus.domain.model.Pod
import dev.hridaya.kubenexus.domain.model.PodStatus
import dev.hridaya.kubenexus.domain.model.resource.ResourceKind
import dev.hridaya.kubenexus.domain.model.resource.ResourceKinds
import dev.hridaya.kubenexus.presentation.resources.ResourcePresentation
import dev.hridaya.kubenexus.presentation.resources.ResourceRow
import dev.hridaya.kubenexus.presentation.resources.RowDetail
import dev.hridaya.kubenexus.presentation.resources.StatusTone

object PodsPresentation : ResourcePresentation<Pod> {

    override val kind: ResourceKind<Pod, *> = ResourceKinds.Pods
    override val title = "Pods"
    override val description = "Container instances and workload state"
    override val icon: ImageVector = Icons.Outlined.Layers
    override val searchHint = "Search pods by name, IP, node or status"

    override fun row(item: Pod) = ResourceRow(
        id = item.id,
        name = item.name,
        namespace = item.namespace,
        status = item.status.tone,
        details = listOfNotNull(
            RowDetail("${item.readyContainers} ready • ${item.restarts} restarts"),
            item.ip?.let { RowDetail(it, label = "IP:", monospace = true) },
        ),
        createdAtMillis = item.creationTimestampMillis,
        searchTerms = listOfNotNull(item.ip, item.node, item.status.title),
    )

    private val PodStatus.tone: StatusTone
        get() = when (this) {
            PodStatus.RUNNING -> StatusTone.Healthy
            PodStatus.PENDING -> StatusTone.Pending
            PodStatus.COMPLETED -> StatusTone.Completed
            PodStatus.FAILED, PodStatus.CRASH_LOOP -> StatusTone.Failed
            PodStatus.UNKNOWN -> StatusTone.Unknown
        }
}
