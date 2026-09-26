package dev.hridaya.kubenexus.presentation.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.CreationExtras
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.reflect.KClass

class ScreenViewModelStoresTest {

    private class TrackingViewModel : ViewModel() {
        var cleared = false
        public override fun onCleared() {
            cleared = true
        }
    }

    private val factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: KClass<T>, extras: CreationExtras): T =
            TrackingViewModel() as T
    }

    private fun viewModelIn(store: ViewModelStore): TrackingViewModel =
        ViewModelProvider.create(store, factory)[TrackingViewModel::class]

    @Test
    fun `a screen gets the same ViewModel back while it stays on the back stack`() {
        val stores = ScreenViewModelStores()
        val first = viewModelIn(stores.storeFor("pod/cluster-a/default/web-0"))

        stores.retainOnly(setOf("pod/cluster-a/default/web-0", "deployment/cluster-a/default/web"))

        assertSame(first, viewModelIn(stores.storeFor("pod/cluster-a/default/web-0")))
        assertFalse(first.cleared)
    }

    @Test
    fun `leaving a screen clears its ViewModels and the next visit starts fresh`() {
        val stores = ScreenViewModelStores()
        val first = viewModelIn(stores.storeFor("pod/cluster-a/default/web-0"))

        stores.retainOnly(emptySet())

        assertTrue("onCleared must run so sessions and native resources are released", first.cleared)
        assertTrue(stores.keys.isEmpty())
        assertNotSame(first, viewModelIn(stores.storeFor("pod/cluster-a/default/web-0")))
    }

    // Keys carry the cluster, so the same object name on another cluster is another screen.
    @Test
    fun `the same object on another cluster never shares a ViewModel`() {
        val stores = ScreenViewModelStores()
        val onA = viewModelIn(stores.storeFor("create-deployment/cluster-a"))

        stores.retainOnly(setOf("create-deployment/cluster-b"))
        val onB = viewModelIn(stores.storeFor("create-deployment/cluster-b"))

        assertTrue(onA.cleared)
        assertNotSame(onA, onB)
        assertEquals(setOf("create-deployment/cluster-b"), stores.keys)
    }

    @Test
    fun `clearing the holder clears every screen`() {
        val stores = ScreenViewModelStores()
        val a = viewModelIn(stores.storeFor("logcat"))
        val b = viewModelIn(stores.storeFor("services/cluster-a"))

        ViewModelStore().apply { put("holder", stores) }.clear()

        assertTrue(a.cleared)
        assertTrue(b.cleared)
    }
}
