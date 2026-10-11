package dev.hridaya.kubenexus.data.repository

import dev.hridaya.kubenexus.core.common.result.Result
import dev.hridaya.kubenexus.data.nativebridge.KubeNexusNativeBridge
import dev.hridaya.kubenexus.data.portforward.PortForwardListener
import dev.hridaya.kubenexus.data.portforward.PortForwardRepository
import javax.inject.Inject

/**
 * Thin pass-through to the native bridge.
 *
 * Deliberately stateless: the Go core owns the tunnel registry, so there is
 * nothing to cache or clean up on the Kotlin side. The repository exists to
 * keep presentation depending on a domain-owned contract rather than on
 * `client.*` binding details.
 */
class PortForwardRepositoryImpl @Inject constructor(
    private val nativeBridge: KubeNexusNativeBridge,
) : PortForwardRepository {

    override fun start(
        rawKubeconfig: String,
        namespace: String,
        podName: String,
        localPort: Int,
        remotePort: Int,
        listener: PortForwardListener,
    ): Result<String> = nativeBridge.startPortForward(
        rawKubeconfig = rawKubeconfig,
        namespace = namespace,
        podName = podName,
        localPort = localPort,
        remotePort = remotePort,
        listener = listener,
    )

    override fun stop(handleId: String): Result<Unit> = nativeBridge.stopPortForward(handleId)
}
