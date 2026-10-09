package dev.hridaya.kubenexus.presentation.pods.components.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
internal fun TerminalCommandInput(
    value: String,
    onValueChange: (String) -> Unit,
    onExecute: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .background(GhosttyBg, MaterialTheme.shapes.small)
            .border(1.dp, GhosttyBorder, MaterialTheme.shapes.small)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = "❯ ",
            style = MaterialTheme.typography.labelMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
            ),
            color = Color.White,
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                color = GhosttyText,
            ),
            cursorBrush = SolidColor(Color.White),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Send,
            ),
            keyboardActions = KeyboardActions(
                onSend = { onExecute() },
            ),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
        )
        IconButton(
            onClick = onExecute,
            modifier = Modifier.size(28.dp),
            shapes = IconButtonDefaults.shapes(),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.Send,
                contentDescription = "Send Command",
                tint = if (value.isNotEmpty()) Color.White else GhosttyGutter,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
