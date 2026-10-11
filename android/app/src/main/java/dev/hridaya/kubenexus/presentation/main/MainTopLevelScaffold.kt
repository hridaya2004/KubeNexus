package dev.hridaya.kubenexus.presentation.main

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import dev.hridaya.kubenexus.presentation.explore.ExploreRoute
import dev.hridaya.kubenexus.presentation.explore.ExploreViewModel
import dev.hridaya.kubenexus.presentation.favourites.FavouritesRoute
import dev.hridaya.kubenexus.presentation.favourites.FavouritesUiEffect
import dev.hridaya.kubenexus.presentation.favourites.FavouritesViewModel
import dev.hridaya.kubenexus.presentation.home.HomeUiAction
import dev.hridaya.kubenexus.presentation.home.HomeViewModel
import dev.hridaya.kubenexus.presentation.home.ResourcesRoute
import dev.hridaya.kubenexus.presentation.navigation.Destination
import dev.hridaya.kubenexus.presentation.navigation.Screen
import dev.hridaya.kubenexus.presentation.settings.SettingsScreen

@Composable
internal fun MainTopLevelScaffold(
    homeViewModel: HomeViewModel,
    currentDestination: Destination,
    onSelectDestination: (Destination) -> Unit,
    onOpen: (Screen) -> Unit,
    activeForwardCount: Int,
    onOpenPortForwardSessions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val favouritesViewModel: FavouritesViewModel = hiltViewModel()
    val context = LocalContext.current
    LaunchedEffect(favouritesViewModel.effects) {
        favouritesViewModel.effects.collect { effect ->
            when (effect) {
                is FavouritesUiEffect.ShowToast -> Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    NavigationSuiteScaffold(
        navigationItems = {
            Destination.topLevelDestinations.forEach { destination ->
                val selected = currentDestination == destination
                NavigationSuiteItem(
                    selected = selected,
                    onClick = {
                        homeViewModel.onAction(HomeUiAction.DismissFabMenu)
                        onSelectDestination(destination)
                    },
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
                Destination.Favourites -> {
                    FavouritesRoute(
                        homeViewModel = homeViewModel,
                        favouritesViewModel = favouritesViewModel,
                        onNavigateToManageClusters = { onOpen(Screen.ManageClusters) },
                        onOpenResources = { onOpen(Screen.ResourceList(it.kind.type.key)) },
                        onCreateResource = { onOpen(Screen.CreateResource(it.kind.type.key)) },
                        onBrowseResources = { onSelectDestination(Destination.Resources) },
                        activePortForwardCount = activeForwardCount,
                        onOpenPortForwardSessions = onOpenPortForwardSessions,
                    )
                }

                Destination.Resources -> {
                    ResourcesRoute(
                        viewModel = homeViewModel,
                        favouritesViewModel = favouritesViewModel,
                        onNavigateToManageClusters = { onOpen(Screen.ManageClusters) },
                        onOpenResources = { onOpen(Screen.ResourceList(it.kind.type.key)) },
                        onCreateResource = { onOpen(Screen.CreateResource(it.kind.type.key)) },
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
                        onNavigateToLogcat = { onOpen(Screen.Logcat) },
                        onNavigateToOpenSourceLicenses = { onOpen(Screen.OpenSourceLicenses) },
                        onClearCachedData = { homeViewModel.onAction(HomeUiAction.ClearCachedData) },
                    )
                }
            }
        }
    }
}
