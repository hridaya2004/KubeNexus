package dev.hridaya.kubenexus.presentation.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.hridaya.kubenexus.presentation.common.components.SegmentedListSection
import dev.hridaya.kubenexus.presentation.common.components.segmentedListItemColors
import dev.hridaya.kubenexus.ui.theme.KubeNexusTheme

@Composable
fun PreferenceNavigationItem(
    title: String,
    onClick: () -> Unit,
    shapes: ListItemShapes,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    painter: Painter? = icon?.let { rememberVectorPainter(it) },
    subtitle: String? = null,
) {
    PreferenceRow(
        title = title,
        subtitle = subtitle,
        shapes = shapes,
        modifier = modifier,
        onClick = onClick,
        leadingContent = painter?.let { { Icon(painter = it, contentDescription = null) } },
        trailingContent = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
            )
        },
    )
}

@Composable
fun PreferenceSwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    shapes: ListItemShapes,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    PreferenceRow(
        title = title,
        subtitle = subtitle,
        shapes = shapes,
        modifier = modifier,
        onClick = { onCheckedChange(!checked) },
        enabled = enabled,
        leadingContent = { Icon(imageVector = icon, contentDescription = null) },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        },
    )
}

@Composable
fun AboutItem(
    versionName: String,
    shapes: ListItemShapes,
    modifier: Modifier = Modifier,
) {
    PreferenceRow(
        title = "KubeNexus",
        // Registered mark of LF Projects, LLC: adjectival use only, never in the product
        // name. See legal/TRADEMARK-NOTICE.md.
        subtitle = "Client for Kubernetes® clusters\nv$versionName",
        shapes = shapes,
        modifier = modifier,
        leadingContent = { Icon(imageVector = Icons.Outlined.Info, contentDescription = null) },
    )
}

data class ModuleInfo(
    val title: String,
    val subtitle: String,
    val version: String,
    val icon: ImageVector,
)

@Composable
fun ModuleItem(
    module: ModuleInfo,
    shapes: ListItemShapes,
    modifier: Modifier = Modifier,
) {
    PreferenceRow(
        title = module.title,
        subtitle = module.subtitle,
        shapes = shapes,
        modifier = modifier,
        leadingContent = { Icon(imageVector = module.icon, contentDescription = null) },
        trailingContent = {
            Text(
                text = module.version,
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
            )
        },
    )
}

/**
 * A settings row in the theme's list colours. It draws them directly rather than fading between
 * them as Material's list item does, so on a theme change it switches in the same frame as the
 * rest of the screen.
 */
@Composable
private fun PreferenceRow(
    title: String,
    shapes: ListItemShapes,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    val colors = segmentedListItemColors()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shapes.shape)
            .background(colors.containerColor(enabled = enabled, selected = false, dragged = false))
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier)
            .padding(ListItemDefaults.ContentPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingContent != null) {
            CompositionLocalProvider(
                LocalContentColor provides
                    colors.leadingContentColor(enabled = enabled, selected = false, dragged = false),
                content = leadingContent,
            )
            Spacer(modifier = Modifier.width(16.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLargeEmphasized,
                color = colors.contentColor(enabled = enabled, selected = false, dragged = false),
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.supportingContentColor(enabled = enabled, selected = false, dragged = false),
                )
            }
        }
        if (trailingContent != null) {
            Spacer(modifier = Modifier.width(16.dp))
            CompositionLocalProvider(
                LocalContentColor provides
                    colors.trailingContentColor(enabled = enabled, selected = false, dragged = false),
                content = trailingContent,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PreferenceItemsPreview() {
    KubeNexusTheme {
        SegmentedListSection(title = "Preview") {
            PreferenceNavigationItem(
                icon = Icons.Outlined.BugReport,
                title = "Logcat",
                subtitle = "Inspect application logs",
                onClick = {},
                shapes = ListItemDefaults.segmentedShapes(index = 0, count = 3),
            )
            ModuleItem(
                module = ModuleInfo(
                    title = "Android App",
                    subtitle = "dev.hridaya.kubenexus",
                    version = "abc1234",
                    icon = Icons.Outlined.Android,
                ),
                shapes = ListItemDefaults.segmentedShapes(index = 1, count = 3),
            )
            AboutItem(
                versionName = "1.0.2",
                shapes = ListItemDefaults.segmentedShapes(index = 2, count = 3),
            )
        }
    }
}
