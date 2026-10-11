package dev.hridaya.kubenexus.data.portforward

import dev.hridaya.kubenexus.core.common.result.Result

/**
 * Lifecycle owner for port-forwards into a pod.
 *
 * Takes the raw kubeconfig rather than a clusterId because forwards are
 * short-lived interactive sessions: the caller already holds the decrypted
 * kubeconfig context of the screen that started the forward, and a handle-based
 * stop must work even after the cluster row changes underneath it.
 */
interface PortForwardRepository {

    /**
     * Opens a tunnel localhost:[localPort] -> pod [remotePort] and returns its
     * opaque handle id on success. Readiness is asynchronous — the tunnel is
     * usable only once [PortForwardListener.onPortForwardReady] fires.
     *
     * The same handle id is passed to every subsequent listener callback and to
     * [stop], so callers can disambiguate concurrent forwards.
     */
    fun start(
        rawKubeconfig: String,
        namespace: String,
        podName: String,
        localPort: Int,
        remotePort: Int,
        listener: PortForwardListener,
    ): Result<String>

    /** Closes the tunnel behind [handleId]; stopping an unknown handle still succeeds. */
    fun stop(handleId: String): Result<Unit>
}
