package dev.hridaya.kubenexus.data.repository

/** Supplies a cluster's decrypted kubeconfig to data-layer code; it never leaves this layer. */
fun interface KubeconfigProvider {
    /** The kubeconfig of [clusterId], or null when it is unknown or cannot be decrypted. */
    suspend fun kubeconfigOf(clusterId: String): String?
}
