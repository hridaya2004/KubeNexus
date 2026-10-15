package dev.hridaya.kubenexus.presentation.home.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.hridaya.kubenexus.domain.model.Cluster
import dev.hridaya.kubenexus.domain.model.ClusterConnectionStatus
import dev.hridaya.kubenexus.ui.theme.KubeNexusTheme
import dev.hridaya.kubenexus.ui.theme.LocalStatusColors

private val MinTileWidth = 140.dp
private val TileGap = 8.dp

/** Home category of clusters as a wrapping grid of single-choice tiles, ending with a Manage clusters tile. */
@Composable
fun ClusterSelectorSection(
    clusters: List<Cluster>,
    activeClusterId: String?,
    connectionStatus: ClusterConnectionStatus,
    onSelectCluster: (String) -> Unit,
    onManageClustersClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // A null entry is the trailing Manage clusters tile.
    val entries: List<Cluster?> = clusters + null

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ResourceGroupHeader(title = "Cluster", lastRefreshedAt = null)
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val columns = ((maxWidth + TileGap) / (MinTileWidth + TileGap)).toInt().coerceAtLeast(2)
            Column(verticalArrangement = Arrangement.spacedBy(TileGap)) {
                entries.chunked(columns).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(TileGap)) {
                        row.forEach { cluster ->
                            val tileModifier = Modifier.weight(1f)
                            if (cluster == null) {
                                ClusterTile(
                                    label = "Manage clusters",
                                    icon = Icons.Outlined.Tune,
                                    selected = false,
                                    onClick = onManageClustersClick,
                                    modifier = tileModifier,
                                )
                            } else {
                                val isSelected = cluster.id == activeClusterId
                                ClusterTile(
                                    label = cluster.name,
                                    icon = Icons.Outlined.CloudQueue,
                                    selected = isSelected,
                                    onClick = { if (!isSelected) onSelectCluster(cluster.id) },
                                    connectionStatus = connectionStatus.takeIf { isSelected },
                                    modifier = tileModifier.semantics { role = Role.RadioButton },
                                )
                            }
                        }
                        repeat(columns - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

/** Vertical tile: icon on top, label below. [connectionStatus] adds a status dot in the corner. */
@Composable
private fun ClusterTile(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    connectionStatus: ClusterConnectionStatus? = null,
) {
    Surface(
        selected = selected,
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        modifier = modifier,
    ) {
        Box {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 20.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (connectionStatus != null) {
                StatusDot(
                    color = LocalStatusColors.current.forConnectionStatus(connectionStatus),
                    pulsing = connectionStatus == ClusterConnectionStatus.CONNECTING,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp),
                )
            }
        }
    }
}

@Composable
private fun StatusDot(color: Color, pulsing: Boolean, modifier: Modifier = Modifier) {
    val pulseModifier = if (pulsing) {
        val pulseAlpha = rememberInfiniteTransition(label = "connecting_pulse").animateFloat(
            initialValue = 0.4f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "pulse_alpha",
        )
        Modifier.graphicsLayer { alpha = pulseAlpha.value }
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            .size(10.dp)
            .then(pulseModifier)
            .background(color = color, shape = CircleShape),
    )
}

@Preview(showBackground = true)
@Composable
private fun ClusterSelectorSectionPreview() {
    KubeNexusTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            ClusterSelectorSection(
                clusters = listOf(
                    Cluster(
                        id = "1",
                        name = "production-cluster",
                        serverUrl = "https://k8s.example.com:6443",
                        contextName = "prod",
                        userName = "admin",
                        namespace = "default",
                        isActive = true,
                    ),
                    Cluster(
                        id = "2",
                        name = "minikube",
                        serverUrl = "https://127.0.0.1:8443",
                        contextName = "minikube",
                        userName = "minikube",
                        namespace = "default",
                    ),
                ),
                activeClusterId = "1",
                connectionStatus = ClusterConnectionStatus.CONNECTED,
                onSelectCluster = {},
                onManageClustersClick = {},
            )
        }
    }
}
