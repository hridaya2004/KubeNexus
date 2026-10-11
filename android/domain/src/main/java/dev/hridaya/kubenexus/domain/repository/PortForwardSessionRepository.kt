package dev.hridaya.kubenexus.domain.repository

import dev.hridaya.kubenexus.core.common.result.Result
import dev.hridaya.kubenexus.domain.model.ActivePortForwardSession
import kotlinx.coroutines.flow.StateFlow

/** The app's port-forwards, opened by cluster id so credentials stay in the data layer. */
interface PortForwardSessionRepository {

    val sessions: StateFlow<List<ActivePortForwardSession>>

    suspend fun startPodForward(
        clusterId: String,
        namespace: String,
        podName: String,
        localPort: Int,
        remotePort: Int,
    ): Result<String>

    suspend fun startServiceForward(
        clusterId: String,
        namespace: String,
        serviceName: String,
        localPort: Int,
        servicePort: Int,
        targetPodName: String,
        targetPodPort: Int,
    ): Result<String>

    suspend fun stop(handleId: String): Result<Unit>
}
