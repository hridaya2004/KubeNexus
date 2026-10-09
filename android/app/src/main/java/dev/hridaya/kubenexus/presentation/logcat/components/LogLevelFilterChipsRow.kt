package dev.hridaya.kubenexus.presentation.logcat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.hridaya.kubenexus.domain.model.LogLevel
import dev.hridaya.kubenexus.ui.theme.logColors

@Composable
fun LogLevelFilterChipsRow(
    selectedLogLevel: LogLevel?,
    logCount: Int,
    levelCounts: Map<LogLevel, Int>,
    onSelectLogLevel: (LogLevel?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = selectedLogLevel == null,
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onSelectLogLevel(null)
            },
            label = {
                Text(
                    "All ($logCount)",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = if (selectedLogLevel == null) FontWeight.SemiBold else FontWeight.Normal,
                    ),
                )
            },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                labelColor = MaterialTheme.colorScheme.onSurface,
            ),
        )

        val logLevels = listOf(
            LogLevel.VERBOSE,
            LogLevel.DEBUG,
            LogLevel.INFO,
            LogLevel.WARN,
            LogLevel.ERROR,
            LogLevel.FATAL,
        )

        logLevels.forEach { level ->
            val levelCount = levelCounts[level] ?: 0
            FilterChip(
                selected = selectedLogLevel == level,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSelectLogLevel(if (selectedLogLevel == level) null else level)
                },
                label = {
                    Text(
                        "${level.code} ($levelCount)",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = if (selectedLogLevel == level) FontWeight.SemiBold else FontWeight.Normal,
                        ),
                    )
                },
                leadingIcon = {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(logColors.forLevel(level), CircleShape),
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    labelColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        }
    }
}
