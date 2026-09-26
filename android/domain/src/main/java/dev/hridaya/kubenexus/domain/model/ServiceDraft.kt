package dev.hridaya.kubenexus.domain.model

import dev.hridaya.kubenexus.core.common.util.K8sNames

/**
 * User input for the guided Service creation flow (issue #5).
 *
 * Validation lives here rather than in the UI so the rules are plain-JVM
 * testable and identical between the live form check and the pre-apply gate.
 * [validate] returns one message per invalid field, keyed by field name;
 * an empty map means the draft is ready to be rendered into a manifest.
 *
 * [selectorApp] targets the pods to route traffic to by matching their
 * `app` label, which is the label the Deployment generator stamps onto them.
 */
data class ServiceDraft(
    val name: String = "",
    val namespace: String = "",
    val selectorApp: String = "",
    val port: Int = DEFAULT_PORT,
    val targetPort: Int = DEFAULT_PORT,
    val serviceType: String = DEFAULT_TYPE,
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
            selectorApp.isBlank() -> errors["selectorApp"] = "Selector is required"
            // A label value, not a DNS name: "MyApp", "my_app" and "web.v2" are all valid.
            !K8sNames.isValidLabelValue(selectorApp) ->
                errors["selectorApp"] = "Use up to 63 letters, numbers, '-', '_' or '.', starting and ending with a letter or number"
        }

        if (port !in MIN_PORT..MAX_PORT) {
            errors["port"] = "Port must be between $MIN_PORT and $MAX_PORT"
        }

        if (targetPort !in MIN_PORT..MAX_PORT) {
            errors["targetPort"] = "Target port must be between $MIN_PORT and $MAX_PORT"
        }

        if (serviceType !in TYPES) {
            errors["serviceType"] = "Type must be ClusterIP, NodePort, or LoadBalancer"
        }

        return errors
    }

    companion object {
        const val DEFAULT_TYPE = "ClusterIP"
        const val DEFAULT_PORT = 0
        const val MIN_PORT = 1
        const val MAX_PORT = 65535
        const val MAX_NAME_LENGTH = 63
        val TYPES = listOf("ClusterIP", "NodePort", "LoadBalancer")
    }
}
