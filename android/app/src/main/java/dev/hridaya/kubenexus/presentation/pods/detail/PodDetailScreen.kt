package dev.hridaya.kubenexus.presentation.pods.detail

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hridaya.kubenexus.domain.model.ContainerDetail
import dev.hridaya.kubenexus.domain.model.PodConditionDetail
import dev.hridaya.kubenexus.domain.model.PodDetails
import dev.hridaya.kubenexus.domain.model.PodStatus
import dev.hridaya.kubenexus.presentation.common.components.RefreshableBox
import dev.hridaya.kubenexus.presentation.common.share.LogExportHelper
import dev.hridaya.kubenexus.presentation.pods.components.terminal.GhosttyTerminalEngine
import dev.hridaya.kubenexus.presentation.pods.components.terminal.GhosttyTerminalView
import dev.hridaya.kubenexus.presentation.pods.detail.components.DeletePodConfirmDialog
import dev.hridaya.kubenexus.presentation.pods.detail.components.DescribeTabContent
import dev.hridaya.kubenexus.presentation.pods.detail.components.LogsTabContent
import dev.hridaya.kubenexus.presentation.pods.detail.components.PodDetailTopBar
import dev.hridaya.kubenexus.ui.theme.KubeNexusTheme

@Composable
fun PodDetailRoute(
    viewModel: PodDetailViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(viewModel.effects) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is PodDetailUiEffect.ShowToast -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }

                is PodDetailUiEffect.NavigateBack -> onNavigateBack()

                is PodDetailUiEffect.LogsUploaded -> {
                    LogExportHelper.copyToClipboard(
                        context,
                        effect.url,
                        "Pastebin URL",
                        sensitive = false
                    )
                    Toast.makeText(
                        context,
                        "Logs uploaded! URL copied to clipboard: ${effect.url}",
                        Toast.LENGTH_LONG,
                    ).show()
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, uiState.podName)
                        putExtra(Intent.EXTRA_TEXT, effect.url)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share Log Link"))
                }
            }
        }
    }

    // Tied to visibility, because the ViewModel outlives the UI.
    LifecycleStartEffect(viewModel) {
        viewModel.startMetricsPolling()
        viewModel.resumeLogStream()
        onStopOrDispose {
            viewModel.stopMetricsPolling()
            viewModel.pauseLogStream()
        }
    }

    PodDetailScreen(
        uiState = uiState,
        engine = viewModel.terminalEngine,
        onAction = viewModel::onAction,
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodDetailScreen(
    uiState: PodDetailUiState,
    onAction: (PodDetailUiAction) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    engine: GhosttyTerminalEngine? = null,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            PodDetailTopBar(
                podName = uiState.podName,
                namespace = uiState.namespace,
                onNavigateBack = onNavigateBack,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        ) {
            PrimaryTabRow(
                selectedTabIndex = uiState.selectedTab.ordinal,
                containerColor = Color.Transparent,
                divider = { HorizontalDivider() },
            ) {
                PodDetailTab.entries.forEach { tab ->
                    Tab(
                        selected = uiState.selectedTab == tab,
                        onClick = { onAction(PodDetailUiAction.SelectTab(tab)) },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val icon = when (tab) {
                                    PodDetailTab.DESCRIBE -> Icons.Outlined.Info
                                    PodDetailTab.LOGS -> Icons.Outlined.Description
                                    PodDetailTab.TERMINAL -> Icons.Outlined.Terminal
                                }
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = tab.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = if (uiState.selectedTab == tab) FontWeight.SemiBold else FontWeight.Normal,
                                )
                            }
                        },
                    )
                }
            }

            when (uiState.selectedTab) {
                PodDetailTab.DESCRIBE -> {
                    RefreshableBox(
                        isRefreshing = uiState.isRefreshing,
                        onRefresh = { onAction(PodDetailUiAction.RefreshDescribe) },
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        DescribeTabContent(
                            uiState = uiState,
                            onAction = onAction,
                            onNavigateToLogs = { containerName ->
                                onAction(PodDetailUiAction.SelectContainer(containerName))
                                onAction(PodDetailUiAction.SelectTab(PodDetailTab.LOGS))
                            },
                            onNavigateToTerminal = { containerName ->
                                onAction(PodDetailUiAction.SelectContainer(containerName))
                                onAction(PodDetailUiAction.SelectTab(PodDetailTab.TERMINAL))
                            },
                        )
                    }
                }

                PodDetailTab.LOGS -> {
                    LogsTabContent(
                        uiState = uiState,
                        onAction = onAction,
                    )
                }

                PodDetailTab.TERMINAL -> {
                    if (engine != null) {
                        GhosttyTerminalView(
                            uiState = uiState,
                            engine = engine,
                            onAction = onAction,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }

    if (uiState.showDeleteConfirmDialog) {
        DeletePodConfirmDialog(
            podName = uiState.podName,
            namespace = uiState.namespace,
            isDeletingPod = uiState.isDeletingPod,
            onConfirm = { onAction(PodDetailUiAction.ConfirmDeletePod) },
            onDismiss = { onAction(PodDetailUiAction.ShowDeleteDialog(false)) },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PodDetailScreenPreview() {
    KubeNexusTheme {
        PodDetailScreen(
            uiState = PodDetailUiState(
                podName = "nginx-deployment-78f56c879d-gqw87",
                namespace = "default",
                podDetails = PodDetails(
                    name = "nginx-deployment-78f56c879d-gqw87",
                    namespace = "default",
                    status = PodStatus.RUNNING,
                    node = "worker-node-1",
                    ip = "10.244.1.42",
                    hostIp = "192.168.1.15",
                    startTime = "2026-08-18T14:32:00Z",
                    labels = mapOf("app" to "nginx", "env" to "production"),
                    annotations = mapOf("deployment.kubernetes.io/revision" to "1"),
                    containers = listOf(
                        ContainerDetail(
                            name = "nginx",
                            image = "nginx:1.25-alpine",
                            ready = true,
                            restartCount = 0,
                            state = "Running",
                        )
                    ),
                    conditions = listOf(
                        PodConditionDetail(type = "Ready", status = "True"),
                        PodConditionDetail(type = "ContainersReady", status = "True"),
                    ),
                    events = emptyList(),
                ),
                isLoading = false,
            ),
            onAction = {},
            onNavigateBack = {},
        )
    }
}
