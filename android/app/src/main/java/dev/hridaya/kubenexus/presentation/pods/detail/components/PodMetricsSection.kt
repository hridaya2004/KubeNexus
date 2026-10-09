package dev.hridaya.kubenexus.presentation.pods.detail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.hridaya.kubenexus.domain.model.PodMetricSample
import dev.hridaya.kubenexus.presentation.pods.detail.MetricsRange

internal val CPU_COLOR = Color(0xFF6C9EFF)
internal val MEMORY_COLOR = Color(0xFF63D8A6)

@Composable
fun PodMetricsSection(
    samples: List<PodMetricSample>,
    selectedRange: MetricsRange,
    isLoading: Boolean,
    onSelectRange: (MetricsRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Speed,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Resource Usage",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.weight(1f))
                IntervalDropdown(selectedRange, onSelectRange)
            }

            Spacer(Modifier.height(10.dp))

            val windowed = remember(samples, selectedRange) { selectedRange.window(samples) }
            val latest = windowed.lastOrNull()

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LegendDot(CPU_COLOR, "CPU", latest?.cpuCores?.let(::formatCores) ?: "—")
                LegendDot(MEMORY_COLOR, "Mem", latest?.memoryBytes?.let(::formatBytes) ?: "—")
            }

            Spacer(Modifier.height(8.dp))

            if (windowed.size < 2) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (isLoading || windowed.isEmpty()) {
                            "Collecting usage samples…"
                        } else {
                            "Not enough samples yet"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                UsageChart(windowed, selectedRange)
            }
        }
    }
}

@Composable
private fun IntervalDropdown(
    selected: MetricsRange,
    onSelect: (MetricsRange) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(
            onClick = { expanded = true },
            contentPadding = PaddingValues(
                horizontal = 10.dp,
                vertical = 4.dp,
            ),
            shapes = ButtonDefaults.shapes(),
        ) {
            Text(
                text = selected.label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Icon(
                imageVector = Icons.Outlined.ArrowDropDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            MetricsRange.entries.forEach { range ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = range.label,
                            color = if (range == selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelect(range)
                    },
                )
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape),
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = "$label $value",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
