package dev.hridaya.kubenexus.presentation.deployments.detail.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.hridaya.kubenexus.presentation.common.components.MetadataKeyValueRow

private const val MAX_VISIBLE_ENTRIES = 4

@Composable
internal fun DeploymentLabelsCard(
    labels: Map<String, String>,
    modifier: Modifier = Modifier,
) {
    DeploymentKeyValueCard(title = "Labels", entries = labels.toList(), modifier = modifier)
}

@Composable
internal fun DeploymentAnnotationsCard(
    annotations: Map<String, String>,
    modifier: Modifier = Modifier,
) {
    DeploymentKeyValueCard(
        title = "Annotations",
        entries = annotations.toList(),
        modifier = modifier
    )
}

/**
 * Key/value card shared by labels and annotations. Long maps collapse to the
 * first [MAX_VISIBLE_ENTRIES] pairs with a show-all toggle so one annotation
 * blob cannot push the rest of the describe sections off screen.
 */
@Composable
private fun DeploymentKeyValueCard(
    title: String,
    entries: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
) {
    var showAllEntries by rememberSaveable { mutableStateOf(false) }
    val visibleEntries =
        if (showAllEntries || entries.size <= MAX_VISIBLE_ENTRIES) entries else entries.take(
            MAX_VISIBLE_ENTRIES
        )

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            visibleEntries.forEach { (entryKey, entryValue) ->
                MetadataKeyValueRow(key = entryKey, value = entryValue)
            }
            if (entries.size > MAX_VISIBLE_ENTRIES) {
                TextButton(
                    onClick = { showAllEntries = !showAllEntries },
                    shapes = ButtonDefaults.shapes()
                ) {
                    Text(
                        text = if (showAllEntries) "Show less" else "Show all ${entries.size}",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}
