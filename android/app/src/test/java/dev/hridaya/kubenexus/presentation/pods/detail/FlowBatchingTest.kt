package dev.hridaya.kubenexus.presentation.pods.detail

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FlowBatchingTest {

    @Test
    fun `items arriving together are delivered as one group`() = runTest {
        val groups = flow {
            repeat(5) { emit(it) }
            delay(1_000)
            emit(5)
        }.batched(100).toList()

        assertEquals(listOf(listOf(0, 1, 2, 3, 4), listOf(5)), groups)
    }

    @Test
    fun `nothing is lost when the stream ends inside a window`() = runTest {
        val groups = flow { (1..1_000).forEach { emit(it) } }.batched(100).toList()

        assertEquals((1..1_000).toList(), groups.flatten())
    }

    @Test
    fun `a failure arrives after the lines gathered before it`() = runTest {
        val seen = mutableListOf<Any>()
        flow {
            emit("a")
            emit("b")
            throw IllegalStateException("stream closed")
        }.batched(100)
            .catch { seen += it.message.orEmpty() }
            .toList(seen)

        assertEquals(listOf(listOf("a", "b"), "stream closed"), seen)
    }
}
