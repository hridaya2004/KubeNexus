package dev.hridaya.kubenexus.data.paste

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DpasteLogPasteRepositoryTest {

    @Test
    fun `the destination is the one the user is told about`() {
        val destination = DpasteLogPasteRepository().destination

        assertEquals("dpaste.org", destination.name)
        assertEquals("7 days", destination.retention)
    }

    @Test
    fun `dpaste form body uses dpaste org's expires field in seconds`() {
        val body = DpasteLogPasteRepository.formBody("line one\nline two")

        assertTrue(body.contains("expires=604800"))
        assertFalse("expiry_days is dpaste.com's field and is ignored by dpaste.org", body.contains("expiry_days"))
        assertTrue(body.contains("format=url"))
        assertTrue(body.contains("content=line+one%0Aline+two"))
    }
}
