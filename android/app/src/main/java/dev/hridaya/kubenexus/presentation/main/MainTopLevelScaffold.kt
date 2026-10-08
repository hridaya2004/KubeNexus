package dev.hridaya.kubenexus.presentation.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import dev.hridaya.kubenexus.presentation.explore.ExploreRoute
import dev.hridaya.kubenexus.presentation.explore.ExploreViewModel
import dev.hridaya.kubenexus.presentation.home.HomeRoute
import dev.hridaya.kubenexus.presentation.home.HomeUiAction
import dev.hridaya.kubenexus.presentation.home.HomeViewModel
import dev.hridaya.kubenexus.presentation.navigation.Destination
import dev.hridaya.kubenexus.presentation.settings.SettingsScreen

@Composable
internal fun MainTopLevelScaffold(
    homeViewModel: HomeViewModel,
    currentDestination: Destination,
    onSelectDestination: (Destination) -> Unit,
    onNavigateToManageClusters: () -> Unit,
    onNavigateToPods: () -> Unit,
    onNavigateToDeployments: () -> Unit,
    onNavigateToServices: () -> Unit,
    onNavigateToCreatePod: () -> Unit,
    onNavigateToCreateDeployment: () -> Unit,
    onNavigateToCreateService: () -> Unit,
    onNavigateToLogcat: () -> Unit,
    onNavigateToOpenSourceLicenses: () -> Unit,
    activeForwardCount: Int,
    onOpenPortForwardSessions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationSuiteScaffold(
        navigationItems = {
            Destination.topLevelDestinations.forEach { destination ->
                val selected = currentDestination == destination
                NavigationSuiteItem(
                    selected = selected,
                    onClick = { onSelectDestination(destination) },
                    icon = {
                        Icon(
                            imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                            contentDescription = destination.title,
                        )
                    },
                    label = { Text(text = destination.title) },
                    badge = destination.badgeCount?.let { count -> { Badge { Text(text = "$count") } } },
                )
            }
        },
        modifier = modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            when (currentDestination) {
                Destination.Home -> {
                    HomeRoute(
                        viewModel = homeViewModel,
                        onNavigateToManageClusters = onNavigateToManageClusters,
                        onNavigateToPods = onNavigateToPods,
                        onNavigateToDeployments = onNavigateToDeployments,
                        onNavigateToServices = onNavigateToServices,
                        onNavigateToCreatePod = onNavigateToCreatePod,
                        onNavigateToCreateDeployment = onNavigateToCreateDeployment,
                        onNavigateToCreateService = onNavigateToCreateService,
                        activePortForwardCount = activeForwardCount,
                        onOpenPortForwardSessions = onOpenPortForwardSessions,
                    )
                }

                Destination.Explore -> {
                    val exploreViewModel: ExploreViewModel = hiltViewModel()
                    ExploreRoute(
                        viewModel = exploreViewModel,
                    )
                }

                Destination.Settings -> {
                    SettingsScreen(
                        onNavigateToLogcat = onNavigateToLogcat,
                        onNavigateToOpenSourceLicenses = onNavigateToOpenSourceLicenses,
                        onClearCachedData = { homeViewModel.onAction(HomeUiAction.ClearCachedData) },
                    )
                }
            }
        }
    }
}
