package dev.hridaya.kubenexus.presentation.pods.components.terminal

import android.view.KeyEvent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp

@Composable
internal fun TerminalKeysBar(
    engine: GhosttyTerminalEngine,
    focusRequester: FocusRequester,
    isTerminalActive: Boolean,
    modifiers: TerminalModifiers,
    onModifiersChange: (TerminalModifiers) -> Unit,
    onAppendLocalInput: (String) -> Unit,
    onClearLocalInput: () -> Unit,
    onHistoryUp: () -> Unit,
    onHistoryDown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Sends a key with the armed CTRL/ALT modifiers, then releases them.
    fun sendModifiedKey(keyCode: Int) {
        engine.sendKey(keyCode, metaState = modifiers.metaState)
        if (modifiers.any) onModifiersChange(TerminalModifiers())
    }

    // Accessory keys bar (Virtual keys for ESC, TAB, CTRL, ALT, UP, DOWN, etc.)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TerminalKeyButton(
            label = "ESC",
            onClick = {
                sendModifiedKey(KeyEvent.KEYCODE_ESCAPE)
                focusRequester.requestFocus()
            },
        )
        TerminalKeyButton(
            label = "TAB",
            onClick = {
                if (isTerminalActive) {
                    onAppendLocalInput("\t")
                } else {
                    sendModifiedKey(KeyEvent.KEYCODE_TAB)
                }
                focusRequester.requestFocus()
            },
        )
        TerminalKeyButton(
            label = "CTRL",
            isActive = modifiers.ctrl,
            onClick = {
                onModifiersChange(modifiers.copy(ctrl = !modifiers.ctrl))
                focusRequester.requestFocus()
            },
        )
        TerminalKeyButton(
            label = "ALT",
            isActive = modifiers.alt,
            onClick = {
                onModifiersChange(modifiers.copy(alt = !modifiers.alt))
                focusRequester.requestFocus()
            },
        )
        TerminalKeyButton(
            label = "^C",
            onClick = {
                engine.sendText("\u0003")
                onClearLocalInput()
                focusRequester.requestFocus()
            },
        )
        TerminalKeyButton(
            label = "^D",
            onClick = {
                engine.sendText("\u0004")
                focusRequester.requestFocus()
            },
        )
        TerminalKeyButton(
            label = "^Z",
            onClick = {
                engine.sendText("\u001A")
                focusRequester.requestFocus()
            },
        )
        TerminalKeyButton(
            label = "↑",
            onClick = {
                onHistoryUp()
                focusRequester.requestFocus()
            },
        )
        TerminalKeyButton(
            label = "↓",
            onClick = {
                onHistoryDown()
                focusRequester.requestFocus()
            },
        )
        TerminalKeyButton(
            label = "←",
            onClick = {
                sendModifiedKey(KeyEvent.KEYCODE_DPAD_LEFT)
                focusRequester.requestFocus()
            },
        )
        TerminalKeyButton(
            label = "→",
            onClick = {
                sendModifiedKey(KeyEvent.KEYCODE_DPAD_RIGHT)
                focusRequester.requestFocus()
            },
        )
    }
}
