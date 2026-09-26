package dev.hridaya.kubenexus.domain.usecase

import dev.hridaya.kubenexus.core.common.result.AppError
import dev.hridaya.kubenexus.core.common.result.Result
import dev.hridaya.kubenexus.domain.model.Pod
import dev.hridaya.kubenexus.domain.model.PodStatus
import dev.hridaya.kubenexus.domain.model.ServiceDetails
import dev.hridaya.kubenexus.domain.model.ServiceForwardTarget
import dev.hridaya.kubenexus.domain.repository.PodRepository
import javax.inject.Inject

/**
 * Resolves a concrete pod name and numeric container port destination for
 * forwarding into a Service.
 *
 * Kubernetes Services do not listen directly; kubectl port-forward service/name
 * queries the matching pods, selects a running endpoint pod, and resolves
 * targetPort, which may be a number or the name of one of that pod's container
 * ports.
 */
class ResolveServiceForwardTargetUseCase @Inject constructor(
    private val podRepository: PodRepository,
) {
    suspend operator fun invoke(
        rawKubeconfig: String,
        service: ServiceDetails,
        servicePort: Int,
    ): Result<ServiceForwardTarget> {
        if (service.selector.isEmpty()) {
            return Result.Error(
                AppError.Validation("Service '${service.name}' has no selector defined to target pods."),
            )
        }

        val labelSelector = service.selector.entries.joinToString(",") { "${it.key}=${it.value}" }
        val podsResult = podRepository.listPodsBySelector(
            rawKubeconfig = rawKubeconfig,
            namespace = service.namespace,
            labelSelector = labelSelector,
        )

        val pods = when (podsResult) {
            is Result.Success -> podsResult.data
            is Result.Error -> return Result.Error(podsResult.error)
            is Result.Loading -> return Result.Error(AppError.Validation("Pods loading"))
        }

        val portDetail = service.ports.firstOrNull { it.port == servicePort }
        val namedTarget = portDetail?.targetPortName?.takeIf { portDetail.targetPort <= 0 }

        // Like kubectl, only a running pod can be forwarded to. With a named targetPort the
        // pod must also define that container port.
        val running = pods.filter { it.status == PodStatus.RUNNING }
        val candidates = if (namedTarget != null) {
            running.filter { namedTarget in it.namedContainerPorts }
        } else {
            running
        }
        val targetPod = candidates.firstOrNull { it.isFullyReady() } ?: candidates.firstOrNull()
            ?: return Result.Error(
                AppError.NotFound(
                    when {
                        running.isEmpty() -> "No running pods match service '${service.name}'."
                        else -> "No running pod behind service '${service.name}' defines a container port named '$namedTarget'."
                    },
                ),
            )

        val targetPort = when {
            portDetail == null -> servicePort
            portDetail.targetPort > 0 -> portDetail.targetPort
            namedTarget != null -> targetPod.namedContainerPorts.getValue(namedTarget)
            // An omitted targetPort defaults to the Service port.
            else -> servicePort
        }

        return Result.Success(
            ServiceForwardTarget(
                podName = targetPod.name,
                podPort = targetPort,
            ),
        )
    }
}

private fun Pod.isFullyReady(): Boolean = readyContainers.split("/").let { parts ->
    parts.size == 2 && parts[0] == parts[1] && parts[0] != "0"
}
