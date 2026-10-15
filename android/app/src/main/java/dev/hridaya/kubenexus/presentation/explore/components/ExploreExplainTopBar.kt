package dev.hridaya.kubenexus.presentation.explore.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.hridaya.kubenexus.domain.model.APIResource
import dev.hridaya.kubenexus.ui.theme.KubeNexusTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreExplainTopBar(
    resource: APIResource,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    TopAppBar(
        modifier = modifier,
        title = { Text(text = resource.kind) },
        subtitle = {
            Text(
                text = resource.name,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .clickable(onClickLabel = "Copy kubectl explain command", role = Role.Button) {
                    val clipboard =
                        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val commandClip =
                        ClipData.newPlainText("Command", "kubectl explain ${resource.name}")
                    clipboard.setPrimaryClip(commandClip)
                    Toast.makeText(context, "Command copied", Toast.LENGTH_SHORT).show()
                }
                    .padding(vertical = 4.dp),
            )
        },
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
private fun ExploreExplainTopBarPreview() {
    KubeNexusTheme {
        ExploreExplainTopBar(
            resource = APIResource(
                name = "deployments",
                singularName = "deployment",
                namespaced = true,
                kind = "Deployment",
                verbs = listOf("get", "list", "watch", "create", "update", "patch", "delete"),
                group = "apps",
                version = "v1",
                shortNames = listOf("deploy"),
            ),
            onNavigateBack = {},
        )
    }
}
