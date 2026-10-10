package dev.hridaya.kubenexus.presentation.common.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hridaya.kubenexus.core.common.util.ClipboardHelper
import dev.hridaya.kubenexus.core.common.util.JsonFormatter
import dev.hridaya.kubenexus.ui.theme.KubeNexusTheme

/**
 * Metadata key/value row for Kubernetes labels and annotations.
 *
 * If [value] is structured JSON (an object or array), this component makes the value
 * collapsible:
 * - When collapsed: displays the key, a "JSON" badge, an animated toggle chevron,
 *   and a compact single-line preview.
 * - When expanded: displays the full, pretty-printed JSON inside a scrollable and
 *   selectable code block container with a one-tap copy button.
 *
 * For non-JSON values, standard monospace key and value rows are rendered.
 */
@Composable
fun MetadataKeyValueRow(
    key: String,
    value: String,
    modifier: Modifier = Modifier,
    initialExpanded: Boolean = false,
) {
    val formattedJson = remember(value) { JsonFormatter.formatIfJson(value) }

    if (formattedJson == null) {
        NonJsonMetadataRow(
            key = key,
            value = value,
            modifier = modifier,
        )
    } else {
        CollapsibleJsonMetadataRow(
            key = key,
            rawJson = value,
            formattedJson = formattedJson,
            initialExpanded = initialExpanded,
            modifier = modifier,
        )
    }
}

@Composable
private fun NonJsonMetadataRow(
    key: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = key,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

@Composable
private fun CollapsibleJsonMetadataRow(
    key: String,
    rawJson: String,
    formattedJson: String,
    initialExpanded: Boolean,
    modifier: Modifier = Modifier,
) {
    var isExpanded by rememberSaveable(key) { mutableStateOf(initialExpanded) }
    val chevronRotation = animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "jsonChevronRotation",
    )
    val context = LocalContext.current

    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.extraSmall)
                .clickable(
                    role = Role.Button,
                    onClick = { isExpanded = !isExpanded },
                )
                .padding(vertical = 2.dp),
        ) {
            Text(
                text = key,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Surface(
                shape = MaterialTheme.shapes.extraSmall,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
            ) {
                Text(
                    text = "JSON",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                    ),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = if (isExpanded) "Collapse $key JSON" else "Expand $key JSON",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(18.dp)
                    .graphicsLayer { rotationZ = chevronRotation.value },
            )
        }

        if (!isExpanded) {
            Text(
                text = rawJson.replace("\n", " ").trim(),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .clickable(role = Role.Button) { isExpanded = true },
            )
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) +
                fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
            exit = shrinkVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) +
                fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, top = 4.dp),
            ) {
                Column(
                    modifier = Modifier.padding(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = {
                                // Annotations such as last-applied-configuration can carry secrets.
                                ClipboardHelper.copy(context, key, formattedJson, sensitive = true)
                                Toast.makeText(
                                    context,
                                    "Copied $key JSON to clipboard",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            },
                            modifier = Modifier.size(28.dp),
                            shapes = IconButtonDefaults.shapes(),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ContentCopy,
                                contentDescription = "Copy $key JSON",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    SelectionContainer {
                        Text(
                            text = formattedJson,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 18.sp,
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MetadataKeyValueRowPlainPreview() {
    KubeNexusTheme {
        MetadataKeyValueRow(
            key = "app.kubernetes.io/name",
            value = "kubenexus-backend",
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MetadataKeyValueRowJsonCollapsedPreview() {
    KubeNexusTheme {
        MetadataKeyValueRow(
            key = "kubectl.kubernetes.io/last-applied-configuration",
            value = """{"apiVersion":"apps/v1","kind":"Deployment","metadata":{"name":"nginx"}}""",
            modifier = Modifier.padding(16.dp),
            initialExpanded = false,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MetadataKeyValueRowJsonExpandedPreview() {
    KubeNexusTheme {
        MetadataKeyValueRow(
            key = "kubectl.kubernetes.io/last-applied-configuration",
            value = """{"apiVersion":"apps/v1","kind":"Deployment","metadata":{"name":"nginx"}}""",
            modifier = Modifier.padding(16.dp),
            initialExpanded = true,
        )
    }
}
