package dev.hridaya.kubenexus.presentation.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import dev.hridaya.kubenexus.BuildConfig
import dev.hridaya.kubenexus.R
import dev.hridaya.kubenexus.domain.model.ThemeMode
import dev.hridaya.kubenexus.presentation.settings.components.AboutCard
import dev.hridaya.kubenexus.presentation.settings.components.AppThemeModePreferenceWidget
import dev.hridaya.kubenexus.presentation.settings.components.ModuleInfo
import dev.hridaya.kubenexus.presentation.settings.components.ModulesCard
import dev.hridaya.kubenexus.presentation.settings.components.PreferenceNavigationCard
import dev.hridaya.kubenexus.presentation.settings.components.PreferenceSwitchCard
import dev.hridaya.kubenexus.ui.theme.KubeNexusTheme
import dev.hridaya.kubenexus.ui.theme.LocalAmoledDark
import dev.hridaya.kubenexus.ui.theme.LocalOnAmoledDarkChange
import dev.hridaya.kubenexus.ui.theme.LocalOnThemeModeChange
import dev.hridaya.kubenexus.ui.theme.LocalThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onNavigateToLogcat: () -> Unit = {},
    onNavigateToOpenSourceLicenses: () -> Unit = {},
    onClearCachedData: () -> Unit = {},
) {
    val context = LocalContext.current
    var confirmClearCache by rememberSaveable { mutableStateOf(false) }

    if (confirmClearCache) {
        AlertDialog(
            onDismissRequest = { confirmClearCache = false },
            title = { Text("Clear cached cluster data?") },
            text = {
                Text(
                    "Deletes the pods, deployments, services, namespaces and API schemas KubeNexus " +
                        "has cached from your clusters. Your clusters and their credentials are " +
                        "kept, and the data is fetched again the next time you open it.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmClearCache = false
                    onClearCachedData()
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearCache = false }) { Text("Cancel") }
            },
        )
    }
    val currentThemeMode = LocalThemeMode.current
    val onThemeModeChange = LocalOnThemeModeChange.current
    val amoledDark = LocalAmoledDark.current
    val onAmoledDarkChange = LocalOnAmoledDarkChange.current

    val systemInDark = isSystemInDarkTheme()
    val isDark = when (currentThemeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> systemInDark
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(
                    text = "Theme",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }

            item {
                AppThemeModePreferenceWidget(
                    value = currentThemeMode,
                    onItemClick = onThemeModeChange,
                )
            }

            item {
                PreferenceSwitchCard(
                    icon = Icons.Outlined.Contrast,
                    title = "Pure black dark mode (AMOLED)",
                    subtitle = "Pitch-black background for dark theme on OLED screens",
                    checked = amoledDark && isDark,
                    enabled = isDark,
                    onCheckedChange = onAmoledDarkChange,
                )
            }

            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Diagnostics",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }

            item {
                PreferenceNavigationCard(
                    icon = Icons.Outlined.BugReport,
                    title = "Logcat",
                    subtitle = "Inspect, filter, and share runtime application logs",
                    onClick = onNavigateToLogcat,
                )
            }

            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Data",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }

            item {
                PreferenceNavigationCard(
                    icon = Icons.Outlined.DeleteSweep,
                    title = "Clear cached cluster data",
                    subtitle = "Keeps your clusters and credentials",
                    onClick = { confirmClearCache = true },
                )
            }

            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Modules & Versions",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }

            item {
                ModulesCard(
                    modules = listOf(
                        ModuleInfo(
                            title = "Android App",
                            subtitle = "dev.hridaya.kubenexus",
                            version = BuildConfig.APP_COMMIT_SHA,
                            icon = Icons.Outlined.Android,
                        ),
                        ModuleInfo(
                            title = "libghostty",
                            subtitle = "ghostty-org/ghostty VT engine",
                            version = BuildConfig.LIBGHOSTTY_COMMIT_SHA,
                            icon = Icons.Outlined.Terminal,
                        ),
                        ModuleInfo(
                            title = "Ghostty Bridge",
                            subtitle = "terminal-native JNI bridge",
                            version = BuildConfig.GHOSTTY_BRIDGE_COMMIT_SHA,
                            icon = Icons.Outlined.Build,
                        ),
                        ModuleInfo(
                            title = "Go-core Client",
                            subtitle = "kubenexus-go-client runtime",
                            version = BuildConfig.GO_CORE_COMMIT_SHA,
                            icon = Icons.Outlined.DataObject,
                        ),
                        ModuleInfo(
                            title = "k8s client-go",
                            subtitle = "k8s.io/client-go ${BuildConfig.CLIENT_GO_VERSION}, unmodified",
                            version = BuildConfig.CLIENT_GO_VERSION,
                            icon = Icons.Outlined.Hub,
                        ),
                    ),
                )
            }

            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "About",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }

            item {
                AboutCard(versionName = BuildConfig.VERSION_NAME)
            }

            item {
                PreferenceNavigationCard(
                    icon = Icons.Outlined.Gavel,
                    title = "Open source licenses",
                    subtitle = "Attribution for bundled and linked components",
                    onClick = onNavigateToOpenSourceLicenses,
                )
            }

            item {
                PreferenceNavigationCard(
                    icon = Icons.Outlined.PrivacyTip,
                    title = "Privacy policy",
                    subtitle = "What KubeNexus stores, sends and never collects",
                    onClick = { context.openUrl(PRIVACY_POLICY_URL) },
                )
            }

            item {
                PreferenceNavigationCard(
                    painter = painterResource(R.drawable.github),
                    title = "GitHub",
                    onClick = { context.openUrl(SOURCE_CODE_URL) },
                )
            }

            item {
                // "Kubernetes" is a registered mark of LF Projects, LLC; KubeNexus is independent.
                Text(
                    text = "KubeNexus is an independent app and is not affiliated with, endorsed " +
                        "by, or sponsored by The Linux Foundation or the Kubernetes® project. " +
                        "Kubernetes® is a registered trademark of LF Projects, LLC.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/** The published policy, the same URL entered in Play Console. */
private const val PRIVACY_POLICY_URL =
    "https://github.com/hridaya2004/kubenexus/blob/main/legal/PRIVACY-POLICY.md"

private const val SOURCE_CODE_URL = "https://github.com/hridaya2004/kubenexus"

/** Opens [url] in a browser, or says so if the device has nothing that can open it. */
private fun Context.openUrl(url: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, "No app on this device can open $url", Toast.LENGTH_LONG).show()
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    KubeNexusTheme {
        SettingsScreen()
    }
}
