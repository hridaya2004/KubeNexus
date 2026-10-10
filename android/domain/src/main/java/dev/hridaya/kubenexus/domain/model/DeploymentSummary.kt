package dev.hridaya.kubenexus.domain.model

import dev.hridaya.kubenexus.domain.model.resource.KubeObjectSummary

/**
 * Read-only summary of an apps/v1 Deployment for list rendering.
 *
 * Deliberately lean: enough for a workload card (name, rollout progress,
 * images, age) without pulling the full spec/status graphs across the JNI
 * boundary.
 */
data class DeploymentSummary(
    override val id: String,
    override val name: String,
    override val namespace: String,
    val desiredReplicas: Int,
    val readyReplicas: Int,
    val availableReplicas: Int,
    val images: List<String>,
    override val creationTimestampMillis: Long,
) : KubeObjectSummary {

    /** True when every desired replica is ready and available. */
    val isHealthy: Boolean
        get() = desiredReplicas > 0 && readyReplicas >= desiredReplicas && availableReplicas >= desiredReplicas
}
