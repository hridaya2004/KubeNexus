package dev.hridaya.kubenexus.presentation.pods.detail.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import dev.hridaya.kubenexus.ui.theme.KubeNexusTheme

/**
 * Detail screen app bar: back navigation and pod title.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodDetailTopBar(
    podName: String,
    namespace: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TopAppBar(
        modifier = modifier,
        title = {
            Text(
                text = podName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        subtitle = { Text(text = namespace) },
        navigationIcon = {
            IconButton(onClick = onNavigateBack, shapes = IconButtonDefaults.shapes()) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                )
            }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun PodDetailTopBarPreview() {
    KubeNexusTheme {
        PodDetailTopBar(
            podName = "nginx-deployment-78f56c879d-gqw87",
            namespace = "default",
            onNavigateBack = {},
        )
    }
}
