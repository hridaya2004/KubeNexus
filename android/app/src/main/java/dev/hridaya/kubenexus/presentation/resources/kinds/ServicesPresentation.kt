package dev.hridaya.kubenexus.presentation.resources.kinds

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.ui.graphics.vector.ImageVector
import dev.hridaya.kubenexus.domain.model.ServicePortDetail
import dev.hridaya.kubenexus.domain.model.ServiceSummary
import dev.hridaya.kubenexus.domain.model.resource.ResourceKind
import dev.hridaya.kubenexus.domain.model.resource.ResourceKinds
import dev.hridaya.kubenexus.presentation.resources.ResourcePresentation
import dev.hridaya.kubenexus.presentation.resources.ResourceRow
import dev.hridaya.kubenexus.presentation.resources.RowDetail

object ServicesPresentation : ResourcePresentation<ServiceSummary> {

    override val kind: ResourceKind<ServiceSummary, *> = ResourceKinds.Services
    override val title = "Services"
    override val description = "Stable network endpoints for Pods"
    override val icon: ImageVector = Icons.Outlined.Dns
    override val searchHint = "Search"

    override fun row(item: ServiceSummary) = ResourceRow(
        id = item.id,
        name = item.name,
        namespace = item.namespace,
        badge = item.type,
        details = listOfNotNull(
            RowDetail(item.clusterIP.ifBlank { "None" }, label = "ClusterIP:", monospace = true),
            item.ports.takeIf { it.isNotEmpty() }?.let { RowDetail(portsSummary(it)) },
        ),
        createdAtMillis = item.creationTimestampMillis,
        searchTerms = listOf(item.type, item.clusterIP),
    )

    /** Compact one-line breakdown, e.g. "80:8080/TCP, 443/TCP". */
    private fun portsSummary(ports: List<ServicePortDetail>): String =
        ports.joinToString(separator = ", ") { port ->
            val mapping = if (port.targetPort == port.port) "${port.port}" else "${port.port}:${port.targetPort}"
            "$mapping/${port.protocol}"
        }
}
