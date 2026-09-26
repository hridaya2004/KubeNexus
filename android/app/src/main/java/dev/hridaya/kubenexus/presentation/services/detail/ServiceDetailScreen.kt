package dev.hridaya.kubenexus.presentation.services.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hridaya.kubenexus.domain.model.K8sEventSummary
import dev.hridaya.kubenexus.domain.model.ServiceDetails
import dev.hridaya.kubenexus.domain.model.ServicePortDetail
import dev.hridaya.kubenexus.presentation.common.rememberNotificationPermissionRequest
import dev.hridaya.kubenexus.presentation.portforward.ActivePortForwardChipsRow
import dev.hridaya.kubenexus.presentation.portforward.PortForwardUiState
import dev.hridaya.kubenexus.presentation.services.detail.components.ServiceEventsCard
import dev.hridaya.kubenexus.presentation.services.detail.components.ServiceExternalIpsCard
import dev.hridaya.kubenexus.presentation.services.detail.components.ServiceMetadataCard
import dev.hridaya.kubenexus.presentation.services.detail.components.ServiceOverviewCard
import dev.hridaya.kubenexus.presentation.services.detail.components.ServicePortForwardDialog
import dev.hridaya.kubenexus.presentation.services.detail.components.ServicePortsCard
import dev.hridaya.kubenexus.presentation.services.detail.components.ServiceSelectorCard
import dev.hridaya.kubenexus.ui.theme.KubeNexusTheme

@Composable
fun ServiceDetailRoute(
    viewModel: ServiceDetailViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val servicePortForwardViewModel: ServicePortForwardViewModel = hiltViewModel(
        key = "port_forward_service_${uiState.namespace}_${uiState.serviceName}",
        creationCallback = { factory: ServicePortForwardViewModel.Factory ->
            factory.create(
                serviceName = uiState.serviceName,
                namespace = uiState.namespace,
            )
        },
    )
    val portForwardUiState by servicePortForwardViewModel.uiState.collectAsStateWithLifecycle()
    val requestNotificationPermission = rememberNotificationPermissionRequest()

    ServiceDetailScreen(
        uiState = uiState,
        onAction = viewModel::onAction,
        onNavigateBack = onNavigateBack,
        portForwardUiState = portForwardUiState,
        onPortForwardStart = { localPort, servicePort ->
            uiState.service?.let { service ->
                requestNotificationPermission()
                servicePortForwardViewModel.start(service, localPort, servicePort)
            }
        },
        onPortForwardStop = servicePortForwardViewModel::stop,
        onPortForwardDismissError = servicePortForwardViewModel::dismissError,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceDetailScreen(
    uiState: ServiceDetailUiState,
    onAction: (ServiceDetailUiAction) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    portForwardUiState: PortForwardUiState = PortForwardUiState(),
    onPortForwardStart: (localPort: Int, servicePort: Int) -> Unit = { _, _ -> },
    onPortForwardStop: (handleId: String) -> Unit = {},
    onPortForwardDismissError: () -> Unit = {},
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.serviceName,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { innerPadding ->
        val pullToRefreshState = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { onAction(ServiceDetailUiAction.Refresh) },
            state = pullToRefreshState,
            indicator = {
                if (!uiState.isRefreshing) {
                    PullToRefreshDefaults.Indicator(
                        state = pullToRefreshState,
                        isRefreshing = false,
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
            ) {
                if (uiState.isRefreshing) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                if (portForwardUiState.activeForwards.isNotEmpty()) {
                    ActivePortForwardChipsRow(
                        forwards = portForwardUiState.activeForwards,
                        onStopClick = onPortForwardStop,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }

                when {
                    uiState.isLoading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                strokeWidth = 3.dp,
                            )
                        }
                    }

                    uiState.errorMessage != null && uiState.service == null -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = uiState.errorMessage.orEmpty(),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                OutlinedButton(
                                    onClick = { onAction(ServiceDetailUiAction.Refresh) },
                                    shape = MaterialTheme.shapes.medium,
                                    modifier = Modifier
                                        .height(48.dp)
                                        .padding(horizontal = 16.dp),
                                ) {
                                    Text(text = "Retry", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }

                    else -> uiState.service?.let { service ->
                        ServiceDetailContent(
                            service = service,
                            lastRefreshedAt = uiState.lastRefreshedAt,
                            onPortForwardClick = {
                                onAction(
                                    ServiceDetailUiAction.ShowPortForwardDialog(
                                        true
                                    )
                                )
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }

    if (uiState.showPortForwardDialog && uiState.service != null) {
        ServicePortForwardDialog(
            service = uiState.service,
            uiState = portForwardUiState,
            onStart = onPortForwardStart,
            onStop = onPortForwardStop,
            onDismissError = onPortForwardDismissError,
            onDismiss = { onAction(ServiceDetailUiAction.ShowPortForwardDialog(false)) },
        )
    }
}

@Composable
private fun ServiceDetailContent(
    service: ServiceDetails,
    onPortForwardClick: () -> Unit,
    modifier: Modifier = Modifier,
    lastRefreshedAt: Long? = null,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ServiceOverviewCard(
            service = service,
            lastRefreshedAt = lastRefreshedAt,
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = onPortForwardClick,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
        ) {
            Icon(
                imageVector = Icons.Outlined.SwapHoriz,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Port Forward",
                style = MaterialTheme.typography.labelLarge,
            )
        }

        if (service.ports.isNotEmpty()) {
            ServicePortsCard(
                ports = service.ports,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        ServiceSelectorCard(
            selector = service.selector,
            modifier = Modifier.fillMaxWidth(),
        )

        if (service.externalIPs.isNotEmpty()) {
            ServiceExternalIpsCard(
                externalIPs = service.externalIPs,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        ServiceMetadataCard(
            labels = service.labels,
            annotations = service.annotations,
            modifier = Modifier.fillMaxWidth(),
        )

        if (service.events.isNotEmpty()) {
            ServiceEventsCard(
                events = service.events,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ServiceDetailScreenPreview() {
    KubeNexusTheme {
        ServiceDetailScreen(
            uiState = ServiceDetailUiState(
                serviceName = "web-frontend",
                namespace = "default",
                isLoading = false,
                service = ServiceDetails(
                    name = "web-frontend",
                    namespace = "default",
                    creationTimestampMillis = System.currentTimeMillis(),
                    type = "NodePort",
                    clusterIP = "10.96.0.42",
                    clusterIPs = listOf("10.96.0.42"),
                    externalIPs = listOf("203.0.113.7"),
                    selector = mapOf("app" to "web"),
                    ports = listOf(
                        ServicePortDetail(
                            port = 80,
                            targetPort = 8080,
                            nodePort = 31580,
                            protocol = "TCP",
                            name = "http",
                        ),
                    ),
                    labels = mapOf("app" to "web"),
                    annotations = emptyMap(),
                    events = listOf(
                        K8sEventSummary(
                            type = "Normal",
                            reason = "Created",
                            message = "Service created",
                            count = 1,
                            lastTimestampMillis = System.currentTimeMillis(),
                        ),
                    ),
                ),
            ),
            onAction = {},
            onNavigateBack = {},
        )
    }
}
