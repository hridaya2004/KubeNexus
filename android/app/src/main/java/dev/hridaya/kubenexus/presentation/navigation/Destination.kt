package dev.hridaya.kubenexus.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.ui.graphics.vector.ImageVector

enum class Destination(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val badgeCount: Int? = null,
) {
    Favourites(
        route = "favourites",
        title = "Favourites",
        selectedIcon = Icons.Filled.Star,
        unselectedIcon = Icons.Outlined.StarBorder,
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
        val topLevelDestinations: List<Destination>
            get() = entries

        fun fromRoute(route: String?): Destination {
            return when (route?.substringBefore("/")) {
                Favourites.route -> Favourites
                Resources.route -> Resources
                Explore.route -> Explore
                Settings.route -> Settings
                else -> Favourites
            }
        }
    }
}
