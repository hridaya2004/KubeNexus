package dev.hridaya.kubenexus.data.nativebridge

import client.ExecCallback
import client.ExecResult
import client.ExecSession
import client.LogCallback
import dev.hridaya.kubenexus.core.common.result.Result
import dev.hridaya.kubenexus.domain.model.APIResource
import dev.hridaya.kubenexus.domain.model.ClusterHealth
import dev.hridaya.kubenexus.domain.model.Namespace
import dev.hridaya.kubenexus.domain.model.PodMetricSample
import dev.hridaya.kubenexus.domain.model.PortForwardListener
import dev.hridaya.kubenexus.domain.model.resource.ResourceType

/**
 * A running native log stream started by [KubeNexusNativeBridge.streamPodLogs].
 * [cancel] closes the underlying HTTP stream and is safe to call more than once.
 */
fun interface LogStreamHandle {
    fun cancel()
}

/**
 * Bridge interface defining the contract for interacting with the native Go
 * runtime provided by kubenexus.aar.
 *
 * Resources cross the boundary as verbatim Kubernetes JSON through the generic
 * [listResources] / [getResource] / [createResource] / [deleteResource] calls,
 * keyed by [ResourceType], so supporting a new kind needs no new bridge method
 * and no `client.*` type appears in a resource signature.
 * The remaining `client.*` references are the streaming primitives — log and exec
 * callbacks and the exec session handle — which are genuinely not expressible as
 * a request/response pair and therefore stay purpose-built Gomobile bindings.
 */
interface KubeNexusNativeBridge {
    /**
     * Initializes the Go Mobile Seq context and loads native client bindings.
     */
    fun initialize()

    /**
     * Returns true if the native runtime and library were successfully loaded and initialized.
     */
    fun isAvailable(): Boolean

    /**
     * Touches the native package to trigger runtime static initialization.
     */
    fun touch(): Boolean

    /**
     * Lists objects of [type] and returns the verbatim Kubernetes list as JSON.
     *
     * A blank [namespace] lists across all namespaces, or the whole cluster for
     * cluster-scoped types. [labelSelector] maps onto the list option of the same
     * name, e.g. "app=web"; blank lists everything.
     */
    fun listResources(
        rawKubeconfig: String,
        type: ResourceType,
        namespace: String,
        labelSelector: String = "",
    ): Result<String>

    /** Fetches one object of [type] and returns it verbatim as JSON. */
    fun getResource(
        rawKubeconfig: String,
        type: ResourceType,
        namespace: String,
        name: String,
    ): Result<String>

    /**
     * Creates the single object described by [manifest] (YAML or JSON) in the
     * namespace its own metadata names, and returns the created object as JSON.
     */
    fun createResource(
        rawKubeconfig: String,
        type: ResourceType,
        manifest: String,
    ): Result<String>

    /** Deletes one object of [type], leaving propagation to the server default. */
    fun deleteResource(
        rawKubeconfig: String,
        type: ResourceType,
        namespace: String,
        name: String,
    ): Result<Unit>

    /**
     * Lists the events whose involved object is the [involvedKind] named
     * [involvedName] in [namespace], as verbatim Kubernetes list JSON.
     */
    fun listEvents(
        rawKubeconfig: String,
        namespace: String,
        involvedKind: String,
        involvedName: String,
    ): Result<String>

    /**
     * Retrieves existing cluster namespaces from native runtime.
     */
    fun listNamespaces(rawKubeconfig: String): Result<List<Namespace>>

    /**
     * Creates an empty namespace with [name]. The name must be a valid
     * DNS-1123 label; callers validate UX-side, the API server has final say.
     */
    fun createNamespace(rawKubeconfig: String, name: String): Result<Unit>

    /**
     * Deletes a namespace from native runtime.
     */
    fun deleteNamespace(rawKubeconfig: String, namespace: String): Result<Unit>

    /**
     * Retrieves all discovered Kubernetes API resources from native runtime.
     */
    fun listAPIResources(rawKubeconfig: String): Result<List<APIResource>>

    /**
     * Fetches metrics.k8s.io usage for pods in [namespace], or across all
     * namespaces when null/blank. One sample per pod, container usage summed.
     *
     * Prefer [topPod] when only one pod is of interest; this transfers usage for
     * every pod in the namespace.
     */
    fun topPods(rawKubeconfig: String, namespace: String?): Result<List<PodMetricSample>>

