package dev.hridaya.kubenexus.data.kubeconfig

/** A kubeconfig as parsed for adding a cluster. It holds credentials, so it stays in the data layer. */
data class ParsedKubeconfig(
    val clusterName: String,
    val serverUrl: String,
    val contextName: String,
    val userName: String,
    val namespace: String,
    val rawKubeconfig: String,
    val certificateAuthorityData: String? = null,
    val insecureSkipTlsVerify: Boolean = false,
) {
    // The generated toString would print the kubeconfig's credentials.
    override fun toString(): String =
        "ParsedKubeconfig(clusterName=$clusterName, serverUrl=$serverUrl, contextName=$contextName, " +
                "userName=$userName, namespace=$namespace, insecureSkipTlsVerify=$insecureSkipTlsVerify, " +
                "rawKubeconfig=<redacted>)"
}

