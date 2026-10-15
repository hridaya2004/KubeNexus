package dev.hridaya.kubenexus.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

enum class Destination(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val badgeCount: Int? = null,
) {
    Home(
        route = "home",
        title = "Home",
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
    ),
    Resources(
        route = "resources",
        title = "Resources",
        selectedIcon = Icons.Filled.Category,
        unselectedIcon = Icons.Outlined.Category,
    ),
    Explore(
        route = "explore",
        title = "Explore",
        selectedIcon = Icons.Filled.Explore,
        unselectedIcon = Icons.Outlined.Explore,
    ),
    Settings(
        route = "settings",
        title = "Settings",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
    ),
    ;

    companion object {
        val Favourites: Destination get() = Home

        val topLevelDestinations: List<Destination>
            get() = entries

        fun fromRoute(route: String?): Destination {
            return when (route?.substringBefore("/")) {
                Home.route, "favourites" -> Home
                Resources.route -> Resources
                Explore.route -> Explore
                Settings.route -> Settings
                else -> Home
            }
        }
    }
}
