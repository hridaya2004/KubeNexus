package dev.hridaya.kubenexus

import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import dev.hridaya.kubenexus.domain.repository.ThemePreferencesRepository
import dev.hridaya.kubenexus.presentation.home.HomeViewModel
import dev.hridaya.kubenexus.presentation.main.MainScreen
import dev.hridaya.kubenexus.presentation.update.InAppUpdate
import dev.hridaya.kubenexus.ui.theme.KubeNexusTheme
import dev.hridaya.kubenexus.ui.theme.LocalAmoledDark
import dev.hridaya.kubenexus.ui.theme.LocalOnAmoledDarkChange
import dev.hridaya.kubenexus.ui.theme.LocalOnThemeModeChange
import dev.hridaya.kubenexus.ui.theme.LocalThemeMode
import dev.hridaya.kubenexus.ui.theme.applyToSystem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var themePreferencesRepository: ThemePreferencesRepository

    private val viewModel: HomeViewModel by viewModels()

    @Volatile
    private var themeLoaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.isNavigationBarContrastEnforced = false
        keepSystemSplashWhileLoading()

        val themeRepo = themePreferencesRepository
        // Android remembers the app's theme once told, but a choice saved before it was ever
        // told still has to be handed over.
        lifecycleScope.launch { themeRepo.getThemeModeStream().first().applyToSystem(this@MainActivity) }

        setContent {
            val loadedThemeMode by themeRepo.getThemeModeStream()
                .collectAsStateWithLifecycle(initialValue = null)
            val loadedAmoledDark by themeRepo.getAmoledDarkStream()
                .collectAsStateWithLifecycle(initialValue = null)
            val storedThemeMode = loadedThemeMode ?: return@setContent
            val storedAmoledDark = loadedAmoledDark ?: return@setContent
            SideEffect { themeLoaded = true }

            // A new choice applies straight away and is saved in the background, rather than
            // waiting for the preferences write to finish before the screen changes.
            var themeMode by rememberSaveable(storedThemeMode) { mutableStateOf(storedThemeMode) }
            var amoledDark by rememberSaveable(storedAmoledDark) { mutableStateOf(storedAmoledDark) }

            CompositionLocalProvider(
                LocalThemeMode provides themeMode,
                LocalOnThemeModeChange provides { newMode ->
                    themeMode = newMode
                    newMode.applyToSystem(this@MainActivity)
                    lifecycleScope.launch { themeRepo.setThemeMode(newMode) }
                },
                LocalAmoledDark provides amoledDark,
                LocalOnAmoledDarkChange provides { newAmoled ->
                    amoledDark = newAmoled
                    lifecycleScope.launch { themeRepo.setAmoledDark(newAmoled) }
                },
            ) {
                KubeNexusTheme(
                    themeMode = themeMode,
                    amoledDark = amoledDark,
                ) {
                    MainScreen(homeViewModel = viewModel)
                    InAppUpdate()
                }
            }
        }
    }

    /**
     * Keeps the system splash screen (the launcher icon on the window background, shown by
     * Android 12+) up until the first Home state is ready, instead of following it with a
     * second, in-app splash. Capped, so a stuck load can never hold the app on the splash.
     */
    private fun keepSystemSplashWhileLoading() {
        val startedAt = SystemClock.uptimeMillis()
        val content: View = findViewById(android.R.id.content)
        content.viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    val ready = (themeLoaded && !viewModel.uiState.value.isLoading) ||
                        SystemClock.uptimeMillis() - startedAt > MAX_SPLASH_MS
                    if (ready) content.viewTreeObserver.removeOnPreDrawListener(this)
                    return ready
                }
            },
        )
    }

    private companion object {
        const val MAX_SPLASH_MS = 1_500L
    }
}
