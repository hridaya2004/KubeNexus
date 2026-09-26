package dev.hridaya.kubenexus.presentation.main

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.hridaya.kubenexus.presentation.deployments.DeploymentsRoute
import dev.hridaya.kubenexus.presentation.deployments.DeploymentsViewModel
import dev.hridaya.kubenexus.presentation.deployments.detail.DeploymentDetailRoute
import dev.hridaya.kubenexus.presentation.deployments.detail.DeploymentDetailViewModel
import dev.hridaya.kubenexus.presentation.home.HomeUiEffect
import dev.hridaya.kubenexus.presentation.home.HomeViewModel
import dev.hridaya.kubenexus.presentation.home.ManageClustersScreen
import dev.hridaya.kubenexus.presentation.licenses.OpenSourceLicensesScreen
import dev.hridaya.kubenexus.presentation.logcat.LogcatRoute
import dev.hridaya.kubenexus.presentation.logcat.LogcatViewModel
import dev.hridaya.kubenexus.presentation.navigation.Destination
import dev.hridaya.kubenexus.presentation.navigation.ScreenViewModelScope
import dev.hridaya.kubenexus.presentation.navigation.ScreenViewModelStores
import dev.hridaya.kubenexus.presentation.pods.PodsScreen
import dev.hridaya.kubenexus.presentation.pods.detail.PodDetailRoute
import dev.hridaya.kubenexus.presentation.pods.detail.PodDetailViewModel
import dev.hridaya.kubenexus.presentation.portforward.sessions.PortForwardSessionsViewModel
import dev.hridaya.kubenexus.presentation.portforward.sessions.rememberPortForwardSessionsState
import dev.hridaya.kubenexus.presentation.services.ServicesRoute
import dev.hridaya.kubenexus.presentation.services.ServicesViewModel
import dev.hridaya.kubenexus.presentation.services.detail.ServiceDetailRoute
import dev.hridaya.kubenexus.presentation.services.detail.ServiceDetailViewModel

