package dev.hridaya.kubenexus.domain.fake

import dev.hridaya.kubenexus.core.common.result.Result
import dev.hridaya.kubenexus.domain.repository.DeploymentRepository

/** Records the last rollout call and answers with [result]. */
class FakeDeploymentRepository : DeploymentRepository {

    data class Call(val action: String, val clusterId: String?, val namespace: String, val name: String, val replicas: Int?)

    var lastCall: Call? = null
    var result: Result<Unit> = Result.Success(Unit)

    override suspend fun scaleDeployment(clusterId: String?, namespace: String, name: String, replicas: Int): Result<Unit> {
        lastCall = Call("scale", clusterId, namespace, name, replicas)
        return result
    }

    override suspend fun restartDeployment(clusterId: String?, namespace: String, name: String): Result<Unit> {
        lastCall = Call("restart", clusterId, namespace, name, replicas = null)
        return result
    }
}
