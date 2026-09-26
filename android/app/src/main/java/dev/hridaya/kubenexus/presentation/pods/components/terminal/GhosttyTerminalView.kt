package dev.hridaya.kubenexus.presentation.pods.components.terminal

import android.content.ClipboardManager
import android.content.Context
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import dev.hridaya.kubenexus.core.common.util.ClipboardHelper
import dev.hridaya.kubenexus.presentation.pods.detail.PodDetailUiAction
import dev.hridaya.kubenexus.presentation.pods.detail.PodDetailUiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GhosttyTerminalView(
    uiState: PodDetailUiState,
    engine: GhosttyTerminalEngine,
    onAction: (PodDetailUiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val snapshot by engine.snapshot.collectAsState()
    val isImeVisible = WindowInsets.isImeVisible

    var terminalSelection by remember { mutableStateOf<TerminalSelection?>(null) }
    var localInput by remember { mutableStateOf("") }
    var commandHistory by remember { mutableStateOf(listOf<String>()) }
    var historyIndex by remember { mutableIntStateOf(-1) }
    var ctrlActive by remember { mutableStateOf(false) }
    var altActive by remember { mutableStateOf(false) }

    // When soft keyboard is visible and user presses back, forcefully clear focus and hide keyboard so layout expands to full screen
    BackHandler(enabled = isImeVisible) {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
    }

    // Force layout expansion when IME visibility changes to hidden (e.g. system back key / IME close)
    LaunchedEffect(isImeVisible) {
        if (!isImeVisible) {
            focusManager.clearFocus(force = true)
        }
    }

    LaunchedEffect(Unit) {
        if (!engine.isNativeLoaded) {
            engine.initialize(80, 24)
        }
    }

    LaunchedEffect(uiState.isTerminalActive) {
        if (!uiState.isTerminalActive) {
            localInput = ""
            terminalSelection = null
            focusManager.clearFocus(force = true)
        }
    }

    fun executeLocalCommand() {
        if (localInput.isNotEmpty()) {
            val command = localInput
            if (commandHistory.isEmpty() || commandHistory.last() != command) {
                commandHistory = commandHistory + command
            }
            historyIndex = -1
            engine.sendText(command + "\n")
            localInput = ""
        } else {
            engine.sendText("\n")
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .imePadding(),
    ) {
        TerminalTargetBar(uiState = uiState, onAction = onAction)

        // Pure pitch black terminal box
        Surface(
            color = GhosttyBg,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .border(1.dp, GhosttyBorder, MaterialTheme.shapes.small)
                .clip(MaterialTheme.shapes.small),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TerminalHeaderBar(
                    uiState = uiState,
                    snapshot = snapshot,
                    isImeVisible = isImeVisible,
                    onToggleKeyboard = {
                        if (isImeVisible) {
                            focusManager.clearFocus(force = true)
                            keyboardController?.hide()
                        } else {
                            focusRequester.requestFocus()
                            keyboardController?.show()
                        }
                    },
                    onSelectAll = {
                        val snap = snapshot
                        if (snap != null && snap.cols > 0 && snap.rows > 0) {
                            terminalSelection =
                                TerminalSelection(0, (snap.cols * snap.rows) - 1)
                        }
                    },
                    onCopy = {
                        val snap = snapshot
                        if (snap != null) {
                            val selectionRange =
                                terminalSelection?.normalized(snap.cols * snap.rows)
                            val copiedText = if (selectionRange != null) {
                                extractSelectionText(snap, selectionRange)
                            } else {
                                extractSelectionText(
                                    snap,
                                    0 until (snap.cols * snap.rows)
                                )
                            }
                            if (!copiedText.isNullOrBlank()) {
                                ClipboardHelper.copy(context, "Terminal Output", copiedText, sensitive = true)
                                Toast.makeText(
                                    context,
                                    "Terminal output copied",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    },
                    onPaste = {
                        val clipboard =
                            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = clipboard.primaryClip
                        if (clip != null && clip.itemCount > 0) {
                            val pasteText = clip.getItemAt(0).text?.toString().orEmpty()
                            if (pasteText.isNotEmpty()) {
                                if (uiState.isTerminalActive) {
                                    localInput += pasteText
                                    focusRequester.requestFocus()
                                } else {
                                    engine.sendPaste(pasteText)
                                }
                            }
                        }
                    },
                    onClear = {
                        onAction(PodDetailUiAction.ClearTerminal)
                        terminalSelection = null
                    },
                )

                HorizontalDivider(
                    color = GhosttyBorder,
                    thickness = 1.dp,
                )

                // Render TerminalCanvas & selection controls
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(GhosttyBg),
                ) {
                    val hasContent = snapshot?.let { snap ->
                        snap.codepoints.any { it != 0 && it != 32 }
                    } ?: false

                    if (!uiState.isTerminalActive && !hasContent) {
                        TerminalDetachedPlaceholder(
                            isOnline = uiState.isOnline,
                            selectedContainer = uiState.selectedContainer,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        TerminalCanvas(
                            snapshot = snapshot,
                            terminalHandle = engine.terminalHandle,
                            selection = terminalSelection,
                            onSelectionChange = { terminalSelection = it },
                            onResize = { cols, rows, cellW, cellH, _, _ ->
                                engine.resize(cols, rows, cellW, cellH)
                            },
                            onScroll = { delta, x, y ->
                                engine.scroll(delta, x, y)
                            },
                            onTap = {
                                if (terminalSelection != null) {
                                    terminalSelection = null
                                } else if (isImeVisible) {
                                    focusManager.clearFocus(force = true)
                                    keyboardController?.hide()
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }

        // Dedicated local command input line with 0ms local latency
        if (uiState.isTerminalActive) {
            Spacer(modifier = Modifier.height(6.dp))
            TerminalCommandInput(
                value = localInput,
                onValueChange = { localInput = it },
                onExecute = { executeLocalCommand() },
                focusRequester = focusRequester,
            )
        }

        // Accessory keys bar (Virtual keys for ESC, TAB, CTRL, ALT, UP, DOWN, etc.)
        Spacer(modifier = Modifier.height(6.dp))
        TerminalKeysBar(
            engine = engine,
            focusRequester = focusRequester,
            isTerminalActive = uiState.isTerminalActive,
            ctrlActive = ctrlActive,
            altActive = altActive,
            onCtrlToggle = { ctrlActive = !ctrlActive },
            onAltToggle = { altActive = !altActive },
            onAppendLocalInput = { localInput += it },
            onClearLocalInput = { localInput = "" },
            onHistoryUp = {
                if (commandHistory.isNotEmpty()) {
                    val nextIdx = if (historyIndex == -1) {
                        commandHistory.lastIndex
                    } else {
                        (historyIndex - 1).coerceAtLeast(0)
                    }
                    historyIndex = nextIdx
                    localInput = commandHistory[nextIdx]
                } else {
                    engine.sendKey(KeyEvent.KEYCODE_DPAD_UP)
                }
            },
            onHistoryDown = {
                if (commandHistory.isNotEmpty() && historyIndex != -1) {
                    val nextIdx = historyIndex + 1
                    if (nextIdx <= commandHistory.lastIndex) {
                        historyIndex = nextIdx
                        localInput = commandHistory[nextIdx]
                    } else {
                        historyIndex = -1
                        localInput = ""
                    }
                } else {
                    engine.sendKey(KeyEvent.KEYCODE_DPAD_DOWN)
                }
            },
        )
    }
}
