package dev.hridaya.kubenexus.domain.repository

import dev.hridaya.kubenexus.core.common.result.Result

/**
 * Deployment rollout operations. Listing, describing, creating and deleting
 * Deployments go through [ResourceRepository].
 */
interface DeploymentRepository {

    /**
     * Scales the deployment's replica count.
     */
    suspend fun scaleDeployment(
        clusterId: String?,
        namespace: String,
        name: String,
        replicas: Int,
    ): Result<Unit>

    /**
     * Triggers a rolling restart of the deployment.
     */
    suspend fun restartDeployment(
        clusterId: String?,
        namespace: String,
        name: String,
    ): Result<Unit>
}