    /**
     * Fetches metrics.k8s.io usage for a single pod, with container usage
     * summed. Returns null on success when the pod has no usable sample, which
     * is distinct from the request itself failing.
     */
    fun topPod(
        rawKubeconfig: String,
        namespace: String,
        podName: String,
    ): Result<PodMetricSample?>

    /**
     * Fetches the cluster's OpenAPI v2 schema document verbatim. The document
     * is large; callers are expected to persist it rather than refetch.
     */
    fun openAPISchemaJSON(rawKubeconfig: String): Result<String>

    /**
     * Scales an apps/v1 Deployment to [replicas] count.
     */
    fun scaleDeployment(
        rawKubeconfig: String,
        namespace: String,
        name: String,
        replicas: Int,
    ): Result<Unit>

    /**
     * Triggers a rolling restart for an apps/v1 Deployment by updating the restartedAt annotation.
     */
    fun restartDeployment(
        rawKubeconfig: String,
        namespace: String,
        name: String,
    ): Result<Unit>

    /**
     * Fetches historical logs for a pod container with optional tail line limit.
     */
    fun getPodLogs(
        rawKubeconfig: String,
        namespace: String,
        podName: String,
        container: String? = null,
        tailLines: Long? = null,
    ): Result<String>

    /**
     * Starts following live logs for a pod container and returns immediately.
     *
     * Lines, errors and completion arrive on [callback] from a native thread, with
     * `onDone` always last. The stream has no overall timeout; it runs until the
     * container stops, the connection fails, or [LogStreamHandle.cancel] is called.
     */
    fun streamPodLogs(
        rawKubeconfig: String,
        namespace: String,
        podName: String,
        container: String? = null,
        tailLines: Long? = null,
        callback: LogCallback,
    ): Result<LogStreamHandle>

    /**
     * Executes a non-interactive command inside a container and captures stdout and stderr.
     */
    fun exec(
        rawKubeconfig: String,
        namespace: String,
        podName: String,
        container: String,
        command: String,
        stdin: String = "",
    ): Result<ExecResult>

    /**
     * Starts an interactive terminal shell session attached to the container.
     */
    fun startTerminal(
        rawKubeconfig: String,
        namespace: String,
        podName: String,
        container: String,
        callback: ExecCallback,
    ): Result<ExecSession>

    /**
     * Starts an interactive exec session for a specific command with TTY support.
     */
    fun startExecSession(
        rawKubeconfig: String,
        namespace: String,
        podName: String,
        container: String,
        command: String,
        tty: Boolean,
        callback: ExecCallback,
    ): Result<ExecSession>

    /**
     * Opens a port-forward tunnel localhost:[localPort] -> pod [remotePort] and
     * returns its opaque handle id.
     *
     * Readiness is asynchronous: the Go core dials the pod in the background and
     * reports progress through [listener]. Ports are bound as int32 on the Go
     * side, which maps 1:1 onto Kotlin Int.
     */
    fun startPortForward(
        rawKubeconfig: String,
        namespace: String,
        podName: String,
        localPort: Int,
        remotePort: Int,
        listener: PortForwardListener,
    ): Result<String>

    /**
     * Closes the tunnel behind [handleId]. Stopping an unknown or already
     * closed handle succeeds rather than erroring, so callers can stop
     * unconditionally during cleanup.
     *
     * Handle-based because a forward outlives any single client instance;
     * the registry of live tunnels lives process-wide on the Go side, so this
     * is a package-level call rather than a per-client method.
     */
    fun stopPortForward(handleId: String): Result<Unit>

    /**
     * Pings the Kubernetes cluster verifying connectivity and health endpoints (/readyz, /livez, /healthz, /version).
     */
    fun ping(rawKubeconfig: String): Result<String>

    /**
     * Performs a liveness check against the /livez endpoint.
     */
    fun checkLivez(rawKubeconfig: String): Result<Boolean>

    /**
     * Performs a readiness check against the /readyz endpoint.
     */
    fun checkReadyz(rawKubeconfig: String): Result<Boolean>

    /**
     * Performs a legacy health check against the /healthz endpoint.
     */
    fun checkHealthz(rawKubeconfig: String): Result<Boolean>

    /**
     * Fetches the Kubernetes server version.
     */
    fun serverVersion(rawKubeconfig: String): Result<String>

    /**
     * Returns detailed cluster health inspection data.
     */
    fun checkHealth(rawKubeconfig: String): Result<ClusterHealth>
}
