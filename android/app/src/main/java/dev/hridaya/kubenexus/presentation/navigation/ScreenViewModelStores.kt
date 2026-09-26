package dev.hridaya.kubenexus.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.VIEW_MODEL_STORE_OWNER_KEY
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner

/**
 * One [ViewModelStore] per screen instance, which is what a navigation back stack entry
 * would otherwise provide.
 *
 * MainScreen navigates with saved flags rather than a NavHost, so without this every
 * screen's ViewModels lived in the Activity's store: they were never cleared, kept their
 * sessions and native resources running after Back, and were handed back, stale, the next
 * time a screen with the same key opened, including after switching clusters.
 *
 * A screen is identified by a key that names everything its ViewModels are bound to (the
 * cluster, the namespace, the object). Its store survives configuration changes, because
 * this holder is itself an Activity-scoped ViewModel, and survives the screen leaving
 * composition while it is still on the back stack. It is cleared, running each
 * ViewModel's onCleared, once its key is no longer passed to [retainOnly].
 */
class ScreenViewModelStores : ViewModel() {

    private val stores = mutableMapOf<String, ViewModelStore>()

    fun storeFor(key: String): ViewModelStore = stores.getOrPut(key) { ViewModelStore() }

    /** Clears every screen whose key is not in [liveKeys]. */
    fun retainOnly(liveKeys: Set<String>) {
        val iterator = stores.entries.iterator()
        while (iterator.hasNext()) {
            val (key, store) = iterator.next()
            if (key !in liveKeys) {
                iterator.remove()
                store.clear()
            }
        }
    }

    /** Keys that currently own a store; for tests. */
    internal val keys: Set<String> get() = stores.keys.toSet()

    override fun onCleared() {
        stores.values.forEach { it.clear() }
        stores.clear()
    }
}

/**
 * Provides the store for [key] as [LocalViewModelStoreOwner] to [content], so
 * `hiltViewModel()` calls inside it are scoped to this screen.
 */
@Composable
fun ScreenViewModelScope(
    key: String,
    stores: ScreenViewModelStores,
    content: @Composable () -> Unit,
) {
    val parent = checkNotNull(LocalViewModelStoreOwner.current) {
        "ScreenViewModelScope needs a ViewModelStoreOwner, such as the Activity"
    }
    val owner = remember(key, stores, parent) {
        ScreenViewModelStoreOwner(stores.storeFor(key), parent)
    }
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
        content()
    }
}

/**
 * Delegates factories and creation extras to the Activity, so Hilt ViewModels (including
 * assisted ones) are created exactly as before, only in this screen's store.
 */
private class ScreenViewModelStoreOwner(
    override val viewModelStore: ViewModelStore,
    private val parent: ViewModelStoreOwner,
) : ViewModelStoreOwner, HasDefaultViewModelProviderFactory {

    private val parentDefaults = parent as? HasDefaultViewModelProviderFactory

    override val defaultViewModelProviderFactory: ViewModelProvider.Factory
        get() = checkNotNull(parentDefaults) {
            "The parent ViewModelStoreOwner provides no default factory"
        }.defaultViewModelProviderFactory

    override val defaultViewModelCreationExtras: CreationExtras
        get() = MutableCreationExtras(parentDefaults?.defaultViewModelCreationExtras ?: CreationExtras.Empty).apply {
            // SavedStateHandles for these ViewModels belong to this store, not the Activity's.
            set(VIEW_MODEL_STORE_OWNER_KEY, this@ScreenViewModelStoreOwner)
        }
}
