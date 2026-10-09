package dev.hridaya.kubenexus.presentation.pods.components.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.hridaya.kubenexus.core.terminal.TerminalSnapshot
import dev.hridaya.kubenexus.presentation.pods.detail.PodDetailUiState

@Composable
internal fun TerminalHeaderBar(
    uiState: PodDetailUiState,
    snapshot: TerminalSnapshot?,
    isImeVisible: Boolean,
    onToggleKeyboard: () -> Unit,
    onSelectAll: () -> Unit,
    onCopy: () -> Unit,
    onPaste: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Header bar
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .background(GhosttySurface)
            .padding(horizontal = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f, fill = false),
        ) {
            val dotColor = when {
                !uiState.isOnline -> GhosttyRed
                uiState.isTerminalActive -> GhosttyGreen
                else -> GhosttyGutter
            }
            val statusTitle = when {
                !uiState.isOnline -> "OFFLINE"
                uiState.isTerminalActive -> "ATTACHED"
                else -> "DETACHED"
            }
            val statusSubtitle = when {
                !uiState.isOnline -> "(disconnected)"
                uiState.isTerminalActive -> "(${uiState.activeShellCommand ?: "sh"})"
                else -> "(inactive)"
            }

            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(dotColor, CircleShape),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = statusTitle,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                ),
                color = if (uiState.isTerminalActive) GhosttyGreen else if (!uiState.isOnline) GhosttyRed else GhosttyGutter,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = statusSubtitle,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                ),
                color = GhosttyGutter,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Toggle Keyboard button
                IconButton(
                    onClick = onToggleKeyboard,
                    modifier = Modifier.size(24.dp),
                    shapes = IconButtonDefaults.shapes(),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Keyboard,
                        contentDescription = "Toggle Keyboard",
                        tint = if (isImeVisible) GhosttyGreen else GhosttyText,
                        modifier = Modifier.size(15.dp),
                    )
                }

                // Select All button
                IconButton(
                    onClick = onSelectAll,
                    modifier = Modifier.size(24.dp),
                    shapes = IconButtonDefaults.shapes(),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.SelectAll,
                        contentDescription = "Select All",
                        tint = GhosttyText,
                        modifier = Modifier.size(15.dp),
                    )
                }

                // Copy button
                IconButton(
                    onClick = onCopy,
                    modifier = Modifier.size(24.dp),
                    shapes = IconButtonDefaults.shapes(),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = "Copy",
                        tint = GhosttyText,
                        modifier = Modifier.size(15.dp),
                    )
                }

                // Paste button
                IconButton(
                    onClick = onPaste,
                    modifier = Modifier.size(24.dp),
                    shapes = IconButtonDefaults.shapes(),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentPaste,
                        contentDescription = "Paste",
                        tint = GhosttyText,
                        modifier = Modifier.size(15.dp),
                    )
                }

                // Clear button
                IconButton(
                    onClick = onClear,
                    modifier = Modifier.size(24.dp),
                    shapes = IconButtonDefaults.shapes(),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Clear,
                        contentDescription = "Clear",
                        tint = GhosttyText,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
        }
    }
}
