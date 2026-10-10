package dev.hridaya.kubenexus.presentation.resources.list.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.hridaya.kubenexus.presentation.common.namespaceOrNull
import dev.hridaya.kubenexus.presentation.resources.kinds.ServicesPresentation
import dev.hridaya.kubenexus.ui.theme.KubeNexusTheme

@Composable
fun ResourceListLoading(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        LoadingIndicator()
    }
}

/** Nothing cached for this namespace and the cluster answered with nothing either. */
@Composable
fun ResourceListEmpty(
    title: String,
    icon: ImageVector,
    selectedNamespace: String,
    modifier: Modifier = Modifier,
) {
    val namespace = namespaceOrNull(selectedNamespace)
    ListMessage(
        icon = icon,
        headline = "No $title",
        message = if (namespace != null) {
            "No ${title.lowercase()} in namespace '$namespace'."
        } else {
            "No ${title.lowercase()} in this cluster."
        },
        modifier = modifier,
    )
}

/** The search matched nothing among the rows on screen. */
@Composable
fun ResourceListNoMatch(
    title: String,
    searchQuery: String,
    onClearSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListMessage(
        icon = Icons.Outlined.SearchOff,
        headline = "No Matching $title",
        message = "No ${title.lowercase()} match '$searchQuery'.",
        modifier = modifier,
    ) {
        OutlinedButton(onClick = onClearSearch, shapes = ButtonDefaults.shapes()) {
            Icon(
                imageVector = Icons.Outlined.Clear,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Clear Search")
        }
    }
}

/** The sync failed and nothing is cached to show instead. */
@Composable
fun ResourceListSyncError(
    title: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListMessage(
        icon = Icons.Outlined.CloudOff,
        headline = "Couldn't Load $title",
        message = "Check that the cluster is reachable and try again.",
        modifier = modifier,
    ) {
        OutlinedButton(onClick = onRetry, shapes = ButtonDefaults.shapes()) {
            Text(text = "Retry", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun ListMessage(
    icon: ImageVector,
    headline: String,
    message: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = headline,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (action != null) {
                Spacer(modifier = Modifier.height(16.dp))
                action()
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ResourceListEmptyPreview() {
    KubeNexusTheme {
        ResourceListEmpty(
            title = ServicesPresentation.title,
            icon = ServicesPresentation.icon,
            selectedNamespace = "default",
        )
    }
}
