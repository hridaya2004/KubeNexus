package dev.hridaya.kubenexus.presentation.pods.components

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.hridaya.kubenexus.core.common.result.Result
import dev.hridaya.kubenexus.core.common.util.LogExportHelper
import dev.hridaya.kubenexus.ui.theme.KubeNexusTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun GhosttyTerminalLogViewer(
    logs: List<String>,
    isStreaming: Boolean,
    onClearLogs: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
) {
    var searchQuery by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    var wrapLines by remember { mutableStateOf(true) }
    var autoScrollEnabled by remember { mutableStateOf(true) }
    var confirmUpload by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val filteredLogs = remember(logs, searchQuery) {
        if (searchQuery.isBlank()) {
            logs
        } else {
            logs.filter { it.contains(searchQuery, ignoreCase = true) }
        }
    }

    // Content-aware bottom detection
    val isAtBottom by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty() || layoutInfo.totalItemsCount == 0) {
                true
            } else {
                val lastVisibleItem = visibleItems.last()
                lastVisibleItem.index >= layoutInfo.totalItemsCount - 1
            }
        }
    }

    // Decided when a scroll ends: scrolled away from the bottom pauses following, scrolled
    // back to it resumes. Checking when a scroll starts pulled the user back on the next line.
    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) {
            autoScrollEnabled = isAtBottom
        }
    }

    // Re-enable auto-scroll and jump to latest when streaming starts or logs are freshly fetched
    var previousLogsRef by remember { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(logs, isStreaming) {
        if (previousLogsRef !== logs || isStreaming) {
            previousLogsRef = logs
            autoScrollEnabled = true
            if (filteredLogs.isNotEmpty()) {
                listState.scrollToItem(filteredLogs.size - 1)
            }
        }
    }

    // Follow the tail when new logs arrive while auto-scroll is enabled. The last line is part
    // of the key because the count stops changing once the log view reaches its line cap.
    LaunchedEffect(filteredLogs.size, filteredLogs.lastOrNull(), autoScrollEnabled) {
        if (autoScrollEnabled && filteredLogs.isNotEmpty()) {
            listState.scrollToItem(filteredLogs.size - 1)
        }
    }

    if (confirmUpload) {
        val provider = LogExportHelper.pasteProvider
        LogUploadConfirmDialog(
            lineCount = logs.size,
            serviceName = provider.name,
            retention = provider.retention,
            onDismiss = { confirmUpload = false },
            onConfirm = {
                confirmUpload = false
                val text = logs.joinToString("\n")
                Toast.makeText(context, "Uploading logs to ${provider.name}...", Toast.LENGTH_SHORT).show()
                scope.launch {
                    when (val result = LogExportHelper.uploadToPastebin(text, provider)) {
                        is Result.Success -> {
                            val pasteUrl = result.data
                            LogExportHelper.copyToClipboard(context, pasteUrl, "Pastebin URL", sensitive = false)
                            Toast.makeText(
                                context,
                                "Logs uploaded! URL copied to clipboard: $pasteUrl",
                                Toast.LENGTH_LONG,
                            ).show()
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, title ?: "Pod logs")
                                putExtra(Intent.EXTRA_TEXT, pasteUrl)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Log Link"))
                        }

                        is Result.Error -> {
                            Toast.makeText(
                                context,
                                "Export failed: ${result.error.message}",
                                Toast.LENGTH_LONG,
                            ).show()
                        }

                        Result.Loading -> Unit
                    }
                }
            },
        )
    }

    Surface(
        color = TerminalBg,
        shape = MaterialTheme.shapes.small,
        modifier = modifier
            .fillMaxSize()
            .border(1.dp, TerminalBorder, MaterialTheme.shapes.small)
            .clip(MaterialTheme.shapes.small),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            LogViewerHeaderBar(
                isStreaming = isStreaming,
                visibleCount = filteredLogs.size,
                showSearch = showSearch,
                wrapLines = wrapLines,
                onToggleSearch = { showSearch = !showSearch },
                onToggleWrap = { wrapLines = !wrapLines },
                onCopyAll = {
                    val textToCopy = logs.joinToString("\n")
                    LogExportHelper.copyToClipboard(context, textToCopy, "Pod Logs")
                    Toast.makeText(
                        context,
                        "Copied ${logs.size} log lines to clipboard",
                        Toast.LENGTH_SHORT,
                    ).show()
                },
                onExportFile = {
                    val snapshot = logs
                    scope.launch {
                        val text = withContext(Dispatchers.Default) { snapshot.joinToString("\n") }
                        LogExportHelper.shareAsFile(context, text, "${title ?: "pod"}.log")
                    }
                },
                onExportPastebin = {
                    if (logs.isEmpty()) {
                        Toast.makeText(context, "No logs to export", Toast.LENGTH_SHORT).show()
                    } else {
                        confirmUpload = true
                    }
                },
                onClearLogs = onClearLogs,
            )

            if (showSearch) {
                LogSearchField(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                if (filteredLogs.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (isStreaming) "Waiting for container logs" else "No log output available",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                            ),
                            color = TerminalGutter,
                        )
                    }
                } else {
                    LogLinesList(
                        lines = filteredLogs,
                        wrapLines = wrapLines,
                        highlightQuery = searchQuery,
                        listState = listState,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                // Down arrow button to jump straight to latest log line
                ScrollToBottomFab(
                    visible = (!isAtBottom || !autoScrollEnabled) && filteredLogs.isNotEmpty(),
                    onClick = {
                        autoScrollEnabled = true
                        scope.launch {
                            listState.animateScrollToItem(filteredLogs.size - 1)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun BoxScope.ScrollToBottomFab(
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut(),
        modifier = modifier
            .align(Alignment.BottomEnd)
            .padding(12.dp),
    ) {
        SmallFloatingActionButton(
            onClick = onClick,
            containerColor = TerminalFabBg,
            contentColor = TerminalText,
            shape = CircleShape,
        ) {
            Icon(
                imageVector = Icons.Outlined.ArrowDownward,
                contentDescription = "Go to latest log",
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun GhosttyTerminalLogViewerPreview() {
    KubeNexusTheme {
        GhosttyTerminalLogViewer(
            logs = listOf(
                "2026-08-19T10:00:00.123456Z [info] Starting server on port 8080",
                "2026-08-19T10:00:01.234567Z [debug] Loaded configuration module",
                "2026-08-19T10:00:02.345678Z [info] Ready to accept incoming connections",
            ),
            isStreaming = true,
            onClearLogs = {},
            title = "nginx-app",
        )
    }
}
