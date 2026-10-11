package dev.hridaya.kubenexus.presentation.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.hridaya.kubenexus.presentation.navigation.Destination
import dev.hridaya.kubenexus.ui.theme.KubeNexusTheme

@Preview(showBackground = true)
@Composable
private fun MainScreenNavigationPreview() {
    KubeNexusTheme {
        NavigationSuiteScaffold(
            navigationItems = {
                Destination.topLevelDestinations.forEach { destination ->
                    NavigationSuiteItem(
                        selected = destination == Destination.Favourites,
                        onClick = {},
                        icon = {
                            Icon(
                                imageVector = destination.selectedIcon,
                                contentDescription = destination.title,
                            )
                        },
                        label = { Text(text = destination.title) },
                    )
                }
            },
        ) {
            Box(modifier = Modifier.fillMaxSize())
        }
    }
}
