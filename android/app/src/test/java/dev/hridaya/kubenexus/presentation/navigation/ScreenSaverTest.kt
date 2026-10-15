package dev.hridaya.kubenexus.presentation.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScreenSaverTest {

    @Test
    fun `every screen survives saving and restoring`() {
        val screens = listOf(
            Screen.ResourceList(typeKey = "apps/v1/deployments"),
            Screen.ResourceDetail(
                typeKey = "v1/pods",
                namespace = "kube-system",
                name = "coredns-7db6d8ff4d-x2v9k"
            ),
            Screen.CreateResource(typeKey = "v1/services"),
            Screen.ManageClusters,
            Screen.Logcat,
            Screen.OpenSourceLicenses,
        )

        assertEquals(screens, screens.map { ScreenSaver.restore(ScreenSaver.save(it)) })
    }

    @Test
    fun `an unknown saved entry is dropped rather than crashing`() {
        assertNull(ScreenSaver.restore("removed-screen|x"))
    }

    @Test
    fun `store keys include the cluster so screens never cross clusters`() {
        val detail = Screen.ResourceDetail(typeKey = "v1/pods", namespace = "web", name = "web-1")

        assertEquals("detail/c-1/v1/pods/web/web-1", detail.storeKey("c-1"))
        assertNull(Screen.ManageClusters.storeKey("c-1"))
    }
}
