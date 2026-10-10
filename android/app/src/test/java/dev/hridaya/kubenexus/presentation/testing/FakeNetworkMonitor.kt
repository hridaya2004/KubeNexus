package dev.hridaya.kubenexus.presentation.testing

import dev.hridaya.kubenexus.core.common.network.NetworkMonitor
import kotlinx.coroutines.flow.MutableStateFlow

class FakeNetworkMonitor(online: Boolean = true) : NetworkMonitor {
    override val isOnline = MutableStateFlow(online)
}
