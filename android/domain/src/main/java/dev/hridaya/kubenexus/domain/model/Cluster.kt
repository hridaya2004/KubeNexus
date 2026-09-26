package dev.hridaya.kubenexus.domain.model

enum class ClusterStatus {
    CONNECTED,
    DISCONNECTED,
    ERROR,
}

data class Cluster(
    val id: String,
    val name: String,
    val serverUrl: String,
    val rawKubeconfig: String,
    val contextName: String,
    val userName: String = "",
    val namespace: String = "default",
    val isActive: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val lastConnectedAt: Long? = null,
    val status: ClusterStatus = ClusterStatus.DISCONNECTED,
    /**
     * The stored credentials exist but can no longer be decrypted on this device, e.g. the
     * Keystore key was lost. [rawKubeconfig] is empty; the cluster has to be added again.
     */
    val credentialsUnavailable: Boolean = false,
) {
    // The generated toString would print the decrypted kubeconfig into any log or crash
    // report that includes a Cluster.
    override fun toString(): String =
        "Cluster(id=$id, name=$name, serverUrl=$serverUrl, contextName=$contextName, " +
            "isActive=$isActive, status=$status, credentialsUnavailable=$credentialsUnavailable, " +
            "rawKubeconfig=<redacted>)"
}

data class ParsedKubeconfig(
    val clusterName: String,
    val serverUrl: String,
    val contextName: String,
    val userName: String,
    val namespace: String,
    val rawKubeconfig: String,
    val certificateAuthorityData: String? = null,
    val insecureSkipTlsVerify: Boolean = false,
)

