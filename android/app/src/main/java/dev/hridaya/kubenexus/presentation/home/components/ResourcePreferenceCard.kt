package dev.hridaya.kubenexus.presentation.home.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.hridaya.kubenexus.presentation.common.components.segmentedListItemColors
import dev.hridaya.kubenexus.ui.theme.KubeNexusTheme

@Composable
fun ResourcePreferenceCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    shapes: ListItemShapes,
    modifier: Modifier = Modifier,
    /** Null hides the favourite toggle. */
    isFavourite: Boolean? = null,
    onToggleFavourite: () -> Unit = {},
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = shapes,
        modifier = modifier,
        colors = segmentedListItemColors(),
        verticalAlignment = Alignment.CenterVertically,
        leadingContent = { Icon(imageVector = icon, contentDescription = null) },
        supportingContent = { Text(text = subtitle) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isFavourite != null) {
                    IconToggleButton(
                        checked = isFavourite,
                        onCheckedChange = { onToggleFavourite() },
                        shapes = IconButtonDefaults.toggleableShapes(),
                    ) {
                        Icon(
                            imageVector = if (isFavourite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                            contentDescription = if (isFavourite) {
                                "Remove $title from favourites"
                            } else {
                                "Add $title to favourites"
                            },
                            tint = if (isFavourite) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                )
            }
        },
    ) {
        Text(text = title)
    }
}

@Preview(showBackground = true)
@Composable
private fun ResourcePreferenceCardPreview() {
    KubeNexusTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            ResourcePreferenceCard(
                title = "Deployments",
                subtitle = "Manage replica sets and rollout status",
                icon = Icons.Outlined.Layers,
                onClick = {},
                shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
                isFavourite = true,
            )
        }
    }
}
