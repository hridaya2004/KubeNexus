package dev.hridaya.kubenexus.presentation.pods.detail.components

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.hridaya.kubenexus.domain.model.PodConditionDetail
import dev.hridaya.kubenexus.ui.theme.KubeNexusTheme
import dev.hridaya.kubenexus.ui.theme.LocalStatusColors

@Composable
fun ConditionRow(
    condition: PodConditionDetail,
    modifier: Modifier = Modifier,
) {
    val statusColors = LocalStatusColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = condition.type,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Normal,
            )
            val reason = condition.reason
            if (!reason.isNullOrBlank()) {
                Text(
                    text = "Reason: $reason",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            condition.lastTransitionMillis?.let { lastTransitionMillis ->
                Text(
                    text = DateUtils.getRelativeTimeSpanString(lastTransitionMillis).toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        val isTrue = condition.status.equals("True", ignoreCase = true)
        Icon(
            imageVector = if (isTrue) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
            contentDescription = condition.status,
            tint = if (isTrue) statusColors.connected else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ConditionRowPreview() {
    KubeNexusTheme {
        ConditionRow(
            condition = PodConditionDetail(
                type = "Ready",
                status = "True",
                reason = "PodCompleted",
            ),
        )
    }
}
