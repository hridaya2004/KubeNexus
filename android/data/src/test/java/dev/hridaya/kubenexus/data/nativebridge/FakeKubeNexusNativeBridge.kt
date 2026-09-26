package dev.hridaya.kubenexus.data.nativebridge

import client.ExecCallback
import client.ExecResult
import client.ExecSession
import client.LogCallback
import dev.hridaya.kubenexus.core.common.result.AppError
import dev.hridaya.kubenexus.core.common.result.Result
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

/**
 * Reusable test double for [KubeNexusNativeBridge].
 *
 * Every method has a benign default so a test can override only what it
 * exercises. This replaces four near-identical full-interface fakes that had to
 * be edited in lockstep whenever the bridge changed.
 *
 * Methods returning `client.*` streaming types default to an error, because those
 * types are JNI proxies that cannot be constructed on the JVM.
 */
open class FakeKubeNexusNativeBridge : KubeNexusNativeBridge {

    private var initialized = false

    override fun initialize() {
        initialized = true
    }

    override fun isAvailable(): Boolean = initialized

    override fun touch(): Boolean = true

    override fun listPods(
        rawKubeconfig: String,
        namespace: String?,
        labelSelector: String,
        limit: Long,
    ): Result<List<Pod>> = Result.Success(emptyList())

    override fun listDeployments(
        rawKubeconfig: String,
        namespace: String?,
    ): Result<List<DeploymentSummary>> = Result.Success(emptyList())

    override fun createNamespace(rawKubeconfig: String, name: String): Result<Unit> =
        Result.Success(Unit)

    override fun listNamespaces(rawKubeconfig: String): Result<List<Namespace>> =
        Result.Success(emptyList())

    override fun deleteNamespace(rawKubeconfig: String, namespace: String): Result<Unit> =
        Result.Success(Unit)

    override fun listAPIResources(rawKubeconfig: String): Result<List<APIResource>> =
        Result.Success(emptyList())

    override fun topPod(
        rawKubeconfig: String,
        namespace: String,
        podName: String,
    ): Result<PodMetricSample?> = Result.Success(null)

    override fun topPods(rawKubeconfig: String, namespace: String?): Result<List<PodMetricSample>> =
        Result.Success(emptyList())

    override fun openAPISchemaJSON(rawKubeconfig: String): Result<String> =
        Result.Error(AppError.Unknown("OpenAPI schema not configured"))

    override fun describePod(
        rawKubeconfig: String,
        namespace: String,
        podName: String,
    ): Result<PodDetails> = Result.Error(AppError.NotFound("Pod '$podName' not found"))

    override fun describeDeployment(
        rawKubeconfig: String,
        namespace: String,
        name: String,
    ): Result<DeploymentDetails> = Result.Error(AppError.NotFound("Deployment '$name' not found"))

    override fun listServices(
        rawKubeconfig: String,
        namespace: String?,
    ): Result<List<ServiceSummary>> = Result.Success(emptyList())

    override fun describeService(
        rawKubeconfig: String,
        namespace: String,
        name: String,
    ): Result<ServiceDetails> = Result.Error(AppError.NotFound("Service '$name' not found"))

    override fun deletePod(
        rawKubeconfig: String,
        namespace: String,
        podName: String,
    ): Result<Unit> = Result.Success(Unit)

    override fun createDeployment(
        rawKubeconfig: String,
        namespace: String,
        manifestYaml: String,
    ): Result<String> = Result.Success(manifestYaml)

    override fun scaleDeployment(
        rawKubeconfig: String,
        namespace: String,
        name: String,
        replicas: Int,
    ): Result<Unit> = Result.Success(Unit)

    override fun restartDeployment(
        rawKubeconfig: String,
        namespace: String,
        name: String,
    ): Result<Unit> = Result.Success(Unit)

    override fun deleteDeployment(
        rawKubeconfig: String,
        namespace: String,
        name: String,
    ): Result<Unit> = Result.Success(Unit)

    override fun createPod(
        rawKubeconfig: String,
        namespace: String,
        manifestYaml: String,
    ): Result<String> = Result.Success(manifestYaml)

    override fun createService(
        rawKubeconfig: String,
        namespace: String,
        manifestYaml: String,
    ): Result<String> = Result.Success(manifestYaml)

    override fun getPodLogs(
        rawKubeconfig: String,
        namespace: String,
        podName: String,
        container: String?,
        tailLines: Long?,
    ): Result<String> = Result.Success("")

    override fun streamPodLogs(
        rawKubeconfig: String,
        namespace: String,
        podName: String,
        container: String?,
        tailLines: Long?,
        callback: LogCallback,
    ): Result<LogStreamHandle> = Result.Success(LogStreamHandle {})

    override fun exec(
        rawKubeconfig: String,
        namespace: String,
        podName: String,
        container: String,
        command: String,
        stdin: String,
    ): Result<ExecResult> = Result.Error(AppError.Unknown("exec unavailable on the JVM"))

    override fun startTerminal(
        rawKubeconfig: String,
        namespace: String,
        podName: String,
        container: String,
        callback: ExecCallback,
    ): Result<ExecSession> = Result.Error(AppError.Unknown("exec unavailable on the JVM"))

    override fun startExecSession(
        rawKubeconfig: String,
        namespace: String,
        podName: String,
        container: String,
        command: String,
        tty: Boolean,
        callback: ExecCallback,
    ): Result<ExecSession> = Result.Error(AppError.Unknown("exec unavailable on the JVM"))

    override fun startPortForward(
        rawKubeconfig: String,
        namespace: String,
        podName: String,
        localPort: Int,
        remotePort: Int,
        listener: PortForwardListener,
    ): Result<String> = Result.Error(AppError.Unknown("port-forward unavailable on the JVM"))

    override fun stopPortForward(handleId: String): Result<Unit> = Result.Success(Unit)

    override fun ping(rawKubeconfig: String): Result<String> =
        Result.Success("Cluster ready & healthy (Kubernetes v1.30.0)")

    override fun checkLivez(rawKubeconfig: String): Result<Boolean> = Result.Success(true)

    override fun checkReadyz(rawKubeconfig: String): Result<Boolean> = Result.Success(true)

    override fun checkHealthz(rawKubeconfig: String): Result<Boolean> = Result.Success(true)

    override fun serverVersion(rawKubeconfig: String): Result<String> = Result.Success("v1.30.0")

    override fun checkHealth(rawKubeconfig: String): Result<ClusterHealth> = Result.Success(
        ClusterHealth(
            livez = true,
            readyz = true,
            healthz = true,
            serverVersion = "v1.30.0",
            statusMessage = "Ready",
        ),
    )
}
