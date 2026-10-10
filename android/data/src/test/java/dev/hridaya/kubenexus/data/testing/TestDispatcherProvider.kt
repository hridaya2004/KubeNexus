package dev.hridaya.kubenexus.data.testing

import dev.hridaya.kubenexus.core.common.dispatcher.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher

/** Runs every dispatcher on [dispatcher], normally the test's own. */
class TestDispatcherProvider(dispatcher: CoroutineDispatcher) : DispatcherProvider {
    override val main: CoroutineDispatcher = dispatcher
    override val io: CoroutineDispatcher = dispatcher
    override val default: CoroutineDispatcher = dispatcher
    override val unconfined: CoroutineDispatcher = dispatcher
}
