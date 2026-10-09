package dev.hridaya.kubenexus.presentation.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.hridaya.kubenexus.domain.model.Pod
import dev.hridaya.kubenexus.domain.model.PodStatus
import dev.hridaya.kubenexus.presentation.common.components.scaleOnPress
import dev.hridaya.kubenexus.ui.theme.KubeNexusTheme
import dev.hridaya.kubenexus.ui.theme.LocalStatusColors
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

@Composable
fun PodCard(
    pod: Pod,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    showNamespace: Boolean = true,
) {
    val statusColors = LocalStatusColors.current
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .scaleOnPress(targetScale = 0.97f, onClick = onClick),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    val dotColor = statusColors.forPodStatus(pod.status)

                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(color = dotColor, shape = CircleShape),
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = pod.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (showNamespace) {
                    Spacer(modifier = Modifier.width(8.dp))

                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ) {
                        Text(
                            text = pod.namespace,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "${pod.readyContainers} ready • ${pod.restarts} restarts • ${pod.age}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                val podIp = pod.ip
                if (!podIp.isNullOrBlank()) {
                    Text(
                        text = podIp,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PodCardPreview() {
    KubeNexusTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            PodCard(
                pod = Pod(
                    id = "1",
                    name = "nginx-deployment-78f56c879d-gqw87",
                    namespace = "default",
                    status = PodStatus.RUNNING,
                    readyContainers = "1/1",
                    restarts = 0,
                    creationTimestampMillis = System.currentTimeMillis() -
                            (2.days + 4.hours).inWholeMilliseconds,
                    ip = "10.244.0.15",
                    node = "node-1",
                ),
                onClick = {},
            )
        }
    }
}
