package dev.hridaya.kubenexus.data.nativebridge

import android.content.Context
import android.util.Log
import client.Client
import client.Client_
import client.ExecCallback
import client.ExecResult
import client.ExecSession
import client.GroupVersionResource
import client.ListOptions
import client.LogCallback
import client.PortForwardCallback
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.hridaya.kubenexus.core.common.result.AppError
import dev.hridaya.kubenexus.core.common.result.Result
import dev.hridaya.kubenexus.core.common.util.K8sNames
import dev.hridaya.kubenexus.core.security.LogSanitizer
import dev.hridaya.kubenexus.data.mapper.toDeploymentDetails
import dev.hridaya.kubenexus.data.mapper.toDetails
import dev.hridaya.kubenexus.data.mapper.toDomain
import dev.hridaya.kubenexus.data.mapper.toServiceDetails
import dev.hridaya.kubenexus.data.mapper.toServiceSummary
import dev.hridaya.kubenexus.data.source.remote.dto.DeploymentDto
import dev.hridaya.kubenexus.data.source.remote.dto.DeploymentListDto
import dev.hridaya.kubenexus.data.source.remote.dto.EventListDto
import dev.hridaya.kubenexus.data.source.remote.dto.K8sJson
import dev.hridaya.kubenexus.data.source.remote.dto.NamespaceListDto
import dev.hridaya.kubenexus.data.source.remote.dto.PodDto
import dev.hridaya.kubenexus.data.source.remote.dto.PodListDto
import dev.hridaya.kubenexus.data.source.remote.dto.PodMetricsDto
import dev.hridaya.kubenexus.data.source.remote.dto.PodMetricsListDto
import dev.hridaya.kubenexus.data.source.remote.dto.ServiceDto
import dev.hridaya.kubenexus.data.source.remote.dto.ServiceListDto
import dev.hridaya.kubenexus.data.source.remote.dto.toDomain
import dev.hridaya.kubenexus.data.source.remote.dto.toSample
import dev.hridaya.kubenexus.domain.model.APIResource
import dev.hridaya.kubenexus.domain.model.ClusterHealth
import dev.hridaya.kubenexus.domain.model.DeploymentDetails
import dev.hridaya.kubenexus.domain.model.DeploymentSummary
import dev.hridaya.kubenexus.domain.model.Namespace
import dev.hridaya.kubenexus.domain.model.Pod
import dev.hridaya.kubenexus.domain.model.PodDetails
import dev.hridaya.kubenexus.domain.model.PodMetricSample
import dev.hridaya.kubenexus.domain.model.PortForwardListener
import dev.hridaya.kubenexus.domain.model.ServiceDetails
import dev.hridaya.kubenexus.domain.model.ServiceSummary
import go.Seq
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Concrete implementation of [KubeNexusNativeBridge] that bridges calls to the Go
 * Mobile runtime and kubenexus.aar native client library.
 *
 * Resource access goes through the Go core's generic `listJSON` / `getJSON` /
 * `deleteResource` methods and is decoded here with kotlinx.serialization. That
 * replaces the previous per-resource flattened structs, whose indexed
 * `len()`/`get(i)` traversal cost two JNI crossings and one Go proxy handle per
 * element; a JSON payload costs one crossing regardless of size.
 */
