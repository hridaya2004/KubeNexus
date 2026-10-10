package dev.hridaya.kubenexus.presentation.home.components

import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.material3.animateFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.traversalIndex
import dev.hridaya.kubenexus.presentation.resources.ResourcePresentation

@Composable
fun CreateFabMenu(
    expanded: Boolean,
    visible: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    creatableResources: List<ResourcePresentation<*>>,
    onAddResourceClick: (ResourcePresentation<*>) -> Unit,
    onAddClusterClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = expanded) { onExpandedChange(false) }

    val items = creatableResources.map { presentation ->
        MenuEntry("Add ${presentation.singularTitle}", presentation.icon) { onAddResourceClick(presentation) }
    } + MenuEntry("Add Cluster", Icons.Outlined.CloudQueue, onAddClusterClick)

    FloatingActionButtonMenu(
        expanded = expanded,
        modifier = modifier,
        button = {
            ToggleFloatingActionButton(
                checked = expanded,
                onCheckedChange = onExpandedChange,
                modifier = Modifier
                    .semantics {
                        traversalIndex = -1f
                        contentDescription = "Toggle menu"
                        stateDescription = if (expanded) "Expanded" else "Collapsed"
                    }
                    .animateFloatingActionButton(
                        visible = visible || expanded,
                        alignment = Alignment.BottomEnd,
                    ),
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
