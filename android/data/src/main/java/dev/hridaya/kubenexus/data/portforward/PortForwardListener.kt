package dev.hridaya.kubenexus.data.portforward

/**
 * Progress callbacks for a port-forward tunnel.
 *
 * Every method receives [handleId] so one listener can
 * serve several concurrent forwards without closing over per-call state.
 *
 * Callbacks are invoked on worker threads; implementors must hop to their
 * own dispatcher before touching UI state.
 */
interface PortForwardListener {
    /**
     * The tunnel accepts connections on localhost at [localPort]. Until this
     * fires, binding succeeded but the upstream pod connection may not exist.
     */
    fun onPortForwardReady(handleId: String, localPort: Int)

    /** The tunnel failed after start (dial error, pod gone, port busy). */
    fun onPortForwardError(handleId: String, message: String)

    /** The tunnel closed; [reason] distinguishes user stop from remote close. */
    fun onPortForwardStopped(handleId: String, reason: String)
}
