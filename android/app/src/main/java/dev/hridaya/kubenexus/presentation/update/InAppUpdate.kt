package dev.hridaya.kubenexus.presentation.update

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.flowWithLifecycle
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.ktx.AppUpdateResult
import com.google.android.play.core.ktx.isFlexibleUpdateAllowed
import com.google.android.play.core.ktx.requestUpdateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/**
 * Offers Play's flexible in-app update once per session, and asks to restart once it has
 * downloaded. Does nothing when the app was not installed from Play, which covers debug builds
 * and sideloaded APKs.
 */
@Composable
fun InAppUpdate() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val manager = remember { AppUpdateManagerFactory.create(context.applicationContext) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {}

    var offered by rememberSaveable { mutableStateOf(false) }
    var restartDeclined by rememberSaveable { mutableStateOf(false) }
    var downloaded by remember { mutableStateOf<AppUpdateResult.Downloaded?>(null) }

    LaunchedEffect(manager, lifecycleOwner) {
        val updates = runCatching { manager.requestUpdateFlow() }.getOrNull() ?: return@LaunchedEffect
        updates
            .flowWithLifecycle(lifecycleOwner.lifecycle, Lifecycle.State.STARTED)
            .catch { }
            .collect { result ->
                when (result) {
                    is AppUpdateResult.Available -> {
                        if (!offered && result.updateInfo.isFlexibleUpdateAllowed) {
                            offered = true
                            result.startFlexibleUpdate(launcher)
                        }
                    }

                    is AppUpdateResult.Downloaded -> downloaded = result
                    else -> Unit
                }
            }
    }

    val ready = downloaded
    if (ready != null && !restartDeclined) {
        AlertDialog(
            onDismissRequest = { restartDeclined = true },
            title = { Text("Update ready") },
            text = { Text("A new version of KubeNexus has downloaded. Restart to finish installing it.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            try {
                                ready.completeUpdate()
                            } catch (e: Exception) {
                                if (e is CancellationException) throw e
                            }
                        }
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text("Restart now")
                }
            },
            dismissButton = {
                TextButton(onClick = { restartDeclined = true }, shapes = ButtonDefaults.shapes()) {
                    Text("Later")
                }
            },
        )
    }
}
