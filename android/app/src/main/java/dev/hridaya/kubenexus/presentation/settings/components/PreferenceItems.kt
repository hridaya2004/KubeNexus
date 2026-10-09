package dev.hridaya.kubenexus.presentation.settings.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForwardIos
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
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
    ListItem(
        onClick = onClick,
        modifier = modifier,
        shapes = shapes,
        colors = segmentedListItemColors(),
        leadingContent = painter?.let {
            {
                Icon(
                    painter = it,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        },
        supportingContent = subtitle?.let { { Text(text = it) } },
        trailingContent = {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowForwardIos,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
        },
    ) {
        Text(text = title)
    }
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
    ListItem(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        shapes = shapes,
        colors = segmentedListItemColors(),
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        supportingContent = { Text(text = subtitle) },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = null, enabled = enabled)
        },
    ) {
        Text(text = title)
    }
}

@Composable
fun AboutItem(
    versionName: String,
    shapes: ListItemShapes,
    modifier: Modifier = Modifier,
) {
    ListItem(
        modifier = modifier,
        shapes = shapes,
        colors = segmentedListItemColors(),
        leadingContent = {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        supportingContent = {
            // Registered mark of LF Projects, LLC: adjectival use only, never in the product
            // name. See legal/TRADEMARK-NOTICE.md.
            Text(text = "Client for Kubernetes® clusters · v$versionName")
        },
    ) {
        Text(text = "KubeNexus", style = MaterialTheme.typography.titleMediumEmphasized)
    }
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
    val context = LocalContext.current
    ListItem(
        onClick = { context.copyVersion(module) },
        modifier = modifier,
        shapes = shapes,
        colors = segmentedListItemColors(),
        leadingContent = {
            Icon(
                imageVector = module.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        supportingContent = { Text(text = module.subtitle) },
        trailingContent = {
            Row(
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = module.version,
                    style = MaterialTheme.typography.labelMediumEmphasized.copy(
                        fontFamily = FontFamily.Monospace,
                    ),
                    color = MaterialTheme.colorScheme.primary,
                )
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                )
            }
        },
    ) {
        Text(text = module.title)
    }
}

private fun Context.copyVersion(module: ModuleInfo) {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText("Version", module.version))
    Toast.makeText(this, "Copied ${module.title} (${module.version})", Toast.LENGTH_SHORT).show()
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
