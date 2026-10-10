package dev.hridaya.kubenexus.data.mapper

import dev.hridaya.kubenexus.core.common.time.K8sTime
import dev.hridaya.kubenexus.data.source.remote.dto.DeploymentConditionDto
import dev.hridaya.kubenexus.data.source.remote.dto.DeploymentDto
import dev.hridaya.kubenexus.data.source.remote.dto.EventDto
import dev.hridaya.kubenexus.domain.model.DeploymentCondition
import dev.hridaya.kubenexus.domain.model.DeploymentDetails
import dev.hridaya.kubenexus.domain.model.DeploymentSummary
import dev.hridaya.kubenexus.domain.model.K8sEventSummary

private const val DEFAULT_NAMESPACE = "default"

/**
 * Maps an event to the workload-detail event shape. Kubernetes populates
 * whichever of lastTimestamp/eventTime/firstTimestamp the emitting component
 * uses; the latest of the three is the one worth showing.
 */
fun EventDto.toEventSummary(): K8sEventSummary = K8sEventSummary(
    type = type.ifBlank { null },
    reason = reason.ifBlank { null },
    message = message.ifBlank { null },
    count = count,
    lastTimestampMillis =
        K8sTime.parseTimestampMillis(lastTimestamp ?: eventTime ?: firstTimestamp),
)

/** Lean list-row projection of a Deployment. */
fun DeploymentDto.toDeploymentSummary(): DeploymentSummary {
    val namespace = metadata.namespace.ifBlank { DEFAULT_NAMESPACE }
    return DeploymentSummary(
        id = "${namespace}_${metadata.name}",
        name = metadata.name,
        namespace = namespace,
        desiredReplicas = spec.replicas,
        readyReplicas = status.readyReplicas,
        availableReplicas = status.availableReplicas,
        images = images(),
        creationTimestampMillis = K8sTime.parseTimestampMillis(metadata.creationTimestamp) ?: 0L,
    )
}

/**
 * Builds the describe-view of a Deployment. [events] comes from a separate
 * field-selected API call and is passed in rather than fetched here so a
 * failure fetching events cannot lose the primary payload.
 */
fun DeploymentDto.toDeploymentDetails(events: List<EventDto> = emptyList()): DeploymentDetails {
    val namespace = metadata.namespace.ifBlank { DEFAULT_NAMESPACE }
    return DeploymentDetails(
        name = metadata.name,
        namespace = namespace,
        creationTimestampMillis = K8sTime.parseTimestampMillis(metadata.creationTimestamp) ?: 0L,
        desiredReplicas = spec.replicas,
        readyReplicas = status.readyReplicas,
        availableReplicas = status.availableReplicas,
        updatedReplicas = status.updatedReplicas,
        strategyType = spec.strategy.type?.ifBlank { null },
        minReadySeconds = spec.minReadySeconds,
        selectorMatchLabels = spec.selector.matchLabels,
        labels = metadata.labels,
        annotations = metadata.annotations,
        conditions = status.conditions.map { it.toDomain() },
        images = images(),
        events = events.map { it.toEventSummary() },
    )
}

private fun DeploymentDto.images(): List<String> =
    spec.template.spec.containers.map { it.image }.filter { it.isNotBlank() }

private fun DeploymentConditionDto.toDomain(): DeploymentCondition = DeploymentCondition(
    type = type,
    status = status,
    lastUpdateMillis = K8sTime.parseTimestampMillis(lastUpdateTime),
    reason = reason?.ifBlank { null },
    message = message?.ifBlank { null },
)
