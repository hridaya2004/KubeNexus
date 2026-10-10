package dev.hridaya.kubenexus.domain.repository

import dev.hridaya.kubenexus.core.common.result.Result
import dev.hridaya.kubenexus.domain.model.CommandExecResult
import dev.hridaya.kubenexus.domain.model.PodMetricSample
import dev.hridaya.kubenexus.domain.model.TerminalSession
import kotlinx.coroutines.flow.Flow

/**
 * Pod behavior that has no generic counterpart: metrics, logs and exec.
 * Listing, describing, creating and deleting pods go through [ResourceRepository].
 */
interface PodRepository {

    suspend fun getPodMetrics(clusterId: String?, namespace: String?): Result<List<PodMetricSample>>

    /**
     * Usage for a single pod. Preferred over [getPodMetrics] when polling one
     * pod, since it does not transfer usage for the whole namespace.
     */
    suspend fun getSinglePodMetrics(
        clusterId: String?,
        namespace: String,
        podName: String,
    ): Result<PodMetricSample?>

    suspend fun getPodLogs(
        clusterId: String?,
        namespace: String,
        podName: String,
        containerName: String? = null,
        tailLines: Long? = null,
    ): Result<String>

    fun streamPodLogs(
        clusterId: String?,
        namespace: String,
        podName: String,
        containerName: String? = null,
        tailLines: Long? = null,
    ): Flow<String>

    suspend fun execCommand(
        clusterId: String?,
        namespace: String,
        podName: String,
        containerName: String,
        command: String,
        stdin: String = "",
    ): Result<CommandExecResult>

    suspend fun startTerminalSession(
        clusterId: String?,
        namespace: String,
        podName: String,
        containerName: String,
        onStdout: (String) -> Unit,
        onStderr: (String) -> Unit,
        onError: (String) -> Unit,
        onDone: () -> Unit,
    ): Result<TerminalSession>

    suspend fun startExecSession(
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
    ): Result<TerminalSession>
}