@Singleton
class KubeNexusNativeBridgeImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val jsonParser: NativeBridgeJsonParser = NativeBridgeJsonParser(),
) : KubeNexusNativeBridge {

    companion object {
        private const val TAG = "KubeNexusNativeBridge"

        /**
         * Number of Go clients kept alive. Each holds a parsed kubeconfig, a TLS
         * configuration and an HTTP connection pool, so reuse matters; a handful
         * covers realistic multi-cluster use without pinning memory.
         */
        private const val CLIENT_CACHE_SIZE = 4

        const val CREDENTIALS_UNAVAILABLE_MESSAGE =
            "This cluster's saved credentials can no longer be read on this device. " +
                "Delete the cluster and add it again."
    }

    private var initialized = false

    /**
     * Most recently started port-forward client. Tunnels live in a process-wide
     * Go registry, so any bound instance can stop them by handle; remembering
     * one here spares every layer above from threading kubeconfig back down.
     */
    private val lastPortForwardClient = AtomicReference<Client_?>(null)

    /**
     * Cached Go clients keyed by a digest of the kubeconfig.
     *
     * Every bridge method used to call `Client.newClient(rawKubeconfig)`, which
     * re-parsed the kubeconfig, rebuilt the TLS configuration and created a fresh
     * connection pool on every single list, describe and delete. That cost far
     * more than the shape of the binding ever did.
     *
     * The key is a digest rather than the kubeconfig itself so cluster
     * credentials are not retained as map keys. A rotated kubeconfig hashes
     * differently and therefore builds a new client, as it must.
     */
    private val clientCache = object : LinkedHashMap<String, Client_>(
        /* initialCapacity = */ CLIENT_CACHE_SIZE,
        /* loadFactor = */ 0.75f,
        /* accessOrder = */ true,
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Client_>?): Boolean =
            size > CLIENT_CACHE_SIZE
    }

    // Gomobile factory calls allocate a Go-side proxy each time, so the
    // identifiers for the resources this app touches are created once.
    private val podsResource: GroupVersionResource by lazy { Client.podsResource() }
    private val namespacesResource: GroupVersionResource by lazy { Client.namespacesResource() }

    // No dedicated Go factory exists for deployments, so the identifier is
    // built through the bound NewGroupVersionResource constructor.
    private val deploymentsResource: GroupVersionResource by lazy {
        Client.newGroupVersionResource("apps", "v1", "deployments")
    }

    // Services are core v1 with no dedicated Go factory either; an empty group
    // is valid there and means the core API group.
    private val servicesResource: GroupVersionResource by lazy {
        Client.newGroupVersionResource("", "v1", "services")
    }

    override fun initialize() {
        try {
            Seq.setContext(context)
            Client.touch()
            initialized = true
            Log.d(TAG, "Successfully initialized Go runtime Seq context and Client package")
        } catch (t: Throwable) {
            initialized = false
            Log.e(TAG, LogSanitizer.withStackTrace("Failed to initialize native Go runtime", t))
        }
    }

    override fun isAvailable(): Boolean = initialized

    override fun touch(): Boolean {
        return try {
            Client.touch()
            true
        } catch (t: Throwable) {
            Log.e(TAG, LogSanitizer.withStackTrace("Failed to touch native Client: ${LogSanitizer.sanitize(t.message)}", t))
            false
        }
    }

    private fun ensureInitialized() {
        if (!initialized) {
            initialize()
        }
    }

    private inline fun <T> nativeCatching(errorMsg: String, block: () -> T): Result<T> {
        return try {
            ensureInitialized()
            Result.Success(block())
        } catch (t: Throwable) {
            Log.e(TAG, LogSanitizer.withStackTrace("$errorMsg: ${LogSanitizer.sanitize(t.message)}", t))
            Result.Error(AppError.Unknown(t.message ?: errorMsg, t))
        }
    }

    /**
     * Returns a cached Go client for this kubeconfig, creating one if needed.
     * Synchronised because bridge methods are called from repository coroutines
     * on the IO dispatcher and [LinkedHashMap] is not thread safe.
     */
    private fun clientFor(rawKubeconfig: String): Client_ = synchronized(clientCache) {
        // Repositories pass an empty kubeconfig when the stored one cannot be decrypted.
        check(rawKubeconfig.isNotBlank()) { CREDENTIALS_UNAVAILABLE_MESSAGE }
        val key = digest(rawKubeconfig)
        clientCache.getOrPut(key) { Client.newClient(rawKubeconfig) }
    }

    private fun digest(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte) }

    private fun listOptions(labelSelector: String, limit: Long): ListOptions =
        ListOptions().apply {
            if (labelSelector.isNotBlank()) this.labelSelector = labelSelector
            if (limit > 0L) this.limit = limit
        }

    override fun listPods(
        rawKubeconfig: String,
        namespace: String?,
        labelSelector: String,
        limit: Long,
    ): Result<List<Pod>> =
        nativeCatching("Failed to list pods from native client") {
            val json = clientFor(rawKubeconfig).listJSON(
                podsResource,
                normalizeNamespace(namespace),
                listOptions(labelSelector, limit),
            )
            K8sJson.decodeFromString<PodListDto>(json).items.map { it.toDomain() }
        }

    override fun listNamespaces(rawKubeconfig: String): Result<List<Namespace>> =
        nativeCatching("Failed to list namespaces from native client") {
            // Namespaces are cluster scoped, so the namespace argument is empty.
            val json = clientFor(rawKubeconfig).listJSON(namespacesResource, "", ListOptions())
            val now = System.currentTimeMillis()
            K8sJson.decodeFromString<NamespaceListDto>(json).items.map { it.toDomain(now) }
        }

    override fun createNamespace(rawKubeconfig: String, name: String): Result<Unit> {
        // The name is interpolated into a YAML manifest, so the format guard is
        // also injection defense, not just UX validation.
        if (!K8sNames.isValidDnsLabel(name)) {
            return Result.Error(
                AppError.Validation("Namespace name must be a lowercase DNS label of up to 63 characters"),
            )
        }
        val manifestYaml = """
            apiVersion: v1
            kind: Namespace
            metadata:
              name: $name
        """.trimIndent()
        return nativeCatching("Failed to create namespace from native client") {
            clientFor(rawKubeconfig).createResource(namespacesResource, "", manifestYaml)
        }
    }

    override fun deleteNamespace(rawKubeconfig: String, namespace: String): Result<Unit> =
        nativeCatching("Failed to delete namespace '$namespace' from native client") {
            clientFor(rawKubeconfig).deleteResource(namespacesResource, "", namespace, null)
        }

    override fun listAPIResources(rawKubeconfig: String): Result<List<APIResource>> =
        nativeCatching("Failed to listAPIResources from native client") {
            jsonParser.parseAPIResources(clientFor(rawKubeconfig).listAPIResourcesJSON())
        }

    override fun topPods(rawKubeconfig: String, namespace: String?): Result<List<PodMetricSample>> =
        nativeCatching("Failed to fetch pod metrics from native client") {
            val json = clientFor(rawKubeconfig).topPodsJSON(normalizeNamespace(namespace))
            K8sJson.decodeFromString<PodMetricsListDto>(json)
                .items
                .mapNotNull { it.toSample() }
        }

    override fun topPod(
        rawKubeconfig: String,
        namespace: String,
        podName: String,
    ): Result<PodMetricSample?> =
        nativeCatching("Failed to fetch metrics for pod '$podName' from native client") {
            // A named resource returns a single PodMetrics object rather than a
            // PodMetricsList, so this decodes a different shape to topPods.
            val json = clientFor(rawKubeconfig).topPodJSON(namespace, podName)
            K8sJson.decodeFromString<PodMetricsDto>(json).toSample()
        }

    override fun openAPISchemaJSON(rawKubeconfig: String): Result<String> =
        nativeCatching("Failed to fetch OpenAPI schema from native client") {
            clientFor(rawKubeconfig).openAPISchemaJSON()
        }

    override fun describePod(
        rawKubeconfig: String,
        namespace: String,
        podName: String,
    ): Result<PodDetails> =
        nativeCatching("Failed to describePod '$podName' from native client") {
            val nativeClient = clientFor(rawKubeconfig)
            val podJson = nativeClient.getJSON(podsResource, namespace, podName)
            val pod = K8sJson.decodeFromString<PodDto>(podJson)

            // Events are a separate collection with a field selector. A failure
            // here must not lose the pod itself, which is the primary payload.
            val events = try {
                val eventsJson = nativeClient.eventsForJSON(namespace, "Pod", podName)
                K8sJson.decodeFromString<EventListDto>(eventsJson).items
            } catch (t: Throwable) {
                Log.w(
                    TAG,
                    "Failed to load events for pod '$podName': ${LogSanitizer.sanitize(t.message)}",
                )
                emptyList()
            }

            pod.toDetails(events = events, rawJson = podJson)
        }

    override fun deletePod(
        rawKubeconfig: String,
        namespace: String,
        podName: String,
    ): Result<Unit> =
        nativeCatching("Failed to deletePod '$podName' from native client") {
            clientFor(rawKubeconfig).deleteResource(podsResource, namespace, podName, null)
        }

    override fun listDeployments(
        rawKubeconfig: String,
        namespace: String?,
    ): Result<List<DeploymentSummary>> =
        nativeCatching("Failed to list deployments from native client") {
            val json = clientFor(rawKubeconfig).listJSON(
                deploymentsResource,
                normalizeNamespace(namespace),
                null,
            )
            K8sJson.decodeFromString<DeploymentListDto>(json).items.map { it.toDomain() }
        }

    override fun describeDeployment(
        rawKubeconfig: String,
        namespace: String,
        name: String,
    ): Result<DeploymentDetails> =
        nativeCatching("Failed to describeDeployment '$name' from native client") {
            val nativeClient = clientFor(rawKubeconfig)
            val deploymentJson = nativeClient.getJSON(deploymentsResource, namespace, name)
            val deployment = K8sJson.decodeFromString<DeploymentDto>(deploymentJson)

            // Events are a separate collection with a field selector. A failure
            // here must not lose the Deployment itself, which is the primary
            // payload — identical trade-off to describePod.
            val events = try {
                val eventsJson = nativeClient.eventsForJSON(namespace, "Deployment", name)
                K8sJson.decodeFromString<EventListDto>(eventsJson).items
            } catch (t: Throwable) {
                Log.w(
                    TAG,
                    "Failed to load events for deployment '$name': ${LogSanitizer.sanitize(t.message)}",
                )
                emptyList()
            }

            deployment.toDeploymentDetails(events = events)
        }

    override fun listServices(
        rawKubeconfig: String,
        namespace: String?,
    ): Result<List<ServiceSummary>> =
        nativeCatching("Failed to list services from native client") {
            val json = clientFor(rawKubeconfig).listJSON(
                servicesResource,
                normalizeNamespace(namespace),
                null,
            )
            K8sJson.decodeFromString<ServiceListDto>(json).items.map { it.toServiceSummary() }
        }

    override fun describeService(
        rawKubeconfig: String,
        namespace: String,
        name: String,
    ): Result<ServiceDetails> =
        nativeCatching("Failed to describeService '$name' from native client") {
            val nativeClient = clientFor(rawKubeconfig)
            val serviceJson = nativeClient.getJSON(servicesResource, namespace, name)
            val service = K8sJson.decodeFromString<ServiceDto>(serviceJson)

            // Events are a separate collection with a field selector. A failure
            // here must not lose the Service itself, which is the primary
            // payload — identical trade-off to describePod.
            val events = try {
                val eventsJson = nativeClient.eventsForJSON(namespace, "Service", name)
                K8sJson.decodeFromString<EventListDto>(eventsJson).items
            } catch (t: Throwable) {
                Log.w(
                    TAG,
                    "Failed to load events for service '$name': ${LogSanitizer.sanitize(t.message)}",
                )
                emptyList()
            }

            service.toServiceDetails(events = events)
        }

    override fun createDeployment(
        rawKubeconfig: String,
        namespace: String,
        manifestYaml: String,
    ): Result<String> =
        nativeCatching("Failed to create deployment from native client") {
            clientFor(rawKubeconfig).createResource(deploymentsResource, namespace, manifestYaml)
        }

    override fun scaleDeployment(
        rawKubeconfig: String,
        namespace: String,
        name: String,
        replicas: Int,
    ): Result<Unit> =
        nativeCatching("Failed to scale deployment '$name' to $replicas replicas") {
            clientFor(rawKubeconfig).scaleDeployment(namespace, name, replicas.toLong())
        }

    override fun restartDeployment(
        rawKubeconfig: String,
        namespace: String,
        name: String,
    ): Result<Unit> =
        nativeCatching("Failed to restart deployment '$name'") {
            clientFor(rawKubeconfig).restartDeployment(namespace, name)
        }

    override fun deleteDeployment(
        rawKubeconfig: String,
        namespace: String,
        name: String,
    ): Result<Unit> =
        nativeCatching("Failed to delete deployment '$name'") {
            clientFor(rawKubeconfig).deleteDeployment(namespace, name)
        }

    override fun createPod(
        rawKubeconfig: String,
        namespace: String,
        manifestYaml: String,
    ): Result<String> =
        nativeCatching("Failed to create pod from native client") {
            clientFor(rawKubeconfig).createResource(podsResource, namespace, manifestYaml)
        }

    override fun createService(
        rawKubeconfig: String,
        namespace: String,
        manifestYaml: String,
    ): Result<String> =
        nativeCatching("Failed to create service from native client") {
            clientFor(rawKubeconfig).createResource(servicesResource, namespace, manifestYaml)
        }

    override fun getPodLogs(
        rawKubeconfig: String,
        namespace: String,
        podName: String,
        container: String?,
        tailLines: Long?,
    ): Result<String> =
        nativeCatching("Failed to getPodLogs for '$podName' from native client") {
            val nativeClient = clientFor(rawKubeconfig)
            val tail = tailLines ?: 0L
            if (tail > 0L) {
                nativeClient.logsWithTail(namespace, podName, container.orEmpty(), tail)
            } else {
                nativeClient.logs(namespace, podName, container.orEmpty())
            }
        }

    override fun streamPodLogs(
        rawKubeconfig: String,
        namespace: String,
        podName: String,
        container: String?,
        tailLines: Long?,
        callback: LogCallback,
    ): Result<LogStreamHandle> =
        nativeCatching("Failed to streamPodLogs for '$podName' from native client") {
            val stream = clientFor(rawKubeconfig).startLogStream(
                namespace,
                podName,
                container.orEmpty(),
                tailLines ?: 0L,
                callback,
            )
            LogStreamHandle { stream.cancel() }
        }

    override fun exec(
        rawKubeconfig: String,
        namespace: String,
        podName: String,
        container: String,
        command: String,
        stdin: String,
    ): Result<ExecResult> =
        nativeCatching("Failed to exec command in pod '$podName'") {
            clientFor(rawKubeconfig).exec(namespace, podName, container, command, stdin)
        }

    override fun startTerminal(
        rawKubeconfig: String,
        namespace: String,
        podName: String,
        container: String,
        callback: ExecCallback,
    ): Result<ExecSession> =
        nativeCatching("Failed to start terminal session for pod '$podName'") {
            clientFor(rawKubeconfig).startTerminal(namespace, podName, container, callback)
        }

    override fun startExecSession(
        rawKubeconfig: String,
        namespace: String,
        podName: String,
        container: String,
        command: String,
        tty: Boolean,
        callback: ExecCallback,
    ): Result<ExecSession> =
        nativeCatching("Failed to start exec session for pod '$podName'") {
            clientFor(rawKubeconfig)
                .startExecSession(namespace, podName, container, command, tty, callback)
        }

    override fun startPortForward(
        rawKubeconfig: String,
        namespace: String,
        podName: String,
        localPort: Int,
        remotePort: Int,
        listener: PortForwardListener,
    ): Result<String> =
        nativeCatching("Failed to start port-forward for pod '$podName'") {
            // Wired like the exec family: the tunnel hangs off the cached
            // client that holds the parsed kubeconfig and TLS config. Ports go
            // in as Kotlin Int; the Go side declares them int32. The binding
            // takes the kubeconfig explicitly even though the client already
            // carries it, matching the generated Go signature.
            val nativeClient = clientFor(rawKubeconfig)
            // gomobile binds the Go interface as client.PortForwardCallback with
            // camelCase methods; adapt our listener rather than leaking the
            // binding type upward.
            val callback = object : PortForwardCallback {
                override fun portForwardReady(handleID: String?, localPort: Int) {
                    listener.onPortForwardReady(handleID.orEmpty(), localPort)
                }

                override fun portForwardError(handleID: String?, message: String?) {
                    listener.onPortForwardError(handleID.orEmpty(), message.orEmpty())
                }

                override fun portForwardStopped(handleID: String?, reason: String?) {
                    listener.onPortForwardStopped(handleID.orEmpty(), reason.orEmpty())
                }
            }
            nativeClient
                .also { lastPortForwardClient.set(it) }
                .startPortForward(
                    rawKubeconfig,
                    namespace,
                    podName,
                    localPort,
                    remotePort,
                    callback
                )
        }

    override fun stopPortForward(handleId: String): Result<Unit> =
        nativeCatching("Failed to stop port-forward '$handleId'") {
            // Tunnels are registered process-wide on the Go side, so ANY bound
            // client can stop them by handle; remembering the most recent one
            // spares every layer above from threading kubeconfig back down.
            val client = lastPortForwardClient.get()
                ?: return@nativeCatching
            client.stopPortForward(handleId)
        }

    override fun ping(rawKubeconfig: String): Result<String> =
        nativeCatching("Failed to ping cluster") {
            clientFor(rawKubeconfig).ping()
        }

    override fun checkLivez(rawKubeconfig: String): Result<Boolean> =
        nativeCatching("Failed to check /livez") {
            clientFor(rawKubeconfig).checkLivez()
        }

    override fun checkReadyz(rawKubeconfig: String): Result<Boolean> =
        nativeCatching("Failed to check /readyz") {
            clientFor(rawKubeconfig).checkReadyz()
        }

    override fun checkHealthz(rawKubeconfig: String): Result<Boolean> =
        nativeCatching("Failed to check /healthz") {
            clientFor(rawKubeconfig).checkHealthz()
        }

    override fun serverVersion(rawKubeconfig: String): Result<String> =
        nativeCatching("Failed to retrieve server version") {
            clientFor(rawKubeconfig).serverVersion()
        }

    override fun checkHealth(rawKubeconfig: String): Result<ClusterHealth> =
        nativeCatching("Failed to check cluster health") {
            jsonParser.parseClusterHealth(clientFor(rawKubeconfig).checkHealthJSON())
        }

    private fun normalizeNamespace(namespace: String?): String {
        return if (namespace.isNullOrBlank() ||
            namespace == "All Namespaces" ||
            namespace.equals("all", ignoreCase = true)
        ) {
            ""
        } else {
            namespace.trim()
        }
    }
}
