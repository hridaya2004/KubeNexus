package dev.hridaya.kubenexus.data.repository

import android.util.Log
import client.LogCallback
import dev.hridaya.kubenexus.core.common.dispatcher.DispatcherProvider
import dev.hridaya.kubenexus.core.common.result.AppError
import dev.hridaya.kubenexus.core.common.result.Result
import dev.hridaya.kubenexus.core.security.KubeconfigEncryptor
import dev.hridaya.kubenexus.core.security.LogSanitizer
import dev.hridaya.kubenexus.core.security.NoOpKubeconfigEncryptor
import dev.hridaya.kubenexus.data.mapper.toDomain
import dev.hridaya.kubenexus.data.mapper.toDomainName
import dev.hridaya.kubenexus.data.mapper.toEntity
import dev.hridaya.kubenexus.data.nativebridge.KubeNexusNativeBridge
import dev.hridaya.kubenexus.data.source.local.dao.ClusterDao
import dev.hridaya.kubenexus.data.source.local.dao.NamespaceDao
import dev.hridaya.kubenexus.data.source.local.dao.PodDao
import dev.hridaya.kubenexus.domain.model.CommandExecResult
import dev.hridaya.kubenexus.domain.model.Namespace
import dev.hridaya.kubenexus.domain.model.Pod
import dev.hridaya.kubenexus.domain.model.PodDetails
import dev.hridaya.kubenexus.domain.model.PodMetricSample
import dev.hridaya.kubenexus.domain.model.TerminalSession
import dev.hridaya.kubenexus.domain.repository.PodRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

class NativeTerminalSession(private val session: client.ExecSession) : TerminalSession {
    override fun write(input: String) {
        session.write(input)
    }

    override fun writeBytes(bytes: ByteArray) {
        session.writeBytes(bytes)
    }

    override fun resize(columns: Int, rows: Int) {
        session.resize(columns, rows)
    }

    override fun close() {
        session.close()
    }
}

