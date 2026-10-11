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
import dev.hridaya.kubenexus.core.common.log.APP_LOG_TAG_PREFIX
import dev.hridaya.kubenexus.core.common.result.AppError
import dev.hridaya.kubenexus.core.common.result.Result
import dev.hridaya.kubenexus.core.common.util.K8sNames
import dev.hridaya.kubenexus.core.security.LogSanitizer
import dev.hridaya.kubenexus.data.mapper.toDomain
import dev.hridaya.kubenexus.data.portforward.PortForwardListener
import dev.hridaya.kubenexus.data.source.remote.dto.K8sJson
import dev.hridaya.kubenexus.data.source.remote.dto.NamespaceListDto
import dev.hridaya.kubenexus.data.source.remote.dto.PodMetricsDto
import dev.hridaya.kubenexus.data.source.remote.dto.PodMetricsListDto
import dev.hridaya.kubenexus.data.source.remote.dto.toSample
import dev.hridaya.kubenexus.domain.model.APIResource
import dev.hridaya.kubenexus.domain.model.ClusterHealth
import dev.hridaya.kubenexus.domain.model.Namespace
import dev.hridaya.kubenexus.domain.model.PodMetricSample
import dev.hridaya.kubenexus.domain.model.resource.ResourceType
import go.Seq
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
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
        private const val TAG = "$APP_LOG_TAG_PREFIX/KubeNexusNativeBridge"

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

    @Volatile
    private var initialized = false
    private val initLock = Any()

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

    // Gomobile factory calls allocate a Go-side proxy each time, so each
    // resource identifier is created once and reused.
    private val namespacesResource: GroupVersionResource by lazy { Client.namespacesResource() }
    private val resourceIdentifiers = ConcurrentHashMap<ResourceType, GroupVersionResource>()

    private fun identifierOf(type: ResourceType): GroupVersionResource =
        resourceIdentifiers.getOrPut(type) {
            Client.newGroupVersionResource(type.group, type.version, type.plural)
        }

    override fun initialize() {
        synchronized(initLock) {
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
        if (initialized) return
        synchronized(initLock) {
            if (!initialized) initialize()
        }
    }

    private inline fun <T> nativeCatching(errorMsg: String, block: () -> T): Result<T> {
        return try {
            ensureInitialized()
            Result.Success(block())
        } catch (t: Throwable) {
            Log.e(TAG, LogSanitizer.withStackTrace("$errorMsg: ${LogSanitizer.sanitize(t.message)}", t))
            Result.Error(AppError.Unknown(LogSanitizer.sanitize(t.message).ifEmpty { errorMsg }, t))
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

    override fun forgetClient(rawKubeconfig: String) {
        if (rawKubeconfig.isBlank()) return
        synchronized(clientCache) { clientCache.remove(digest(rawKubeconfig)) }
    }

    private fun digest(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte) }

    private fun listOptions(labelSelector: String): ListOptions =
        ListOptions().apply {
            if (labelSelector.isNotBlank()) this.labelSelector = labelSelector
        }

    override fun listResources(
        rawKubeconfig: String,
        type: ResourceType,
        namespace: String,
        labelSelector: String,
    ): Result<String> =
        nativeCatching("Failed to list ${type.plural} from native client") {
            clientFor(rawKubeconfig).listJSON(identifierOf(type), namespace.trim(), listOptions(labelSelector))
        }

    override fun getResource(
        rawKubeconfig: String,
        type: ResourceType,
        namespace: String,
        name: String,
    ): Result<String> =
        nativeCatching("Failed to get ${type.kind} '$name' from native client") {
            clientFor(rawKubeconfig).getJSON(identifierOf(type), namespace, name)
        }

    override fun createResource(
        rawKubeconfig: String,
        type: ResourceType,
        manifest: String,
    ): Result<String> =
        nativeCatching("Failed to create ${type.kind} from native client") {
            // An empty namespace tells the Go core to keep the manifest's own.
            clientFor(rawKubeconfig).createResource(identifierOf(type), "", manifest)
        }

    override fun deleteResource(
        rawKubeconfig: String,
        type: ResourceType,
        namespace: String,
        name: String,
    ): Result<Unit> =
        nativeCatching("Failed to delete ${type.kind} '$name' from native client") {
            clientFor(rawKubeconfig).deleteResource(identifierOf(type), namespace, name, null)
        }

    override fun listEvents(
        rawKubeconfig: String,
        namespace: String,
        involvedKind: String,
        involvedName: String,
    ): Result<String> =
        nativeCatching("Failed to list events for $involvedKind '$involvedName' from native client") {
            clientFor(rawKubeconfig).eventsForJSON(namespace, involvedKind, involvedName)
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
