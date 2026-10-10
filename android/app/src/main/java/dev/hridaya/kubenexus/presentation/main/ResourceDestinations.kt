package dev.hridaya.kubenexus.presentation.main

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import dev.hridaya.kubenexus.domain.model.resource.ResourceKinds
import dev.hridaya.kubenexus.presentation.deployments.detail.DeploymentDetailRoute
import dev.hridaya.kubenexus.presentation.deployments.detail.DeploymentDetailViewModel
import dev.hridaya.kubenexus.presentation.home.HomeViewModel
import dev.hridaya.kubenexus.presentation.navigation.Screen
import dev.hridaya.kubenexus.presentation.pods.detail.PodDetailRoute
import dev.hridaya.kubenexus.presentation.pods.detail.PodDetailViewModel
import dev.hridaya.kubenexus.presentation.resources.ResourceCatalog
import dev.hridaya.kubenexus.presentation.resources.list.ResourceListRoute
import dev.hridaya.kubenexus.presentation.resources.list.ResourceListViewModel
import dev.hridaya.kubenexus.presentation.services.detail.ServiceDetailRoute
import dev.hridaya.kubenexus.presentation.services.detail.ServiceDetailViewModel

/*
 * Maps resource screens to the composables that show them.
 *
 * Every kind is listed by the shared list screen. Kinds with purpose-built
 * detail or create screens (pod logs and exec, deployment scaling, service
 * port-forwarding, guided forms) are routed to them here, and only here.
 */

private val PODS = ResourceKinds.Pods.type.key
private val DEPLOYMENTS = ResourceKinds.Deployments.type.key
private val SERVICES = ResourceKinds.Services.type.key

@Composable
internal fun ResourceListDestination(
    screen: Screen.ResourceList,
    clusterId: String?,
    initialNamespace: String?,
    onNavigateBack: () -> Unit,
    onOpen: (Screen) -> Unit,
    modifier: Modifier = Modifier,
) {
    val presentation = ResourceCatalog.of(screen.typeKey)
    val viewModel: ResourceListViewModel = hiltViewModel(
        creationCallback = { factory: ResourceListViewModel.Factory ->
            factory.create(presentation, clusterId, initialNamespace)
        },
    )
    ResourceListRoute(
        presentation = presentation,
        viewModel = viewModel,
        onNavigateBack = onNavigateBack,
        onOpenResource = { row -> onOpen(Screen.ResourceDetail(screen.typeKey, row.namespace, row.name)) },
        onCreateResource = { onOpen(Screen.CreateResource(screen.typeKey)) },
        modifier = modifier,
    )
}

@Composable
internal fun ResourceDetailDestination(
    screen: Screen.ResourceDetail,
    onNavigateBack: () -> Unit,
    onOpen: (Screen) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (screen.typeKey) {
        PODS -> {
            val viewModel: PodDetailViewModel = hiltViewModel(
                creationCallback = { factory: PodDetailViewModel.Factory ->
                    factory.create(podName = screen.name, namespace = screen.namespace)
                },
            )
            PodDetailRoute(viewModel = viewModel, onNavigateBack = onNavigateBack, modifier = modifier)
        }

        DEPLOYMENTS -> {
            val viewModel: DeploymentDetailViewModel = hiltViewModel(
                creationCallback = { factory: DeploymentDetailViewModel.Factory ->
                    factory.create(deploymentName = screen.name, namespace = screen.namespace)
                },
            )
            DeploymentDetailRoute(
                viewModel = viewModel,
                onNavigateBack = onNavigateBack,
                onNavigateToPodDetail = { podName, podNamespace ->
                    onOpen(Screen.ResourceDetail(PODS, podNamespace, podName))
                },
                modifier = modifier,
            )
        }

        SERVICES -> {
            val viewModel: ServiceDetailViewModel = hiltViewModel(
                creationCallback = { factory: ServiceDetailViewModel.Factory ->
                    factory.create(serviceName = screen.name, namespace = screen.namespace)
                },
            )
            ServiceDetailRoute(viewModel = viewModel, onNavigateBack = onNavigateBack, modifier = modifier)
        }

        else -> error("No detail screen is registered for '${screen.typeKey}'")
    }
}

@Composable
internal fun CreateResourceDestination(
    screen: Screen.CreateResource,
    homeViewModel: HomeViewModel,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (screen.typeKey) {
        PODS -> CreatePodOverlay(homeViewModel = homeViewModel, onDismiss = onDismiss, modifier = modifier)
        DEPLOYMENTS -> CreateDeploymentOverlay(homeViewModel = homeViewModel, onDismiss = onDismiss, modifier = modifier)
        SERVICES -> CreateServiceOverlay(homeViewModel = homeViewModel, onDismiss = onDismiss, modifier = modifier)
        else -> error("No create screen is registered for '${screen.typeKey}'")
    }
}
