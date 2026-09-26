package dev.hridaya.kubenexus.data.kubeconfig

import dev.hridaya.kubenexus.domain.model.Cluster
import dev.hridaya.kubenexus.domain.model.ParsedKubeconfig
import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings

/**
 * Reads the cluster a kubeconfig connects to, the same way client-go does.
 *
 * The Go engine connects with clientcmd, which follows `current-context` to one context and
 * from there to one cluster and one user. The metadata stored and shown for a cluster must
 * describe that same connection, so this resolves the kubeconfig structurally, by name,
 * rather than by searching the text: in a multi-context file, cluster, context and user
 * entries routinely share names, and a text search can land on the wrong entry and label
 * one cluster with another's name and server.
 */
object KubeconfigParser {

    /**
     * Parses a raw YAML/JSON kubeconfig string and extracts cluster, context, and server metadata.
     * Throws [IllegalArgumentException] with descriptive messages if validation fails.
     */
    fun parse(rawContent: String, customName: String? = null): ParsedKubeconfig {
        val trimmed = rawContent.trim()
        require(trimmed.isNotBlank()) { "Kubeconfig content cannot be empty." }
        require(trimmed.length <= Cluster.MAX_KUBECONFIG_BYTES && trimmed.encodeToByteArray().size <= Cluster.MAX_KUBECONFIG_BYTES) {
            "This kubeconfig is larger than 1 MB, which is more than any kubeconfig needs. Check that it is the right file."
        }

        val document = try {
            Load(LoadSettings.builder().setLabel("kubeconfig").build()).loadFromString(trimmed)
        } catch (e: Exception) {
            throw IllegalArgumentException("This kubeconfig is not valid YAML or JSON: ${e.message}", e)
        }
        @Suppress("UNCHECKED_CAST")
        val root = document as? Map<String?, Any?>
            ?: throw IllegalArgumentException("This kubeconfig is not a YAML or JSON object.")

        val currentContext = root.string("current-context")
        require(currentContext.isNotBlank()) {
            "This kubeconfig has no current-context. Choose one with `kubectl config use-context <name>` and paste it again."
        }

        val context = root.namedEntry("contexts", "context", currentContext)
            ?: throw IllegalArgumentException(
                "current-context is \"$currentContext\", but the kubeconfig has no context with that name.",
            )
        val clusterRef = context.string("cluster")
        require(clusterRef.isNotBlank()) { "Context \"$currentContext\" does not name a cluster." }

        val cluster = root.namedEntry("clusters", "cluster", clusterRef)
            ?: throw IllegalArgumentException(
                "Context \"$currentContext\" uses cluster \"$clusterRef\", which is not defined in the kubeconfig.",
            )
        val serverUrl = cluster.string("server")
        require(serverUrl.isNotBlank()) {
            "No Kubernetes API server URL found in kubeconfig. Please verify the 'clusters.cluster.server' field."
        }

        val finalClusterName = customName?.trim()?.takeIf { it.isNotEmpty() } ?: clusterRef

        return ParsedKubeconfig(
            clusterName = finalClusterName,
            serverUrl = serverUrl,
            contextName = currentContext,
            userName = context.string("user"),
            namespace = context.string("namespace").ifBlank { "default" },
            rawKubeconfig = trimmed,
            certificateAuthorityData = cluster.string("certificate-authority-data").ifBlank { null },
            insecureSkipTlsVerify = cluster.string("insecure-skip-tls-verify").equals("true", ignoreCase = true),
        )
    }

    /**
     * Finds `name` in a kubeconfig list such as `clusters:` and returns its body, e.g. the
     * `cluster:` map of `- name: prod / cluster: {...}`.
     */
    private fun Map<String?, Any?>.namedEntry(listKey: String, bodyKey: String, name: String): Map<String?, Any?>? {
        val entries = this[listKey] as? List<*> ?: return null
        val entry = entries.firstOrNull { (it as? Map<*, *>)?.get("name")?.toString()?.trim() == name }
            as? Map<*, *> ?: return null
        @Suppress("UNCHECKED_CAST")
        return entry[bodyKey] as? Map<String?, Any?> ?: emptyMap()
    }

    private fun Map<String?, Any?>.string(key: String): String = this[key]?.toString()?.trim().orEmpty()
}