class PodRepositoryImpl @Inject constructor(
    private val clusterDao: ClusterDao,
    private val podDao: PodDao,
    private val namespaceDao: NamespaceDao,
    private val nativeBridge: KubeNexusNativeBridge,
    private val encryptor: KubeconfigEncryptor = NoOpKubeconfigEncryptor,
    private val dispatcherProvider: DispatcherProvider,
) : PodRepository {

    companion object {
        private const val TAG = "PodRepositoryImpl"
    }

    override fun getPodsStream(clusterId: String?, namespace: String?): Flow<List<Pod>> {
        if (clusterId == null) return flowOf(emptyList())

        val stream =
            if (namespace.isNullOrBlank() ||
                namespace == "All Namespaces" ||
                namespace.equals(
                    "all",
                    ignoreCase = true,
                )
            ) {
                podDao.getPodsStream(clusterId)
            } else {
                podDao.getPodsByNamespaceStream(clusterId, namespace.trim())
            }

        return stream.map { entities ->
            entities.map { it.toDomain() }
        }.flowOn(dispatcherProvider.io)
    }

    override fun getNamespacesStream(clusterId: String?): Flow<List<String>> {
        if (clusterId == null) return flowOf(listOf("All Namespaces"))

        return namespaceDao.getNamespacesStream(clusterId).map { entities ->
            val nsList =
                entities.map { it.toDomainName() }.filter { it.isNotBlank() }.distinct().sorted()
            if (nsList.isNotEmpty()) {
                listOf("All Namespaces") + nsList
            } else {
                listOf("All Namespaces", "default", "kube-system")
            }
        }.flowOn(dispatcherProvider.io)
    }

    override fun getLastRefreshedStream(clusterId: String?): Flow<Long?> {
        if (clusterId == null) return flowOf(null)
        return podDao.getSyncMetadataStream("${clusterId}_pods").flowOn(dispatcherProvider.io)
    }

    override suspend fun listPodsBySelector(
        rawKubeconfig: String,
        namespace: String?,
        labelSelector: String,
    ): Result<List<Pod>> = withContext(dispatcherProvider.io) {
        try {
            nativeBridge.listPods(
                rawKubeconfig = rawKubeconfig,
                namespace = namespace,
                labelSelector = labelSelector,
            )
        } catch (t: Throwable) {
            val sanitizedMsg = LogSanitizer.sanitize(t.message)
            Log.e(TAG, LogSanitizer.withStackTrace("Failed to list pods by selector: $sanitizedMsg", t))
            Result.Error(AppError.Network(sanitizedMsg.ifEmpty { "Failed to list pods by selector" }))
        }
    }

    override suspend fun getPodsBySelector(
        clusterId: String?,
        namespace: String?,
        labelSelector: String,
    ): Result<List<Pod>> = withContext(dispatcherProvider.io) {
        if (clusterId == null) return@withContext Result.Error(AppError.NotFound("No cluster selected"))
        val cluster = clusterDao.getClusterById(clusterId)
            ?: return@withContext Result.Error(AppError.NotFound("Cluster '$clusterId' not found"))

        val decryptedKubeconfig = encryptor.decryptOrEmpty(cluster.rawKubeconfig)
        listPodsBySelector(decryptedKubeconfig, namespace, labelSelector)
    }

    override suspend fun refreshWorkloads(clusterId: String?, namespace: String?): Result<Unit> {
        return withContext(dispatcherProvider.io) {
            if (clusterId == null) {
                return@withContext Result.Error(AppError.NotFound("No active cluster specified"))
            }

            val cluster = clusterDao.getClusterById(clusterId)
                ?: return@withContext Result.Error(AppError.NotFound("Cluster with ID '$clusterId' not found"))

            val decryptedKubeconfig = encryptor.decryptOrEmpty(cluster.rawKubeconfig)
            val queryNamespace =
                if (namespace.isNullOrBlank() ||
                    namespace == "All Namespaces" ||
                    namespace.equals(
                        "all",
                        ignoreCase = true,
                    )
                ) {
                    null
                } else {
                    namespace.trim()
                }

            try {
                val nativeResult = nativeBridge.listPods(decryptedKubeconfig, queryNamespace)
                if (nativeResult.isFailure) {
                    val ex = nativeResult.exceptionOrNull()
                    val sanitizedMsg = LogSanitizer.sanitize(ex?.message)
                    return@withContext Result.Error(
                        AppError.Network(sanitizedMsg.ifEmpty { "Failed to list pods from cluster" }),
                    )
                }
                val livePods: List<Pod> = nativeResult.getOrThrow()

                val nativeNsResult = nativeBridge.listNamespaces(decryptedKubeconfig)
                val liveNamespaces: List<Namespace> = if (nativeNsResult.isSuccess) {
                    nativeNsResult.getOrThrow()
                } else {
                    emptyList()
                }

                podDao.syncPods(
                    clusterId = clusterId,
                    namespace = queryNamespace,
                    pods = livePods.map { it.toEntity(clusterId) },
                    timestamp = System.currentTimeMillis(),
                )

                if (liveNamespaces.isNotEmpty()) {
                    val namespaceEntities = liveNamespaces
                        .filter { it.name != "All Namespaces" && it.name.isNotBlank() }
                        .map { it.toEntity(clusterId) }
                    namespaceDao.syncNamespaces(clusterId, namespaceEntities)
                }

                Result.Success(Unit)
            } catch (t: Throwable) {
                val sanitizedMsg = LogSanitizer.sanitize(t.message)
                Log.e(TAG, LogSanitizer.withStackTrace("Failed to refresh workloads for cluster '$clusterId': $sanitizedMsg", t))
                Result.Error(AppError.Network(sanitizedMsg.ifEmpty { "Failed to connect to cluster API" }))
            }
        }
    }

    override suspend fun describePod(
        clusterId: String?,
        namespace: String,
        podName: String
    ): Result<PodDetails> = withContext(dispatcherProvider.io) {
        if (clusterId == null) return@withContext Result.Error(AppError.NotFound("No cluster selected"))
        val cluster = clusterDao.getClusterById(clusterId)
            ?: return@withContext Result.Error(AppError.NotFound("Cluster '$clusterId' not found"))

        val decryptedKubeconfig = encryptor.decryptOrEmpty(cluster.rawKubeconfig)

        try {
            val nativeResult = nativeBridge.describePod(decryptedKubeconfig, namespace, podName)
            if (nativeResult.isSuccess) {
                Result.Success(nativeResult.getOrThrow())
            } else {
                val ex = nativeResult.exceptionOrNull()
                val sanitizedMsg = LogSanitizer.sanitize(ex?.message)
                Result.Error(AppError.Network(sanitizedMsg.ifEmpty { "Failed to describe pod '$podName'" }))
            }
        } catch (t: Throwable) {
            val sanitizedMsg = LogSanitizer.sanitize(t.message)
            Log.e(TAG, LogSanitizer.withStackTrace("Failed to describe pod '$podName': $sanitizedMsg", t))
            Result.Error(AppError.Network(sanitizedMsg.ifEmpty { "Failed to describe pod from cluster API" }))
        }
    }

    override suspend fun getPodMetrics(
        clusterId: String?,
        namespace: String?
    ): Result<List<PodMetricSample>> = withContext(dispatcherProvider.io) {
        if (clusterId == null) return@withContext Result.Error(AppError.NotFound("No cluster selected"))
        val cluster = clusterDao.getClusterById(clusterId)
            ?: return@withContext Result.Error(AppError.NotFound("Cluster '$clusterId' not found"))

        val decryptedKubeconfig = encryptor.decryptOrEmpty(cluster.rawKubeconfig)

        try {
            val nativeResult = nativeBridge.topPods(decryptedKubeconfig, namespace)
            if (nativeResult.isSuccess) {
                Result.Success(nativeResult.getOrThrow())
            } else {
                val ex = nativeResult.exceptionOrNull()
                val sanitizedMsg = LogSanitizer.sanitize(ex?.message)
                Result.Error(AppError.Network(sanitizedMsg.ifEmpty { "Failed to fetch pod metrics" }))
            }
        } catch (t: Throwable) {
            val sanitizedMsg = LogSanitizer.sanitize(t.message)
            Log.e(TAG, LogSanitizer.withStackTrace("Failed to fetch pod metrics: $sanitizedMsg", t))
            Result.Error(AppError.Network(sanitizedMsg.ifEmpty { "Failed to fetch pod metrics" }))
        }
    }

    override suspend fun getSinglePodMetrics(
        clusterId: String?,
        namespace: String,
        podName: String,
    ): Result<PodMetricSample?> = withContext(dispatcherProvider.io) {
        if (clusterId == null) return@withContext Result.Error(AppError.NotFound("No cluster selected"))
        val cluster = clusterDao.getClusterById(clusterId)
            ?: return@withContext Result.Error(AppError.NotFound("Cluster '$clusterId' not found"))

        val decryptedKubeconfig = encryptor.decryptOrEmpty(cluster.rawKubeconfig)

        try {
            val nativeResult = nativeBridge.topPod(decryptedKubeconfig, namespace, podName)
            if (nativeResult.isSuccess) {
                Result.Success(nativeResult.getOrThrow())
            } else {
                val ex = nativeResult.exceptionOrNull()
                val sanitizedMsg = LogSanitizer.sanitize(ex?.message)
                Result.Error(AppError.Network(sanitizedMsg.ifEmpty { "Failed to fetch metrics for pod '$podName'" }))
            }
        } catch (t: Throwable) {
            val sanitizedMsg = LogSanitizer.sanitize(t.message)
            Log.e(TAG, LogSanitizer.withStackTrace("Failed to fetch metrics for pod '$podName': $sanitizedMsg", t))
            Result.Error(AppError.Network(sanitizedMsg.ifEmpty { "Failed to fetch pod metrics" }))
        }
    }

    override suspend fun deletePod(
        clusterId: String?,
        namespace: String,
        podName: String
    ): Result<Unit> = withContext(dispatcherProvider.io) {
        if (clusterId == null) return@withContext Result.Error(AppError.NotFound("No cluster selected"))
        val cluster = clusterDao.getClusterById(clusterId)
            ?: return@withContext Result.Error(AppError.NotFound("Cluster '$clusterId' not found"))

        val decryptedKubeconfig = encryptor.decryptOrEmpty(cluster.rawKubeconfig)

        try {
            val nativeResult = nativeBridge.deletePod(decryptedKubeconfig, namespace, podName)
            if (nativeResult.isSuccess) {
                val podId = "${clusterId}_${namespace}_$podName"
                podDao.deletePod(podId)
                Result.Success(Unit)
            } else {
                val error = nativeResult.exceptionOrNull()
                val sanitizedMsg = LogSanitizer.sanitize(error?.message)
                Result.Error(AppError.Network(sanitizedMsg.ifEmpty { "Failed to delete pod $podName" }))
            }
        } catch (t: Throwable) {
            val sanitizedMsg = LogSanitizer.sanitize(t.message)
            Log.e(TAG, LogSanitizer.withStackTrace("Failed to delete pod '$podName': $sanitizedMsg", t))
            Result.Error(AppError.Network(sanitizedMsg.ifEmpty { "Failed to delete pod" }))
        }
    }

    override suspend fun deleteNamespace(
        clusterId: String?,
        namespace: String
    ): Result<Unit> = withContext(dispatcherProvider.io) {
        if (clusterId == null) return@withContext Result.Error(AppError.NotFound("No cluster selected"))
        val cluster = clusterDao.getClusterById(clusterId)
            ?: return@withContext Result.Error(AppError.NotFound("Cluster '$clusterId' not found"))

        val decryptedKubeconfig = encryptor.decryptOrEmpty(cluster.rawKubeconfig)

        try {
            val nativeResult = nativeBridge.deleteNamespace(decryptedKubeconfig, namespace)
            if (nativeResult.isSuccess) {
                namespaceDao.deleteNamespace(clusterId, namespace)
                podDao.deletePodsForNamespace(clusterId, namespace)
                Result.Success(Unit)
            } else {
                val error = nativeResult.exceptionOrNull()
                val sanitizedMsg = LogSanitizer.sanitize(error?.message)
                Result.Error(AppError.Network(sanitizedMsg.ifEmpty { "Failed to delete namespace $namespace" }))
            }
        } catch (t: Throwable) {
            val sanitizedMsg = LogSanitizer.sanitize(t.message)
            Log.e(TAG, LogSanitizer.withStackTrace("Failed to delete namespace '$namespace': $sanitizedMsg", t))
            Result.Error(AppError.Network(sanitizedMsg.ifEmpty { "Failed to delete namespace" }))
        }
    }

    override suspend fun createNamespace(
        clusterId: String?,
        name: String,
    ): Result<Unit> = withContext(dispatcherProvider.io) {
        if (clusterId == null) return@withContext Result.Error(AppError.NotFound("No cluster selected"))
        val cluster = clusterDao.getClusterById(clusterId)
            ?: return@withContext Result.Error(AppError.NotFound("Cluster '$clusterId' not found"))

        val decryptedKubeconfig = encryptor.decryptOrEmpty(cluster.rawKubeconfig)

        try {
            val nativeResult = nativeBridge.createNamespace(decryptedKubeconfig, name)
            if (nativeResult.isSuccess) {
                Result.Success(Unit)
            } else {
                val error = nativeResult.exceptionOrNull()
                val sanitizedMsg = LogSanitizer.sanitize(error?.message)
                Result.Error(AppError.Network(sanitizedMsg.ifEmpty { "Failed to create namespace $name" }))
            }
        } catch (t: Throwable) {
            val sanitizedMsg = LogSanitizer.sanitize(t.message)
            Log.e(TAG, LogSanitizer.withStackTrace("Failed to create namespace '$name': $sanitizedMsg", t))
            Result.Error(AppError.Network(sanitizedMsg.ifEmpty { "Failed to create namespace '$name'" }))
        }
    }

    override suspend fun createPodFromManifest(
        clusterId: String?,
        manifestYaml: String,
    ): Result<Unit> = withContext(dispatcherProvider.io) {
        if (clusterId == null) return@withContext Result.Error(AppError.NotFound("No cluster selected"))
        val cluster = clusterDao.getClusterById(clusterId)
            ?: return@withContext Result.Error(AppError.NotFound("Cluster '$clusterId' not found"))

        val decryptedKubeconfig = encryptor.decryptOrEmpty(cluster.rawKubeconfig)

        try {
            // The reviewed manifest carries its own namespace, so the bridge is
            // told to fall back to it rather than overriding the user's choice.
            val nativeResult = nativeBridge.createPod(decryptedKubeconfig, "", manifestYaml)
            if (nativeResult.isSuccess) {
                Result.Success(Unit)
            } else {
                val error = nativeResult.exceptionOrNull()
                val sanitizedMsg = LogSanitizer.sanitize(error?.message)
                Log.e(TAG, "Failed to create pod for cluster '$clusterId': $sanitizedMsg")
                Result.Error(AppError.Network(sanitizedMsg.ifEmpty { "Failed to create pod" }))
            }
        } catch (t: Throwable) {
            val sanitizedMsg = LogSanitizer.sanitize(t.message)
            Log.e(TAG, LogSanitizer.withStackTrace("Failed to create pod for cluster '$clusterId': $sanitizedMsg", t))
            Result.Error(AppError.Network(sanitizedMsg.ifEmpty { "Failed to create pod" }))
        }
    }

    override suspend fun getPodLogs(
        clusterId: String?,
        namespace: String,
        podName: String,
        containerName: String?,
        tailLines: Long?,
    ): Result<String> = withContext(dispatcherProvider.io) {
        if (clusterId == null) return@withContext Result.Error(AppError.NotFound("No cluster selected"))
        val cluster = clusterDao.getClusterById(clusterId)
            ?: return@withContext Result.Error(AppError.NotFound("Cluster '$clusterId' not found"))

        val decryptedKubeconfig = encryptor.decryptOrEmpty(cluster.rawKubeconfig)

        try {
            val nativeResult =
                nativeBridge.getPodLogs(
                    decryptedKubeconfig,
                    namespace,
                    podName,
                    containerName,
                    tailLines
                )
            if (nativeResult.isSuccess) {
                Result.Success(nativeResult.getOrThrow())
            } else {
                val ex = nativeResult.exceptionOrNull()
                val sanitizedMsg = LogSanitizer.sanitize(ex?.message)
                Result.Error(AppError.Network(sanitizedMsg.ifEmpty { "Failed to fetch logs for pod '$podName'" }))
            }
        } catch (t: Throwable) {
            val sanitizedMsg = LogSanitizer.sanitize(t.message)
            Log.e(TAG, LogSanitizer.withStackTrace("Failed to fetch logs for pod '$podName': $sanitizedMsg", t))
            Result.Error(AppError.Network(sanitizedMsg.ifEmpty { "Failed to fetch logs from cluster API" }))
        }
    }

    override fun streamPodLogs(
        clusterId: String?,
        namespace: String,
        podName: String,
        containerName: String?,
        tailLines: Long?,
    ): Flow<String> = callbackFlow {
        if (clusterId == null) {
            trySend("Error: No active cluster selected")
            close()
            return@callbackFlow
        }
        val cluster = clusterDao.getClusterById(clusterId)
        if (cluster == null) {
            trySend("Error: Cluster '$clusterId' not found in database")
            close()
            return@callbackFlow
        }

        val decryptedKubeconfig = encryptor.decryptOrEmpty(cluster.rawKubeconfig)

        // Callbacks arrive on a native thread. trySendBlocking applies backpressure to
        // the Go reader instead of dropping lines once the channel buffer is full, and
        // fails fast once the flow is cancelled and the channel closed.
        val logCallback = object : LogCallback {
            override fun onLogLine(line: String) {
                trySendBlocking(line)
            }

            override fun onError(err: String) {
                trySendBlocking("[Log error] ${LogSanitizer.sanitize(err)}")
            }

            override fun onDone() {
                close()
            }
        }

        when (
            val nativeResult = nativeBridge.streamPodLogs(
                rawKubeconfig = decryptedKubeconfig,
                namespace = namespace,
                podName = podName,
                container = containerName,
                tailLines = tailLines,
                callback = logCallback,
            )
        ) {
            is Result.Success -> {
                val stream = nativeResult.data
                awaitClose { stream.cancel() }
            }

            is Result.Error -> {
                trySend("[Stream error] ${LogSanitizer.sanitize(nativeResult.error.message)}")
                close()
            }

            Result.Loading -> close()
        }
    }.flowOn(dispatcherProvider.io)

    override suspend fun execCommand(
        clusterId: String?,
        namespace: String,
        podName: String,
        containerName: String,
        command: String,
        stdin: String,
    ): Result<CommandExecResult> = withContext(dispatcherProvider.io) {
        if (clusterId == null) return@withContext Result.Error(AppError.NotFound("No cluster selected"))
        val cluster = clusterDao.getClusterById(clusterId)
            ?: return@withContext Result.Error(AppError.NotFound("Cluster '$clusterId' not found"))

        val decryptedKubeconfig = encryptor.decryptOrEmpty(cluster.rawKubeconfig)

        try {
            val result = nativeBridge.exec(
                rawKubeconfig = decryptedKubeconfig,
                namespace = namespace,
                podName = podName,
                container = containerName,
                command = command,
                stdin = stdin,
            )
            if (result.isSuccess) {
                val nativeRes = result.getOrThrow()
                Result.Success(
                    CommandExecResult(
                        stdout = nativeRes.stdout.orEmpty(),
                        stderr = nativeRes.stderr.orEmpty(),
                        exitCode = nativeRes.exitCode,
                    ),
                )
            } else {
                val ex = result.exceptionOrNull()
                val sanitizedMsg = LogSanitizer.sanitize(ex?.message)
                Result.Error(AppError.Network(sanitizedMsg.ifEmpty { "Failed to exec command" }))
            }
        } catch (t: Throwable) {
            val sanitizedMsg = LogSanitizer.sanitize(t.message)
            Log.e(TAG, LogSanitizer.withStackTrace("Exec command error on pod '$podName': $sanitizedMsg", t))
            Result.Error(AppError.Network(sanitizedMsg.ifEmpty { "Failed to exec command" }))
        }
    }

    override suspend fun startTerminalSession(
        clusterId: String?,
        namespace: String,
        podName: String,
        containerName: String,
        onStdout: (String) -> Unit,
        onStderr: (String) -> Unit,
        onError: (String) -> Unit,
        onDone: () -> Unit,
    ): Result<TerminalSession> = withContext(dispatcherProvider.io) {
        if (clusterId == null) return@withContext Result.Error(AppError.NotFound("No cluster selected"))
        val cluster = clusterDao.getClusterById(clusterId)
            ?: return@withContext Result.Error(AppError.NotFound("Cluster '$clusterId' not found"))

        val decryptedKubeconfig = encryptor.decryptOrEmpty(cluster.rawKubeconfig)

        try {
            val callback = object : client.ExecCallback {
                override fun onStdout(output: String) {
                    onStdout(output)
                }

                override fun onStderr(output: String) {
                    onStderr(output)
                }

                override fun onError(err: String) {
                    onError(LogSanitizer.sanitize(err))
                }

                override fun onDone() {
                    onDone()
                }
            }

            val result = nativeBridge.startTerminal(
                rawKubeconfig = decryptedKubeconfig,
                namespace = namespace,
                podName = podName,
                container = containerName,
                callback = callback,
            )
            if (result.isSuccess) {
                Result.Success(NativeTerminalSession(result.getOrThrow()))
            } else {
                val ex = result.exceptionOrNull()
                val sanitizedMsg = LogSanitizer.sanitize(ex?.message)
                Result.Error(AppError.Network(sanitizedMsg.ifEmpty { "Failed to start terminal session" }))
            }
        } catch (t: Throwable) {
            val sanitizedMsg = LogSanitizer.sanitize(t.message)
            Log.e(TAG, LogSanitizer.withStackTrace("Start terminal error on pod '$podName': $sanitizedMsg", t))
            Result.Error(AppError.Network(sanitizedMsg.ifEmpty { "Failed to start terminal session" }))
        }
    }

    override suspend fun startExecSession(
        clusterId: String?,
        namespace: String,
        podName: String,
        containerName: String,
        command: String,
        tty: Boolean,
        onStdout: (String) -> Unit,
        onStderr: (String) -> Unit,
        onError: (String) -> Unit,
        onDone: () -> Unit,
    ): Result<TerminalSession> = withContext(dispatcherProvider.io) {
        if (clusterId == null) return@withContext Result.Error(AppError.NotFound("No cluster selected"))
        val cluster = clusterDao.getClusterById(clusterId)
            ?: return@withContext Result.Error(AppError.NotFound("Cluster '$clusterId' not found"))

        val decryptedKubeconfig = encryptor.decryptOrEmpty(cluster.rawKubeconfig)

        try {
            val callback = object : client.ExecCallback {
                override fun onStdout(output: String) {
                    onStdout(output)
                }

                override fun onStderr(output: String) {
                    onStderr(output)
                }

                override fun onError(err: String) {
                    onError(LogSanitizer.sanitize(err))
                }

                override fun onDone() {
                    onDone()
                }
            }

            val result = nativeBridge.startExecSession(
                rawKubeconfig = decryptedKubeconfig,
                namespace = namespace,
                podName = podName,
                container = containerName,
                command = command,
                tty = tty,
                callback = callback,
            )
            if (result.isSuccess) {
                Result.Success(NativeTerminalSession(result.getOrThrow()))
            } else {
                val ex = result.exceptionOrNull()
                val sanitizedMsg = LogSanitizer.sanitize(ex?.message)
                Result.Error(AppError.Network(sanitizedMsg.ifEmpty { "Failed to start exec session" }))
            }
        } catch (t: Throwable) {
            val sanitizedMsg = LogSanitizer.sanitize(t.message)
            Log.e(TAG, LogSanitizer.withStackTrace("Start exec session error on pod '$podName': $sanitizedMsg", t))
            Result.Error(AppError.Network(sanitizedMsg.ifEmpty { "Failed to start exec session" }))
        }
    }
}
