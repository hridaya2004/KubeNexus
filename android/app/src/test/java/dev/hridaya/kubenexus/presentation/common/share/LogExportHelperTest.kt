package dev.hridaya.kubenexus.presentation.common.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogExportHelperTest {

    @Test
    fun `safeFileName keeps export files flat and named`() {
        assertEquals("nginx-7d9c-abc.log", LogExportHelper.safeFileName("nginx-7d9c-abc.log"))
        assertEquals("etc-passwd.log", LogExportHelper.safeFileName("../../etc/passwd.log"))
        assertEquals("hidden", LogExportHelper.safeFileName(".hidden"))
        assertEquals("logs.log", LogExportHelper.safeFileName("///"))
        assertEquals(120, LogExportHelper.safeFileName("a".repeat(300)).length)
    }

    @Test
    fun `pruneExports deletes only exports older than an hour`() {
        val dir = kotlin.io.path.createTempDirectory("kn-logs").toFile()
        try {
            val now = 10_000_000_000L
            val old = java.io.File(dir, "old.log")
                .apply { writeText("x"); setLastModified(now - 2 * 60 * 60 * 1000L) }
            val recent = java.io.File(dir, "recent.log")
                .apply { writeText("y"); setLastModified(now - 60 * 1000L) }

            LogExportHelper.pruneExports(dir, now)

            assertFalse(old.exists())
            assertTrue(recent.exists())
        } finally {
            dir.deleteRecursively()
        }
    }
}
