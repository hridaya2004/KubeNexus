package dev.hridaya.kubenexus.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable

/**
 * The screens open over the top-level destinations, last on top. Empty means a
 * top-level destination is showing.
 *
 * Replaces one saved flag (plus name and namespace) per screen: a stack keeps
 * every screen's way back without each screen knowing what opened it.
 */
@Stable
class BackStack(initial: List<Screen> = emptyList()) {

    private val entries = mutableStateListOf<Screen>().apply { addAll(initial) }

    val screens: List<Screen>
        get() = entries

    val current: Screen?
        get() = entries.lastOrNull()

    fun push(screen: Screen) {
        entries += screen
    }

    fun pop() {
        entries.removeLastOrNull()
    }

    fun clear() {
        entries.clear()
    }

    companion object {
        val Saver: Saver<BackStack, Any> = listSaver(
            save = { stack -> stack.entries.map(ScreenSaver::save) },
            restore = { saved -> BackStack(saved.mapNotNull(ScreenSaver::restore)) },
        )
    }
}

@Composable
fun rememberBackStack(): BackStack = rememberSaveable(saver = BackStack.Saver) { BackStack() }
