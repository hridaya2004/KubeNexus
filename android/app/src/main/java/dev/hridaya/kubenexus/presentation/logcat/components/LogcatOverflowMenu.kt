package dev.hridaya.kubenexus.presentation.logcat.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

@Composable
fun LogcatOverflowMenu(
    onCopyLogs: () -> Unit,
    onShareLogs: () -> Unit,
    onRefreshLogs: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        IconButton(onClick = { menuExpanded = true }, shapes = IconButtonDefaults.shapes()) {
            Icon(
                imageVector = Icons.Outlined.MoreVert,
                contentDescription = "More Options",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("Copy All") },
                leadingIcon = {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = null)
                },
                onClick = {
                    menuExpanded = false
                    onCopyLogs()
                },
            )
            DropdownMenuItem(
                text = { Text("Share Logs") },
                leadingIcon = {
                    Icon(Icons.Outlined.Share, contentDescription = null)
                },
                onClick = {
                    menuExpanded = false
                    onShareLogs()
                },
            )
            DropdownMenuItem(
                text = { Text("Refresh Dump") },
                leadingIcon = {
                    Icon(Icons.Outlined.Refresh, contentDescription = null)
                },
                onClick = {
                    menuExpanded = false
                    onRefreshLogs()
                },
            )
        }
    }
}
