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
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import dev.hridaya.kubenexus.domain.model.ThemeMode
import dev.hridaya.kubenexus.domain.repository.ThemePreferencesRepository
import dev.hridaya.kubenexus.presentation.home.HomeViewModel
import dev.hridaya.kubenexus.presentation.main.MainScreen
import dev.hridaya.kubenexus.ui.theme.KubeNexusTheme
import dev.hridaya.kubenexus.ui.theme.LocalAmoledDark
import dev.hridaya.kubenexus.ui.theme.LocalOnAmoledDarkChange
import dev.hridaya.kubenexus.ui.theme.LocalOnThemeModeChange
import dev.hridaya.kubenexus.ui.theme.LocalThemeMode
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var themePreferencesRepository: ThemePreferencesRepository

    private val viewModel: HomeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.isNavigationBarContrastEnforced = false
        keepSystemSplashWhileLoading()

        val themeRepo = themePreferencesRepository

        setContent {
            val themeMode by themeRepo.getThemeModeStream()
                .collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
            val amoledDark by themeRepo.getAmoledDarkStream()
                .collectAsStateWithLifecycle(initialValue = false)

            CompositionLocalProvider(
                LocalThemeMode provides themeMode,
                LocalOnThemeModeChange provides { newMode ->
                    lifecycleScope.launch { themeRepo.setThemeMode(newMode) }
                },
                LocalAmoledDark provides amoledDark,
                LocalOnAmoledDarkChange provides { newAmoled ->
                    lifecycleScope.launch { themeRepo.setAmoledDark(newAmoled) }
                },
            ) {
                KubeNexusTheme(
                    themeMode = themeMode,
                    amoledDark = amoledDark,
                ) {
                    MainScreen(homeViewModel = viewModel)
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
                    val ready = !viewModel.uiState.value.isLoading ||
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
