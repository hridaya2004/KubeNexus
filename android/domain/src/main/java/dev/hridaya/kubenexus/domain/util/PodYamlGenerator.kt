package dev.hridaya.kubenexus.domain.util

import dev.hridaya.kubenexus.core.common.util.renderK8sManifest
import dev.hridaya.kubenexus.domain.model.PodDraft

/**
 * Renders the reviewed manifest for the guided Pod creation flow as
 * deterministic YAML.
 *
 * The generated text is exactly what gets applied: the user reviews this
 * string, and the same string is sent through the bridge, so what is shown is
 * never out of sync with what is created. The manifest is assembled as an
 * ordered structure and serialized by [renderK8sManifest],
 * which preserves key order instead of relying on hand-appended lines. The
 * [PodDraft.containerPort] is omittable because a bare container needs no port
 * mapping at all; a zero means "no ports block" rather than port zero, so the
 * block is left out of the reviewed text entirely instead of being emitted
 * with a placeholder.
 */
object PodYamlGenerator {

    fun generate(draft: PodDraft): String {
        val containerSpec = linkedMapOf<String, Any>(
            "name" to draft.name,
            "image" to draft.image,
        )
        if (draft.containerPort > 0) {
            containerSpec["ports"] = listOf(linkedMapOf("containerPort" to draft.containerPort))
        }

        val podManifest = linkedMapOf<String, Any>(
            "apiVersion" to "v1",
            "kind" to "Pod",
            "metadata" to linkedMapOf(
                "name" to draft.name,
                "namespace" to draft.namespace,
                "labels" to linkedMapOf("app" to draft.name),
            ),
            "spec" to linkedMapOf(
                "containers" to listOf(containerSpec),
            ),
        )

        return renderK8sManifest(podManifest)
    }
}
