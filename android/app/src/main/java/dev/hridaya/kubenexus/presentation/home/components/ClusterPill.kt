package dev.hridaya.kubenexus.presentation.home.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.hridaya.kubenexus.domain.model.Cluster
import dev.hridaya.kubenexus.domain.model.ClusterConnectionStatus
import dev.hridaya.kubenexus.ui.theme.KubeNexusTheme
import dev.hridaya.kubenexus.ui.theme.LocalStatusColors

@Composable
fun ClusterPill(
    activeCluster: Cluster?,
    totalClusters: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    connectionStatus: ClusterConnectionStatus = if (activeCluster != null) {
        ClusterConnectionStatus.CONNECTED
    } else {
        ClusterConnectionStatus.OFFLINE
    },
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 1.dp,
        modifier = modifier.clip(CircleShape),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            val statusColors = LocalStatusColors.current
            val dotColor = statusColors.forConnectionStatus(connectionStatus)

            StatusDot(
                color = dotColor,
                pulsing = connectionStatus == ClusterConnectionStatus.CONNECTING,
            )

            Spacer(modifier = Modifier.width(6.dp))

            val label = when {
                activeCluster != null -> activeCluster.name
                totalClusters > 0 -> "Select Cluster"
                else -> "No Clusters"
            }

            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.width(2.dp))

            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = "Switch cluster",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun StatusDot(color: Color, pulsing: Boolean) {
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
        modifier = Modifier
            .size(8.dp)
            .then(pulseModifier)
            .background(color = color, shape = CircleShape),
    )
}

@Preview(showBackground = true)
@Composable
private fun ClusterPillPreview() {
    KubeNexusTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            ClusterPill(
                activeCluster = Cluster(
                    id = "1",
                    name = "minikube",
                    serverUrl = "https://127.0.0.1:8443",
                    contextName = "minikube",
                    userName = "minikube",
                    namespace = "default",
                ),
                totalClusters = 2,
                onClick = {},
                connectionStatus = ClusterConnectionStatus.CONNECTED,
            )
        }
    }
}
