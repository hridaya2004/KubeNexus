package dev.hridaya.kubenexus.presentation.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.hridaya.kubenexus.domain.model.Cluster
import dev.hridaya.kubenexus.presentation.home.ClusterTestStatus
import dev.hridaya.kubenexus.ui.theme.KubeNexusTheme
import dev.hridaya.kubenexus.ui.theme.statusColors

@Composable
fun ManageClusterCard(
    cluster: Cluster,
    onSelectActive: () -> Unit,
    onTestConnection: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    testStatus: ClusterTestStatus = ClusterTestStatus.IDLE,
) {
    val active = cluster.isActive
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (active) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ClusterIcon(active = active)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = cluster.name,
                        style = if (active) {
                            MaterialTheme.typography.titleMediumEmphasized
                        } else {
                            MaterialTheme.typography.titleMedium
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = cluster.serverUrl,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "Context ${cluster.contextName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                ClusterOverflowMenu(onEdit = onEdit, onDelete = onDelete)
            }

            if (cluster.credentialsUnavailable) {
                CredentialsWarning()
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatusPills(
                    active = active,
                    testStatus = testStatus,
                    modifier = Modifier.weight(1f)
                )
                PrimaryAction(
                    active = active,
                    testStatus = testStatus,
                    onSelectActive = onSelectActive,
                    onTestConnection = onTestConnection,
                )
            }
        }
    }
}

@Composable
private fun ClusterIcon(active: Boolean) {
    val shape = MaterialShapes.Cookie9Sided.toShape()
    Surface(
        shape = shape,
        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(40.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Outlined.Dns,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun ClusterOverflowMenu(onEdit: () -> Unit, onDelete: () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { expanded = true },
            shapes = IconButtonDefaults.shapes(),
            modifier = Modifier.size(IconButtonDefaults.extraSmallContainerSize()),
        ) {
            Icon(
                imageVector = Icons.Outlined.MoreVert,
                contentDescription = "More options",
                modifier = Modifier.size(IconButtonDefaults.extraSmallIconSize),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Rename") },
                leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                onClick = {
                    expanded = false
                    onEdit()
                },
            )
            DropdownMenuItem(
                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
                onClick = {
                    expanded = false
                    onDelete()
                },
            )
        }
    }
}

@Composable
private fun StatusPills(
    active: Boolean,
    testStatus: ClusterTestStatus,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (active) {
            StatusPill(
                label = "Active",
                container = MaterialTheme.colorScheme.primary,
                content = MaterialTheme.colorScheme.onPrimary,
            )
        }
        when (testStatus) {
            ClusterTestStatus.HEALTHY -> StatusPill(
                label = "Reachable",
                container = statusColors.successContainer,
                content = statusColors.onSuccessContainer,
            )

            ClusterTestStatus.UNHEALTHY -> StatusPill(
                label = "Unreachable",
                container = MaterialTheme.colorScheme.errorContainer,
                content = MaterialTheme.colorScheme.onErrorContainer,
            )

            ClusterTestStatus.IDLE, ClusterTestStatus.TESTING -> Unit
        }
    }
}

@Composable
private fun StatusPill(label: String, container: Color, content: Color) {
    Surface(shape = MaterialTheme.shapes.small, color = container, contentColor = content) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMediumEmphasized,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun CredentialsWarning() {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(12.dp)) {
            Icon(
                imageVector = Icons.Outlined.WarningAmber,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Saved credentials can't be read on this device. Delete this cluster and add it again.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun PrimaryAction(
    active: Boolean,
    testStatus: ClusterTestStatus,
    onSelectActive: () -> Unit,
    onTestConnection: () -> Unit,
) {
    if (!active) {
        FilledTonalButton(onClick = onSelectActive, shapes = ButtonDefaults.shapes()) {
            Text("Set active")
        }
        return
    }

    val testing = testStatus == ClusterTestStatus.TESTING
    OutlinedButton(
        onClick = onTestConnection,
        enabled = !testing,
        shapes = ButtonDefaults.shapes()
    ) {
        if (testing) {
            CircularProgressIndicator(
                modifier = Modifier.size(ButtonDefaults.IconSize),
                strokeWidth = 2.dp,
            )
            Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
        }
        Text(
            when (testStatus) {
                ClusterTestStatus.IDLE -> "Test connection"
                ClusterTestStatus.TESTING -> "Testing"
                ClusterTestStatus.HEALTHY, ClusterTestStatus.UNHEALTHY -> "Test again"
            },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ManageClusterCardPreview() {
    KubeNexusTheme {
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            ManageClusterCard(
                cluster = Cluster(
                    id = "1",
                    name = "production-cluster",
                    serverUrl = "https://k8s.example.com:6443",
                    contextName = "prod-context",
                    userName = "admin",
                    namespace = "default",
                    isActive = true,
                ),
                testStatus = ClusterTestStatus.HEALTHY,
                onSelectActive = {},
                onTestConnection = {},
                onEdit = {},
                onDelete = {},
            )
            ManageClusterCard(
                cluster = Cluster(
                    id = "2",
                    name = "staging",
                    serverUrl = "https://staging.example.com:6443",
                    contextName = "staging",
                    credentialsUnavailable = true,
                ),
                onSelectActive = {},
                onTestConnection = {},
                onEdit = {},
                onDelete = {},
            )
        }
    }
}
