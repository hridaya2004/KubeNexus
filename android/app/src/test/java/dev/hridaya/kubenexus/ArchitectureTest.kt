package dev.hridaya.kubenexus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Keeps the app module's layering honest. The domain and core modules are plain Kotlin, so the
 * compiler already stops them depending on Android or the data layer; the app module has to
 * depend on :data for dependency injection, so its imports are checked here instead.
 */
class ArchitectureTest {

    // Unit tests run with the module directory as the working directory.
    private val sources = File("src/main/java/dev/hridaya/kubenexus")

    /** The composition root, which starts the data layer and so may name it. */
    private val compositionRoot = setOf("KubeNexusApp.kt")

    @Test
    fun `the UI never reaches into the data layer`() {
        assertEquals(emptyList<String>(), importsStartingWith("dev.hridaya.kubenexus.data."))
    }

    @Test
    fun `the UI goes through use cases instead of repositories`() {
        assertEquals(
            emptyList<String>(),
            importsStartingWith("dev.hridaya.kubenexus.domain.repository.")
        )
    }

    private fun importsStartingWith(prefix: String): List<String> {
        assertTrue("app sources not found at ${sources.absolutePath}", sources.isDirectory)
        return sources.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name !in compositionRoot }
            .flatMap { file ->
                file.useLines { lines ->
                    lines.filter { it.startsWith("import $prefix") }
                        .map { "${file.relativeTo(sources)}: ${it.removePrefix("import ")}" }
                        .toList()
                }
            }
            .toList()
    }
}