@Composable
fun MainScreen(
    homeViewModel: HomeViewModel,
    modifier: Modifier = Modifier,
) {
    var currentDestination by rememberSaveable { mutableStateOf(Destination.Home) }
    var isManagingClusters by rememberSaveable { mutableStateOf(false) }
    var isViewingPods by rememberSaveable { mutableStateOf(false) }
    var isViewingDeployments by rememberSaveable { mutableStateOf(false) }
    var isViewingServices by rememberSaveable { mutableStateOf(false) }
    var isViewingLogcat by rememberSaveable { mutableStateOf(false) }
    var isViewingOpenSourceLicenses by rememberSaveable { mutableStateOf(false) }
    var isCreatingDeployment by rememberSaveable { mutableStateOf(false) }
    var isCreatingPod by rememberSaveable { mutableStateOf(false) }
    var isCreatingService by rememberSaveable { mutableStateOf(false) }
    var selectedPodName by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedPodNamespace by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedDeploymentName by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedDeploymentNamespace by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedServiceName by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedServiceNamespace by rememberSaveable { mutableStateOf<String?>(null) }
    val portForwardSessionsViewModel: PortForwardSessionsViewModel = hiltViewModel()
    val portForwardSessionsState by rememberPortForwardSessionsState(portForwardSessionsViewModel)
    var showPortForwardSessions by rememberSaveable { mutableStateOf(false) }

    // Screens get their own ViewModel store, keyed by everything their ViewModels are bound
    // to. The active cluster is part of every key, so a screen opened on one cluster never
    // hands its state, or its actions, to another.
    val screenStores: ScreenViewModelStores = viewModel()
    // Only the cluster id, so MainScreen does not recompose on every Home state change.
    val activeClusterId by homeViewModel.activeClusterId.collectAsStateWithLifecycle()
    val clusterKey = activeClusterId.orEmpty()
    val podKey = selectedPodName?.let { "pod/$clusterKey/$selectedPodNamespace/$it" }
    val deploymentKey = selectedDeploymentName?.let { "deployment/$clusterKey/$selectedDeploymentNamespace/$it" }
    val serviceKey = selectedServiceName?.let { "service/$clusterKey/$selectedServiceNamespace/$it" }
    val createDeploymentKey = "create-deployment/$clusterKey".takeIf { isCreatingDeployment }
    val createPodKey = "create-pod/$clusterKey".takeIf { isCreatingPod }
    val createServiceKey = "create-service/$clusterKey".takeIf { isCreatingService }
    val deploymentsKey = "deployments/$clusterKey".takeIf { isViewingDeployments }
    val servicesKey = "services/$clusterKey".takeIf { isViewingServices }
    val logcatKey = "logcat".takeIf { isViewingLogcat }
    val liveScreenKeys = setOfNotNull(
        podKey, deploymentKey, serviceKey,
        createDeploymentKey, createPodKey, createServiceKey,
        deploymentsKey, servicesKey, logcatKey,
    )
    // Runs after the screen that was left has already gone from composition, so its
    // ViewModels are cleared (streams, sessions and native terminals released) only once
    // nothing on screen uses them.
    LaunchedEffect(liveScreenKeys) { screenStores.retainOnly(liveScreenKeys) }

    val context = LocalContext.current
    // The only collector of Home's effects. They come from a Channel, which hands each event to
    // exactly one collector, so a second collector elsewhere silently took half of them.
    LaunchedEffect(homeViewModel.effects) {
        homeViewModel.effects.collect { effect ->
            if (effect is HomeUiEffect.ShowToast) {
                Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
            }
            if (effect is HomeUiEffect.NavigateToHome) {
                isManagingClusters = false
                isViewingPods = false
                isViewingDeployments = false
                isViewingServices = false
                isViewingLogcat = false
                isViewingOpenSourceLicenses = false
                isCreatingDeployment = false
                isCreatingPod = false
                isCreatingService = false
                selectedPodName = null
                selectedPodNamespace = null
                selectedDeploymentName = null
                selectedDeploymentNamespace = null
                selectedServiceName = null
                selectedServiceNamespace = null
                showPortForwardSessions = false
                currentDestination = Destination.Home
            }
        }
    }

    when {
        selectedPodName != null && selectedPodNamespace != null -> {
            val podName = selectedPodName!!
            val podNamespace = selectedPodNamespace!!
            ScreenViewModelScope(key = podKey!!, stores = screenStores) {
                val podDetailViewModel: PodDetailViewModel = hiltViewModel(
                    creationCallback = { factory: PodDetailViewModel.Factory ->
                        factory.create(
                            podName = podName,
                            namespace = podNamespace,
                        )
                    },
                )
                BackHandler {
                    selectedPodName = null
                    selectedPodNamespace = null
                }
                PodDetailRoute(
                    viewModel = podDetailViewModel,
                    onNavigateBack = {
                        selectedPodName = null
                        selectedPodNamespace = null
                    },
                    modifier = modifier,
                )
            }
        }

        selectedDeploymentName != null && selectedDeploymentNamespace != null -> {
            val deploymentName = selectedDeploymentName!!
            val deploymentNamespace = selectedDeploymentNamespace!!
            ScreenViewModelScope(key = deploymentKey!!, stores = screenStores) {
                val deploymentDetailViewModel: DeploymentDetailViewModel = hiltViewModel(
                    creationCallback = { factory: DeploymentDetailViewModel.Factory ->
                        factory.create(
                            deploymentName = deploymentName,
                            namespace = deploymentNamespace,
                        )
                    },
                )
                BackHandler {
                    selectedDeploymentName = null
                    selectedDeploymentNamespace = null
                }
                DeploymentDetailRoute(
                    viewModel = deploymentDetailViewModel,
                    onNavigateBack = {
                        selectedDeploymentName = null
                        selectedDeploymentNamespace = null
                    },
                    onNavigateToPodDetail = { podName, podNamespace ->
                        selectedPodName = podName
                        selectedPodNamespace = podNamespace
                    },
                    modifier = modifier,
                )
            }
        }

        selectedServiceName != null && selectedServiceNamespace != null -> {
            val serviceName = selectedServiceName!!
            val serviceNamespace = selectedServiceNamespace!!
            ScreenViewModelScope(key = serviceKey!!, stores = screenStores) {
                val serviceDetailViewModel: ServiceDetailViewModel = hiltViewModel(
                    creationCallback = { factory: ServiceDetailViewModel.Factory ->
                        factory.create(
                            serviceName = serviceName,
                            namespace = serviceNamespace,
                        )
                    },
                )
                BackHandler {
                    selectedServiceName = null
                    selectedServiceNamespace = null
                }
                ServiceDetailRoute(
                    viewModel = serviceDetailViewModel,
                    onNavigateBack = {
                        selectedServiceName = null
                        selectedServiceNamespace = null
                    },
                    modifier = modifier,
                )
            }
        }

        isCreatingDeployment -> ScreenViewModelScope(key = createDeploymentKey!!, stores = screenStores) {
            CreateDeploymentOverlay(
                homeViewModel = homeViewModel,
                onDismiss = { isCreatingDeployment = false },
                modifier = modifier,
            )
        }

        isCreatingPod -> ScreenViewModelScope(key = createPodKey!!, stores = screenStores) {
            CreatePodOverlay(
                homeViewModel = homeViewModel,
                onDismiss = { isCreatingPod = false },
                modifier = modifier,
            )
        }

        isCreatingService -> ScreenViewModelScope(key = createServiceKey!!, stores = screenStores) {
            CreateServiceOverlay(
                homeViewModel = homeViewModel,
                onDismiss = { isCreatingService = false },
                modifier = modifier,
            )
        }

        isManagingClusters -> {
            val uiState by homeViewModel.uiState.collectAsStateWithLifecycle()
            BackHandler { isManagingClusters = false }
            ManageClustersScreen(
                uiState = uiState,
                onAction = homeViewModel::onAction,
                onNavigateBack = { isManagingClusters = false },
                modifier = modifier,
            )
        }

        isViewingPods -> {
            val uiState by homeViewModel.uiState.collectAsStateWithLifecycle()
            BackHandler { isViewingPods = false }
            PodsScreen(
                uiState = uiState,
                onAction = homeViewModel::onAction,
                onNavigateBack = { isViewingPods = false },
                onNavigateToPodDetail = { pod ->
                    selectedPodName = pod.name
                    selectedPodNamespace = pod.namespace
                },
                onNavigateToCreatePod = { isCreatingPod = true },
                modifier = modifier,
            )
        }

        isViewingDeployments -> ScreenViewModelScope(key = deploymentsKey!!, stores = screenStores) {
            val homeUiState by homeViewModel.uiState.collectAsStateWithLifecycle()
            val clusterId = homeUiState.activeCluster?.id
            val deploymentsViewModel: DeploymentsViewModel = hiltViewModel(
                creationCallback = { factory: DeploymentsViewModel.Factory ->
                    factory.create(
                        clusterId = clusterId,
                        namespace = homeUiState.selectedNamespace
                            .takeIf { it.isNotBlank() && it != "All Namespaces" },
                    )
                },
            )
            BackHandler { isViewingDeployments = false }
            DeploymentsRoute(
                viewModel = deploymentsViewModel,
                onNavigateBack = { isViewingDeployments = false },
                onDeploymentClick = { deployment ->
                    selectedDeploymentName = deployment.name
                    selectedDeploymentNamespace = deployment.namespace
                },
                onNavigateToCreateDeployment = { isCreatingDeployment = true },
                modifier = modifier,
            )
        }

        isViewingServices -> ScreenViewModelScope(key = servicesKey!!, stores = screenStores) {
            val homeUiState by homeViewModel.uiState.collectAsStateWithLifecycle()
            val clusterId = homeUiState.activeCluster?.id
            val servicesViewModel: ServicesViewModel = hiltViewModel(
                creationCallback = { factory: ServicesViewModel.Factory ->
                    factory.create(
                        clusterId = clusterId,
                        namespace = homeUiState.selectedNamespace
                            .takeIf { it.isNotBlank() && it != "All Namespaces" },
                    )
                },
            )
            BackHandler { isViewingServices = false }
            ServicesRoute(
                viewModel = servicesViewModel,
                onNavigateBack = { isViewingServices = false },
                onServiceClick = { service ->
                    selectedServiceName = service.name
                    selectedServiceNamespace = service.namespace
                },
                onNavigateToCreateService = { isCreatingService = true },
                modifier = modifier,
            )
        }

        // Scoped so the logcat process it streams from stops when the screen is left.
        isViewingLogcat -> ScreenViewModelScope(key = logcatKey!!, stores = screenStores) {
            val logcatViewModel: LogcatViewModel = hiltViewModel()
            BackHandler { isViewingLogcat = false }
            LogcatRoute(
                viewModel = logcatViewModel,
                onNavigateBack = { isViewingLogcat = false },
                modifier = modifier,
            )
        }

        isViewingOpenSourceLicenses -> {
            BackHandler { isViewingOpenSourceLicenses = false }
            OpenSourceLicensesScreen(
                onNavigateBack = { isViewingOpenSourceLicenses = false },
                modifier = modifier,
            )
        }

        else -> {
            MainTopLevelScaffold(
                homeViewModel = homeViewModel,
                currentDestination = currentDestination,
                onSelectDestination = { currentDestination = it },
                onNavigateToManageClusters = { isManagingClusters = true },
                onNavigateToPods = { isViewingPods = true },
                onNavigateToDeployments = { isViewingDeployments = true },
                onNavigateToServices = { isViewingServices = true },
                onNavigateToCreatePod = { isCreatingPod = true },
                onNavigateToCreateDeployment = { isCreatingDeployment = true },
                onNavigateToCreateService = { isCreatingService = true },
                onNavigateToLogcat = { isViewingLogcat = true },
                onNavigateToOpenSourceLicenses = { isViewingOpenSourceLicenses = true },
                activeForwardCount = portForwardSessionsState.activeCount,
                onOpenPortForwardSessions = { showPortForwardSessions = true },
                modifier = modifier,
            )
        }
    }

    if (showPortForwardSessions) {
        MainPortForwardSessionsEntry(
            onDismiss = { showPortForwardSessions = false },
        )
    }
}

