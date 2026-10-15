package dev.hridaya.kubenexus.presentation.deployments.detail.components

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.hridaya.kubenexus.domain.model.DeploymentCondition
import dev.hridaya.kubenexus.ui.theme.LocalStatusColors

@Composable
internal fun DeploymentConditionsCard(
    conditions: List<DeploymentCondition>,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            conditions.forEachIndexed { index, condition ->
                DeploymentConditionRow(condition = condition)
                if (index < conditions.size - 1) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                }
            }
        }
    }
}

@Composable
private fun DeploymentConditionRow(
    condition: DeploymentCondition,
    modifier: Modifier = Modifier,
) {
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
            if (!condition.reason.isNullOrBlank()) {
                Text(
                    text = "Reason: ${condition.reason}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            condition.lastUpdateMillis?.let { lastUpdateMillis ->
                Text(
                    text = DateUtils.getRelativeTimeSpanString(lastUpdateMillis).toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        ConditionStatusIcon(status = condition.status)
    }
}

@Composable
private fun ConditionStatusIcon(
    status: String,
    modifier: Modifier = Modifier,
) {
    val statusColors = LocalStatusColors.current
    val isTrue = status.equals("True", ignoreCase = true)
    Icon(
        imageVector = if (isTrue) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
        contentDescription = status,
        tint = if (isTrue) statusColors.connected else MaterialTheme.colorScheme.error,
        modifier = modifier.size(18.dp),
    )
}
