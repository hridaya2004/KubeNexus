package dev.hridaya.kubenexus.presentation.pods.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogFollowTest {

    @Test
    fun `a streamed line does not pull a reader back to the newest line`() {
        assertFalse(resumesFollowing(wasStreaming = true, isStreaming = true, logsChanged = true))
    }

    @Test
    fun `starting a stream follows the newest line`() {
        assertTrue(resumesFollowing(wasStreaming = false, isStreaming = true, logsChanged = false))
    }

    @Test
    fun `fetched logs are shown from the newest line`() {
        assertTrue(resumesFollowing(wasStreaming = false, isStreaming = false, logsChanged = true))
    }

    @Test
    fun `stopping a stream leaves the reader where they are`() {
        assertFalse(resumesFollowing(wasStreaming = true, isStreaming = false, logsChanged = false))
        assertFalse(resumesFollowing(wasStreaming = true, isStreaming = false, logsChanged = true))
    }
}
