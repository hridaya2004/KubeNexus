package dev.hridaya.kubenexus.data.source.local

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogcatTagFilterTest {

    @Test
    fun `keeps the app's own Kotlin and native tags`() {
        assertTrue(isKubeNexusLog("KubeNexus/HomeViewModel"))
        assertTrue(isKubeNexusLog("KubeNexus/PodRepositoryImpl"))
        assertTrue(isKubeNexusLog("KubeNexusGhostty"))
    }

    @Test
    fun `keeps the Go engine and the app's crashes`() {
        assertTrue(isKubeNexusLog("GoLog"))
        assertTrue(isKubeNexusLog("AndroidRuntime"))
    }

    @Test
    fun `drops what the framework logs from inside the process`() {
        listOf(
            "ViewRootImpl",
            "OpenGLRenderer",
            "Choreographer",
            "InsetsController",
            "System.err",
            ""
        )
            .forEach { assertFalse(it, isKubeNexusLog(it)) }
    }
}
