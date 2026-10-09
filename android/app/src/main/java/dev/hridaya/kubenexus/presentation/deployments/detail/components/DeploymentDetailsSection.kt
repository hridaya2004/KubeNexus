package dev.hridaya.kubenexus.presentation.deployments.detail.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.hridaya.kubenexus.domain.model.DeploymentDetails

/**
 * Everything below the status/images overview cards: strategy, selector,
 * conditions, labels/annotations, events. The overview stays visible while
 * this section loads or fails, mirroring the pods describe behavior of never
 * blocking the whole screen on one fetch.
 */
@Composable
internal fun DeploymentDetailsSection(
    isDetailsLoading: Boolean,
    details: DeploymentDetails?,
    detailsErrorMessage: String?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val describe = details ?: run {
        when {
            isDetailsLoading -> DescribeProgressCard(modifier = modifier)
            else -> DescribeErrorCard(
                errorMessage = detailsErrorMessage,
                onRetry = onRetry,
                modifier = modifier,
            )
        }
        return
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        if (describe.strategyType != null || describe.minReadySeconds != null) {
            DeploymentStrategyCard(
                strategyType = describe.strategyType,
                minReadySeconds = describe.minReadySeconds,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (describe.selectorMatchLabels.isNotEmpty()) {
            DescribeSectionTitle(text = "Selector")
            DeploymentSelectorCard(selectorMatchLabels = describe.selectorMatchLabels)
        }

        if (describe.conditions.isNotEmpty()) {
            DescribeSectionTitle(text = "Conditions")
            DeploymentConditionsCard(conditions = describe.conditions)
        }

        if (describe.labels.isNotEmpty()) {
            DeploymentLabelsCard(labels = describe.labels)
        }

        if (describe.annotations.isNotEmpty()) {
            DeploymentAnnotationsCard(annotations = describe.annotations)
        }

        if (describe.events.isNotEmpty()) {
            DeploymentEventsSection(events = describe.events)
        }
    }
}

/** Inline indicator so the summary card above keeps rendering during load. */
@Composable
private fun DescribeProgressCard(
    modifier: Modifier = Modifier,
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(14.dp),
        ) {
            Text(
                text = "Loading describe details",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun DescribeErrorCard(
    errorMessage: String?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(28.dp),
            )
            Text(
                text = errorMessage
                    ?: "Failed to load describe details. Tap retry to try again.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onRetry, shapes = ButtonDefaults.shapes()) {
                    Icon(
                        imageVector = Icons.Outlined.ErrorOutline,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Retry")
                }
            }
        }
    }
}
