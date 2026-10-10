package dev.hridaya.kubenexus.data.repository

import client.ExecCallback
import client.LogCallback
import dev.hridaya.kubenexus.core.common.dispatcher.DispatcherProvider
import dev.hridaya.kubenexus.core.common.result.Result
import dev.hridaya.kubenexus.core.common.result.map
import dev.hridaya.kubenexus.core.security.LogSanitizer
import dev.hridaya.kubenexus.data.nativebridge.KubeNexusNativeBridge
import dev.hridaya.kubenexus.domain.model.CommandExecResult
import dev.hridaya.kubenexus.domain.model.PodMetricSample
import dev.hridaya.kubenexus.domain.model.TerminalSession
import dev.hridaya.kubenexus.domain.repository.PodRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
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
    private val clusterCalls: ClusterCallRunner,
    private val nativeBridge: KubeNexusNativeBridge,
    private val dispatcherProvider: DispatcherProvider,
) : PodRepository {

    override suspend fun getPodMetrics(
        clusterId: String?,
        namespace: String?,
    ): Result<List<PodMetricSample>> =
        clusterCalls.run(clusterId, "Failed to fetch pod metrics") { cluster ->
            nativeBridge.topPods(cluster.kubeconfig, namespace)
        }

    override suspend fun getSinglePodMetrics(
        clusterId: String?,
        namespace: String,
        podName: String,
    ): Result<PodMetricSample?> =
        clusterCalls.run(clusterId, "Failed to fetch metrics for pod '$podName'") { cluster ->
            nativeBridge.topPod(cluster.kubeconfig, namespace, podName)
        }

    override suspend fun getPodLogs(
        clusterId: String?,
        namespace: String,
        podName: String,
        containerName: String?,
        tailLines: Long?,
    ): Result<String> =
        clusterCalls.run(clusterId, "Failed to fetch logs for pod '$podName'") { cluster ->
            nativeBridge.getPodLogs(cluster.kubeconfig, namespace, podName, containerName, tailLines)
        }

    override fun streamPodLogs(
        clusterId: String?,
        namespace: String,
        podName: String,
        containerName: String?,
        tailLines: Long?,
    ): Flow<String> = callbackFlow {
        val cluster = when (val resolved = clusterCalls.resolve(clusterId)) {
            is Result.Success -> resolved.data
            is Result.Error -> {
                trySend("Error: ${resolved.error.message}")
                close()
                return@callbackFlow
            }

            is Result.Loading -> {
                close()
                return@callbackFlow
            }
        }

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
                rawKubeconfig = cluster.kubeconfig,
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
    ): Result<CommandExecResult> =
        clusterCalls.run(clusterId, "Failed to exec command in pod '$podName'") { cluster ->
            nativeBridge.exec(
                rawKubeconfig = cluster.kubeconfig,
                namespace = namespace,
                podName = podName,
                container = containerName,
                command = command,
                stdin = stdin,
            ).map { result ->
                CommandExecResult(
                    stdout = result.stdout.orEmpty(),
                    stderr = result.stderr.orEmpty(),
                    exitCode = result.exitCode,
                )
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
    ): Result<TerminalSession> =
        clusterCalls.run(clusterId, "Failed to start terminal session in pod '$podName'") { cluster ->
            nativeBridge.startTerminal(
                rawKubeconfig = cluster.kubeconfig,
                namespace = namespace,
                podName = podName,
                container = containerName,
                callback = execCallback(onStdout, onStderr, onError, onDone),
            ).map(::NativeTerminalSession)
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
    ): Result<TerminalSession> =
        clusterCalls.run(clusterId, "Failed to start exec session in pod '$podName'") { cluster ->
            nativeBridge.startExecSession(
                rawKubeconfig = cluster.kubeconfig,
                namespace = namespace,
                podName = podName,
                container = containerName,
                command = command,
                tty = tty,
                callback = execCallback(onStdout, onStderr, onError, onDone),
            ).map(::NativeTerminalSession)
        }

    /** Adapts plain callbacks to the Gomobile interface; errors are sanitized before they surface. */
    private fun execCallback(
        onStdout: (String) -> Unit,
        onStderr: (String) -> Unit,
        onError: (String) -> Unit,
        onDone: () -> Unit,
    ): ExecCallback = object : ExecCallback {
        override fun onStdout(output: String) = onStdout(output)

        override fun onStderr(output: String) = onStderr(output)

        override fun onError(err: String) = onError(LogSanitizer.sanitize(err))

        override fun onDone() = onDone()
    }
}
