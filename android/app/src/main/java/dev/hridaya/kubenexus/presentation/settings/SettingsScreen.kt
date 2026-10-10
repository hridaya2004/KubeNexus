package dev.hridaya.kubenexus.presentation.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import dev.hridaya.kubenexus.BuildConfig
import dev.hridaya.kubenexus.R
import dev.hridaya.kubenexus.domain.model.ThemeMode
import dev.hridaya.kubenexus.presentation.common.components.SegmentedListSection
import dev.hridaya.kubenexus.presentation.settings.components.AboutItem
import dev.hridaya.kubenexus.presentation.settings.components.AppThemeModePreferenceWidget
import dev.hridaya.kubenexus.presentation.settings.components.ModuleInfo
import dev.hridaya.kubenexus.presentation.settings.components.ModuleItem
import dev.hridaya.kubenexus.presentation.settings.components.PreferenceNavigationItem
import dev.hridaya.kubenexus.presentation.settings.components.PreferenceSwitchItem
import dev.hridaya.kubenexus.ui.theme.KubeNexusTheme
import dev.hridaya.kubenexus.ui.theme.LocalAmoledDark
import dev.hridaya.kubenexus.ui.theme.LocalOnAmoledDarkChange
import dev.hridaya.kubenexus.ui.theme.LocalOnThemeModeChange
import dev.hridaya.kubenexus.ui.theme.LocalThemeMode
import dev.hridaya.kubenexus.ui.theme.appBarColors
import dev.hridaya.kubenexus.ui.theme.isDark

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
                },
                    shapes = ButtonDefaults.shapes(),
                ) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearCache = false }, shapes = ButtonDefaults.shapes()) { Text("Cancel") }
            },
        )
    }
    val currentThemeMode = LocalThemeMode.current
    val onThemeModeChange = LocalOnThemeModeChange.current
    val amoledDark = LocalAmoledDark.current
    val onAmoledDarkChange = LocalOnAmoledDarkChange.current

    val isDark = currentThemeMode.isDark()

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(
                        text = "Settings",
                    )
                },
                colors = appBarColors(),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item {
                SegmentedListSection(title = "Theme") {
                    AppThemeModePreferenceWidget(
                        value = currentThemeMode,
                        onItemClick = onThemeModeChange,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    PreferenceSwitchItem(
                        icon = Icons.Outlined.Contrast,
                        title = "Pure black",
                        subtitle = "Use true black in dark theme to save battery on OLED screens",
                        checked = amoledDark && isDark,
                        enabled = isDark,
                        onCheckedChange = onAmoledDarkChange,
                        shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
                    )
                }
            }

            item {
                SegmentedListSection(title = "Diagnostics") {
                    PreferenceNavigationItem(
                        icon = Icons.Outlined.BugReport,
                        title = "Logcat",
                        subtitle = "Inspect, filter, and share runtime application logs",
                        onClick = onNavigateToLogcat,
                        shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
                    )
                }
            }

            item {
                SegmentedListSection(title = "Data") {
                    PreferenceNavigationItem(
                        icon = Icons.Outlined.DeleteSweep,
                        title = "Clear cached cluster data",
                        subtitle = "Keeps your clusters and credentials",
                        onClick = { confirmClearCache = true },
                        shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
                    )
                }
            }

            item {
                val modules = listOf(
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
                )
                SegmentedListSection(title = "Modules & Versions") {
                    modules.forEachIndexed { index, module ->
                        ModuleItem(
                            module = module,
                            shapes = ListItemDefaults.segmentedShapes(index = index, count = modules.size),
                        )
                    }
                }
            }

            item {
                SegmentedListSection(title = "About") {
                    AboutItem(
                        versionName = BuildConfig.VERSION_NAME,
                        shapes = ListItemDefaults.segmentedShapes(index = 0, count = 4),
                    )
                    PreferenceNavigationItem(
                        icon = Icons.Outlined.Gavel,
                        title = "Open source licenses",
                        subtitle = "Attribution for bundled and linked components",
                        onClick = onNavigateToOpenSourceLicenses,
                        shapes = ListItemDefaults.segmentedShapes(index = 1, count = 4),
                    )
                    PreferenceNavigationItem(
                        icon = Icons.Outlined.PrivacyTip,
                        title = "Privacy policy",
                        subtitle = "What KubeNexus stores, sends and never collects",
                        onClick = { context.openUrl(PRIVACY_POLICY_URL) },
                        shapes = ListItemDefaults.segmentedShapes(index = 2, count = 4),
                    )
                    PreferenceNavigationItem(
                        painter = painterResource(R.drawable.github),
                        title = "GitHub",
                        onClick = { context.openUrl(SOURCE_CODE_URL) },
                        shapes = ListItemDefaults.segmentedShapes(index = 3, count = 4),
                    )
                }
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
