package dev.hridaya.kubenexus.presentation.main

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.hridaya.kubenexus.presentation.common.namespaceOrNull
import dev.hridaya.kubenexus.presentation.home.HomeUiEffect
import dev.hridaya.kubenexus.presentation.home.HomeViewModel
import dev.hridaya.kubenexus.presentation.home.ManageClustersScreen
import dev.hridaya.kubenexus.presentation.licenses.OpenSourceLicensesScreen
import dev.hridaya.kubenexus.presentation.logcat.LogcatRoute
import dev.hridaya.kubenexus.presentation.logcat.LogcatViewModel
import dev.hridaya.kubenexus.presentation.navigation.Destination
import dev.hridaya.kubenexus.presentation.navigation.Screen
import dev.hridaya.kubenexus.presentation.navigation.ScreenViewModelScope
import dev.hridaya.kubenexus.presentation.navigation.ScreenViewModelStores
import dev.hridaya.kubenexus.presentation.navigation.rememberBackStack
import dev.hridaya.kubenexus.presentation.portforward.sessions.PortForwardSessionsViewModel
import dev.hridaya.kubenexus.presentation.portforward.sessions.rememberPortForwardSessionsState

@Composable
fun MainScreen(
    homeViewModel: HomeViewModel,
    modifier: Modifier = Modifier,
) {
    var currentDestination by rememberSaveable { mutableStateOf(Destination.Favourites) }
    val backStack = rememberBackStack()
    val portForwardSessionsViewModel: PortForwardSessionsViewModel = hiltViewModel()
    val portForwardSessionsState by rememberPortForwardSessionsState(portForwardSessionsViewModel)
    var showPortForwardSessions by rememberSaveable { mutableStateOf(false) }
    val tabStateHolder = rememberSaveableStateHolder()

    // Every screen on the stack gets its own ViewModel store, keyed by everything its
    // ViewModels are bound to. The active cluster is part of every key, so a screen opened
    // on one cluster never hands its state, or its actions, to another.
    val screenStores: ScreenViewModelStores = viewModel()
    // Only the cluster id, so MainScreen does not recompose on every Home state change.
    val activeClusterId by homeViewModel.activeClusterId.collectAsStateWithLifecycle()
    val clusterKey = activeClusterId.orEmpty()
    val liveScreenKeys = backStack.screens.mapNotNull { it.storeKey(clusterKey) }.toSet()
    // Runs after the screen that was left has already gone from composition, so its
    // ViewModels are cleared (streams, sessions and native terminals released) only once
    // nothing on screen uses them.
    LaunchedEffect(liveScreenKeys) { screenStores.retainOnly(liveScreenKeys) }

    val context = LocalContext.current
    // The only collector of Home's effects. They come from a Channel, which hands each event to
    // exactly one collector, so a second collector elsewhere silently took half of them.
    LaunchedEffect(homeViewModel.effects) {
        homeViewModel.effects.collect { effect ->
            when (effect) {
                is HomeUiEffect.ShowToast -> Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                HomeUiEffect.NavigateToHome -> {
                    backStack.clear()
                    showPortForwardSessions = false
                    currentDestination = Destination.Favourites
                }
            }
        }
    }

    // Registered before any screen's own handler, so a screen that handles Back itself
    // (a terminal's keyboard, say) still gets it first.
    BackHandler(enabled = backStack.current != null) { backStack.pop() }

    val screen = backStack.current
    if (screen == null) {
        MainTopLevelScaffold(
            homeViewModel = homeViewModel,
            currentDestination = currentDestination,
            onSelectDestination = { currentDestination = it },
            onOpen = backStack::push,
            activeForwardCount = portForwardSessionsState.activeCount,
            onOpenPortForwardSessions = { showPortForwardSessions = true },
            tabStateHolder = tabStateHolder,
            modifier = modifier,
        )
    } else {
        ScreenContent(
            screen = screen,
            homeViewModel = homeViewModel,
            stores = screenStores,
            clusterKey = clusterKey,
            onOpen = backStack::push,
            onNavigateBack = backStack::pop,
            modifier = modifier,
        )
    }

    if (showPortForwardSessions) {
        MainPortForwardSessionsEntry(
            onDismiss = { showPortForwardSessions = false },
        )
    }
}

@Composable
private fun ScreenContent(
    screen: Screen,
    homeViewModel: HomeViewModel,
    stores: ScreenViewModelStores,
    clusterKey: String,
    onOpen: (Screen) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    /** Gives [content]'s ViewModels the store of this screen, see [Screen.storeKey]. */
    @Composable
    fun Scoped(content: @Composable () -> Unit) =
        ScreenViewModelScope(key = checkNotNull(screen.storeKey(clusterKey)), stores = stores, content = content)

    when (screen) {
        is Screen.ResourceList -> Scoped {
            val homeUiState by homeViewModel.uiState.collectAsStateWithLifecycle()
            ResourceListDestination(
                screen = screen,
                clusterId = homeUiState.activeCluster?.id,
                initialNamespace = namespaceOrNull(homeUiState.selectedNamespace),
                onNavigateBack = onNavigateBack,
                onOpen = onOpen,
                modifier = modifier,
            )
        }

        is Screen.ResourceDetail -> Scoped {
            ResourceDetailDestination(
                screen = screen,
                onNavigateBack = onNavigateBack,
                onOpen = onOpen,
                modifier = modifier,
            )
        }

        is Screen.CreateResource -> Scoped {
            CreateResourceDestination(
                screen = screen,
                homeViewModel = homeViewModel,
                onDismiss = onNavigateBack,
                modifier = modifier,
            )
        }

        // Scoped so the logcat process it streams from stops when the screen is left.
        Screen.Logcat -> Scoped {
            val logcatViewModel: LogcatViewModel = hiltViewModel()
            LogcatRoute(viewModel = logcatViewModel, onNavigateBack = onNavigateBack, modifier = modifier)
        }

        // Its state is the Activity-scoped HomeViewModel's.
        Screen.ManageClusters -> {
            val uiState by homeViewModel.uiState.collectAsStateWithLifecycle()
            ManageClustersScreen(
                uiState = uiState,
                onAction = homeViewModel::onAction,
                onNavigateBack = onNavigateBack,
                modifier = modifier,
            )
        }

        Screen.OpenSourceLicenses -> OpenSourceLicensesScreen(onNavigateBack = onNavigateBack, modifier = modifier)
    }
}
