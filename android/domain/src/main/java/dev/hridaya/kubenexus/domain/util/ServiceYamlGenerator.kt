package dev.hridaya.kubenexus.domain.util

import dev.hridaya.kubenexus.core.common.util.renderK8sManifest
import dev.hridaya.kubenexus.domain.model.ServiceDraft

/**
 * Renders the reviewed manifest for the guided Service creation flow as
 * deterministic YAML.
 *
 * The generated text is exactly what gets applied: the user reviews this
 * string, and the same string is sent through the bridge, so what is shown is
 * never out of sync with what is created. The manifest is assembled as an
 * ordered structure and serialized by [renderK8sManifest],
 * which preserves key order instead of relying on hand-appended lines.
 * [ServiceDraft.selectorApp] intentionally targets the `app` label the
 * Deployment generator stamps onto its pods, so a Service created this way
 * routes traffic to that workload even when the Service's own name differs
 * from it.
 */
object ServiceYamlGenerator {

    fun generate(draft: ServiceDraft): String {
        val servicePorts = listOf(
            linkedMapOf(
                "port" to draft.port,
                "targetPort" to draft.targetPort,
            ),
        )

        val serviceManifest = linkedMapOf<String, Any>(
            "apiVersion" to "v1",
            "kind" to "Service",
            "metadata" to linkedMapOf(
                "name" to draft.name,
                "namespace" to draft.namespace,
                "labels" to linkedMapOf("app" to draft.name),
            ),
            "spec" to linkedMapOf(
                "selector" to linkedMapOf("app" to draft.selectorApp),
                "type" to draft.serviceType,
                "ports" to servicePorts,
            ),
        )

        return renderK8sManifest(serviceManifest)
    }
}
