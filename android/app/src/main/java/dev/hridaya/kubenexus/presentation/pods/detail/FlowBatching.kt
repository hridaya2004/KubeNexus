package dev.hridaya.kubenexus.presentation.pods.detail

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Groups items that arrive within [windowMs] of the first one in a group into a single list.
 *
 * Nothing is dropped, and when the upstream ends or fails, the group gathered so far is
 * emitted before the completion or failure is passed on.
 */
internal fun <T> Flow<T>.batched(windowMs: Long): Flow<List<T>> = flow {
    coroutineScope {
        // Closed with the failure rather than failing, which would cancel the gathered group.
        val upstream = produce(capacity = Channel.UNLIMITED) {
            try {
                this@batched.collect { send(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                close(e)
            }
        }
        while (true) {
            val first = upstream.receiveCatching()
            if (first.isClosed) {
                first.exceptionOrNull()?.let { throw it }
                break
            }
            val group = mutableListOf(first.getOrThrow())
            delay(windowMs)
            var next = upstream.tryReceive()
            while (next.isSuccess) {
                group += next.getOrThrow()
                next = upstream.tryReceive()
            }
            emit(group)
            if (next.isClosed) {
                next.exceptionOrNull()?.let { throw it }
                break
            }
        }
    }
}
