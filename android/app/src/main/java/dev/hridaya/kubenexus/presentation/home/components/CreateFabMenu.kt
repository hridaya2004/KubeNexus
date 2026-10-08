package dev.hridaya.kubenexus.presentation.home.components

import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.traversalIndex

@Composable
fun CreateFabMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onAddPodClick: () -> Unit,
    onAddDeploymentClick: () -> Unit,
    onAddServiceClick: () -> Unit,
    onAddClusterClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = expanded) { onExpandedChange(false) }

    val items = listOf(
        MenuEntry("Add Pod", Icons.Outlined.Layers, onAddPodClick),
        MenuEntry("Add Deployment", Icons.Outlined.Apps, onAddDeploymentClick),
        MenuEntry("Add Service", Icons.Outlined.Lan, onAddServiceClick),
        MenuEntry("Add Cluster", Icons.Outlined.CloudQueue, onAddClusterClick),
    )

    FloatingActionButtonMenu(
        expanded = expanded,
        modifier = modifier,
        button = {
            ToggleFloatingActionButton(
                checked = expanded,
                onCheckedChange = onExpandedChange,
                modifier = Modifier.semantics {
                    traversalIndex = -1f
                    contentDescription = "Toggle menu"
                    stateDescription = if (expanded) "Expanded" else "Collapsed"
                },
            ) {
                Icon(
                    imageVector = if (checkedProgress > 0.5f) Icons.Filled.Close else Icons.Filled.Add,
                    contentDescription = null,
                    modifier = Modifier.animateIcon({ checkedProgress }),
                )
            }
        },
    ) {
        items.forEach { item ->
            FloatingActionButtonMenuItem(
                onClick = {
                    onExpandedChange(false)
                    item.onClick()
                },
                text = { Text(item.label) },
                icon = { Icon(item.icon, contentDescription = null) },
            )
        }
    }
}

private data class MenuEntry(val label: String, val icon: ImageVector, val onClick: () -> Unit)
