package dev.hridaya.kubenexus.presentation.pods.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.WrapText
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun LogViewerHeaderBar(
    isStreaming: Boolean,
    visibleCount: Int,
    showSearch: Boolean,
    wrapLines: Boolean,
    onToggleSearch: () -> Unit,
    onToggleWrap: () -> Unit,
    onCopyAll: () -> Unit,
    onExportFile: () -> Unit,
    onExportPastebin: () -> Unit,
    onClearLogs: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showExportMenu by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .fillMaxWidth()
            .background(TerminalHeaderBg)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        if (isStreaming) TerminalGreen else TerminalYellow,
                        shape = CircleShape,
                    ),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isStreaming) "LIVE LOGS" else "LOGS",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                ),
                color = TerminalText,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "($visibleCount)",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                ),
                color = TerminalGutter,
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconToggleButton(
                checked = showSearch,
                onCheckedChange = { onToggleSearch() },
                shapes = IconButtonDefaults.toggleableShapes(),
                modifier = Modifier.size(28.dp),
                colors = IconButtonDefaults.iconToggleButtonColors(
                    contentColor = TerminalText,
                    checkedContentColor = TerminalCyan,
                ),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = "Search",
                    modifier = Modifier.size(16.dp),
                )
            }

            Spacer(modifier = Modifier.width(2.dp))

            IconToggleButton(
                checked = wrapLines,
                onCheckedChange = { onToggleWrap() },
                shapes = IconButtonDefaults.toggleableShapes(),
                modifier = Modifier.size(28.dp),
                colors = IconButtonDefaults.iconToggleButtonColors(
                    contentColor = TerminalGutter,
                    checkedContentColor = TerminalGreen,
                ),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.WrapText,
                    contentDescription = "Wrap lines",
                    modifier = Modifier.size(16.dp),
                )
            }

            Spacer(modifier = Modifier.width(2.dp))

            IconButton(
                onClick = onCopyAll,
                modifier = Modifier.size(28.dp),
                shapes = IconButtonDefaults.shapes(),
            ) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = "Copy all",
                    tint = TerminalText,
                    modifier = Modifier.size(16.dp),
                )
            }

            Spacer(modifier = Modifier.width(2.dp))

            Box {
                IconButton(
                    onClick = { showExportMenu = true },
                    modifier = Modifier.size(28.dp),
                    shapes = IconButtonDefaults.shapes(),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.FileUpload,
                        contentDescription = "Export logs",
                        tint = TerminalText,
                        modifier = Modifier.size(16.dp),
                    )
                }

                DropdownMenu(
                    expanded = showExportMenu,
                    onDismissRequest = { showExportMenu = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Export as file") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.FileDownload,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        onClick = {
                            showExportMenu = false
                            onExportFile()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Upload to dpaste.org…") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.CloudUpload,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        onClick = {
                            showExportMenu = false
                            onExportPastebin()
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.width(2.dp))

            IconButton(
                onClick = onClearLogs,
                modifier = Modifier.size(28.dp),
                shapes = IconButtonDefaults.shapes(),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Clear,
                    contentDescription = "Clear",
                    tint = TerminalText,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
