package dev.hridaya.kubenexus.presentation.pods.components.terminal

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.hridaya.kubenexus.ui.theme.KubeNexusTheme

@Composable
internal fun TerminalKeyButton(
    label: String,
    onClick: () -> Unit,
    isActive: Boolean = false,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraSmall,
        color = if (isActive) Color.White else GhosttyKeyBg,
        border = BorderStroke(
            1.dp,
            if (isActive) Color.White else GhosttyKeyBorder
        ),
        modifier = Modifier
            .height(32.dp)
            .semantics { KEY_NAMES[label]?.let { contentDescription = it } },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 10.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = if (isActive) Color.Black else GhosttyText,
            )
        }
    }
}

private val KEY_NAMES = mapOf(
    "^C" to "Control C",
    "^D" to "Control D",
    "^Z" to "Control Z",
    "↑" to "Up arrow",
    "↓" to "Down arrow",
    "←" to "Left arrow",
    "→" to "Right arrow",
)

@Preview(showBackground = true)
@Composable
private fun TerminalKeyButtonPreview() {
    KubeNexusTheme {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(12.dp),
        ) {
            TerminalKeyButton(label = "ESC", onClick = {})
            TerminalKeyButton(label = "TAB", onClick = {})
            TerminalKeyButton(label = "CTRL", onClick = {}, isActive = true)
            TerminalKeyButton(label = "ALT", onClick = {})
            TerminalKeyButton(label = "↑", onClick = {})
        }
    }
}
