package dev.hridaya.kubenexus.data.kubeconfig

import android.util.Log
import dev.hridaya.kubenexus.core.common.log.APP_LOG_TAG_PREFIX
import dev.hridaya.kubenexus.core.common.result.AppError
import dev.hridaya.kubenexus.core.common.result.Result
import dev.hridaya.kubenexus.core.security.LogSanitizer
import dev.hridaya.kubenexus.data.nativebridge.KubeNexusNativeBridge
import javax.inject.Inject

open class ClusterConnectionTester @Inject constructor(
    private val nativeBridge: KubeNexusNativeBridge,
) {
    companion object {
        private const val TAG = "$APP_LOG_TAG_PREFIX/ClusterConnTester"
    }

    /**
     * Verifies connectivity and health for the Kubernetes cluster specified in the parsed kubeconfig.
     * Delegates all TLS validation, CA certs, mTLS authentication, and health checks (/readyz, /livez, /version)
     * directly to the native Go client (`client-go`).
     *
     * Returns a descriptive string on success, or throws an exception on failure.
     */
    open fun testConnection(parsed: ParsedKubeconfig): String {
        Log.d(
            TAG,
            "Testing connection to cluster: '${parsed.clusterName}' (server: ${parsed.serverUrl}, context: ${parsed.contextName})",
        )

        return when (val pingResult = nativeBridge.ping(parsed.rawKubeconfig)) {
            is Result.Success -> {
                val status = pingResult.data
                Log.d(TAG, "Cluster ping succeeded: $status")
                status
            }

            is Result.Error -> {
                val errorMsg = LogSanitizer.sanitize(pingResult.error.message)
                    .ifBlank { "Connection failed or cluster unreachable" }
                val errorReport = buildString {
                    appendLine("Failed to connect to Kubernetes Cluster: '${parsed.clusterName}'")
                    appendLine("Target Server: ${parsed.serverUrl}")
                    appendLine("Context: ${parsed.contextName}")
                    appendLine("Message: $errorMsg")
                }
                val cause = (pingResult.error as? AppError.Unknown)?.throwable
                throw Exception(errorReport, cause)
            }

            is Result.Loading -> {
                throw IllegalStateException("Connection test operation is unexpectedly in Loading state")
            }
        }
    }
}
