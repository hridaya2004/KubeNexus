package dev.hridaya.kubenexus.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import dev.hridaya.kubenexus.domain.model.ThemeMode

val LocalThemeMode = compositionLocalOf { ThemeMode.SYSTEM }
val LocalOnThemeModeChange = compositionLocalOf<(ThemeMode) -> Unit> { {} }
val LocalAmoledDark = compositionLocalOf { false }
val LocalOnAmoledDarkChange = compositionLocalOf<(Boolean) -> Unit> { {} }

@Composable
@ReadOnlyComposable
fun ThemeMode.isDark(): Boolean {
    val systemInDark = isSystemInDarkTheme()
    return when (this) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> systemInDark
    }
}

@Composable
fun KubeNexusTheme(
    themeMode: ThemeMode = LocalThemeMode.current,
    amoledDark: Boolean = LocalAmoledDark.current,
    content: @Composable () -> Unit,
) {
    val isDark = themeMode.isDark()
    val context = LocalContext.current

    val baseColorScheme = remember(isDark, context) {
        if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }

    val colorScheme = remember(isDark, amoledDark, baseColorScheme) {
        if (isDark && amoledDark) {
            baseColorScheme.copy(
                background = Color.Black,
                surface = Color.Black,
                surfaceContainer = Color(0xFF121212),
                surfaceContainerLow = Color(0xFF0A0A0A),
                surfaceContainerLowest = Color.Black,
                surfaceContainerHigh = Color(0xFF1E1E1E),
                surfaceContainerHighest = Color(0xFF2C2C2C),
                surfaceVariant = Color(0xFF1E1E1E),
            )
        } else {
            baseColorScheme
        }
    }

    val statusColors = remember(isDark) {
        if (isDark) DarkStatusColors else LightStatusColors
    }

    val logColors = remember(isDark) {
        if (isDark) DarkLogLevelColors else LightLogLevelColors
    }

    CompositionLocalProvider(
        LocalStatusColors provides statusColors,
        LocalLogLevelColors provides logColors,
    ) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
                content = content,
            )
        }
    }
}
