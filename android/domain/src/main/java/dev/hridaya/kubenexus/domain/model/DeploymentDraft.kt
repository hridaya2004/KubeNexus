package dev.hridaya.kubenexus.domain.model

import dev.hridaya.kubenexus.core.common.util.K8sNames

/**
 * User input for the guided Deployment creation flow (issue #5).
 *
 * Validation lives here rather than in the UI so the rules are plain-JVM
 * testable and identical between the live form check and the pre-apply gate.
 * [validate] returns one message per invalid field, keyed by field name;
 * an empty map means the draft is ready to be rendered into a manifest.
 */
data class DeploymentDraft(
    val name: String = "",
    val namespace: String = "",
    val image: String = "",
    val replicas: Int = DEFAULT_REPLICAS,
    val containerPort: Int = DEFAULT_CONTAINER_PORT,
    val serviceType: String = SERVICE_TYPE_NONE,
    val servicePort: Int = DEFAULT_SERVICE_PORT,
) {

    fun validate(): Map<String, String> {
        val errors = mutableMapOf<String, String>()

        when {
            name.isBlank() -> errors["name"] = "Name is required"
            name.length > MAX_NAME_LENGTH ->
                errors["name"] = "Must be $MAX_NAME_LENGTH characters or fewer"

            !K8sNames.isValidDns1035Label(name) ->
                errors["name"] = "Use lowercase letters, numbers and '-' (start with a letter)"
        }

        when {
            namespace.isBlank() -> errors["namespace"] = "Namespace is required"
            namespace.length > MAX_NAME_LENGTH ->
                errors["namespace"] = "Must be $MAX_NAME_LENGTH characters or fewer"

            !K8sNames.isValidDnsLabel(namespace) ->
                errors["namespace"] = "Use lowercase letters, numbers and '-'"
        }

        when {
            image.isBlank() -> errors["image"] = "Image is required"
            image.any { it.isWhitespace() } -> errors["image"] = "Image cannot contain spaces"
        }

        if (replicas !in MIN_REPLICAS..MAX_REPLICAS) {
            errors["replicas"] = "Replicas must be between $MIN_REPLICAS and $MAX_REPLICAS"
        }

        if (containerPort !in MIN_PORT..MAX_PORT) {
            errors["containerPort"] = "Port must be between $MIN_PORT and $MAX_PORT"
        }

        if (serviceType !in SERVICE_TYPES) {
            errors["serviceType"] = "Service type must be None, ClusterIP, or NodePort"
        }

        if (serviceType != SERVICE_TYPE_NONE) {
            if (servicePort !in MIN_PORT..MAX_PORT) {
                errors["servicePort"] = "Service port must be between $MIN_PORT and $MAX_PORT"
            }
        }

        return errors
    }

    companion object {
        const val DEFAULT_REPLICAS = 1
        const val DEFAULT_CONTAINER_PORT = 80
        const val DEFAULT_SERVICE_PORT = 80
        const val SERVICE_TYPE_NONE = "None"
        const val SERVICE_TYPE_CLUSTER_IP = "ClusterIP"
        const val SERVICE_TYPE_NODE_PORT = "NodePort"
        val SERVICE_TYPES =
            listOf(SERVICE_TYPE_NONE, SERVICE_TYPE_CLUSTER_IP, SERVICE_TYPE_NODE_PORT)
        const val MIN_REPLICAS = 1
        const val MAX_REPLICAS = 500
        const val MIN_PORT = 1
        const val MAX_PORT = 65535
        const val MAX_NAME_LENGTH = 63
    }
}
