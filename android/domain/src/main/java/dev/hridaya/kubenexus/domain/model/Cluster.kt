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
    val contextName: String,
    val userName: String = "",
    val namespace: String = "default",
    val isActive: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val lastConnectedAt: Long? = null,
    val status: ClusterStatus = ClusterStatus.DISCONNECTED,
    /**
     * The stored credentials exist but can no longer be decrypted on this device, e.g. the
     * Keystore key was lost. The cluster has to be added again.
     */
    val credentialsUnavailable: Boolean = false,
) {
    companion object {
        /**
         * Largest kubeconfig accepted, in UTF-8 bytes. Real kubeconfigs are kilobytes even with
         * many contexts and embedded certificates, and the encrypted copy is stored in a single
         * database row that has to fit in SQLite's 2 MB cursor window when it is read back.
         */
        const val MAX_KUBECONFIG_BYTES = 1024 * 1024
    }
}
