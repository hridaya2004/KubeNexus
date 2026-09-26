package dev.hridaya.kubenexus.data.mapper

import dev.hridaya.kubenexus.core.common.time.K8sTime
import dev.hridaya.kubenexus.data.source.local.entity.NamespaceEntity
import dev.hridaya.kubenexus.data.source.local.entity.PodEntity
import dev.hridaya.kubenexus.data.source.remote.dto.ContainerDto
import dev.hridaya.kubenexus.data.source.remote.dto.ContainerStateDto
import dev.hridaya.kubenexus.data.source.remote.dto.ContainerStatusDto
import dev.hridaya.kubenexus.data.source.remote.dto.EventDto
import dev.hridaya.kubenexus.data.source.remote.dto.NamespaceDto
import dev.hridaya.kubenexus.data.source.remote.dto.PodConditionDto
import dev.hridaya.kubenexus.data.source.remote.dto.PodDto
import dev.hridaya.kubenexus.domain.model.ContainerDetail
import dev.hridaya.kubenexus.domain.model.Namespace
import dev.hridaya.kubenexus.domain.model.Pod
import dev.hridaya.kubenexus.domain.model.PodConditionDetail
import dev.hridaya.kubenexus.domain.model.PodDetails
import dev.hridaya.kubenexus.domain.model.PodEventDetail
import dev.hridaya.kubenexus.domain.model.PodStatus

private const val CRASH_LOOP_BACK_OFF = "CrashLoopBackOff"
private const val DEFAULT_NAMESPACE = "default"

/**
 * Derives the displayed status from phase plus container state.
 *
 * The Kubernetes phase alone can never yield [PodStatus.CRASH_LOOP], because
 * CrashLoopBackOff is a container waiting reason rather than a pod phase. The old
 * mapper matched on the phase string only, which left that enum case
 * permanently unreachable. Container statuses are now consulted, so a
 * crash-looping pod is reported as one.
 */
private fun derivePodStatus(dto: PodDto): PodStatus {
    val allStatuses = dto.status.containerStatuses + dto.status.initContainerStatuses
    val crashLooping = allStatuses.any { containerStatus ->
        containerStatus.state?.waiting?.reason
            ?.equals(CRASH_LOOP_BACK_OFF, ignoreCase = true) == true
    }
    if (crashLooping) return PodStatus.CRASH_LOOP

    return when (dto.status.phase.lowercase()) {
        "running" -> PodStatus.RUNNING
        "pending" -> PodStatus.PENDING
        "succeeded", "completed" -> PodStatus.COMPLETED
        "failed" -> PodStatus.FAILED
        else -> PodStatus.UNKNOWN
    }
}

/** Counts ready containers against the number declared in the spec. */
private fun readyContainers(dto: PodDto): String {
    val total = dto.spec.containers.size
    val ready = dto.status.containerStatuses.count { it.ready }
    return "$ready/$total"
}

/** Sums restarts across containers, matching kubectl's RESTARTS column. */
private fun totalRestarts(dto: PodDto): Int =
    dto.status.containerStatuses.sumOf { it.restartCount }

private fun PodDto.resolvedNamespace(): String =
    metadata.namespace.ifBlank { DEFAULT_NAMESPACE }

fun PodDto.toDomain(): Pod = Pod(
    id = "${resolvedNamespace()}_${metadata.name}",
    name = metadata.name,
    namespace = resolvedNamespace(),
    status = derivePodStatus(this),
    readyContainers = readyContainers(this),
    restarts = totalRestarts(this),
    creationTimestampMillis = K8sTime.parseTimestampMillis(metadata.creationTimestamp),
    ip = status.podIP?.ifBlank { null },
    node = spec.nodeName?.ifBlank { null },
    // The flattened Gomobile struct had no image field, so the pod list could
    // never show one. The full spec is available now.
    image = spec.containers.firstOrNull()?.image?.ifBlank { null },
    namedContainerPorts = spec.containers
        .flatMap { it.ports }
        .filter { !it.name.isNullOrBlank() && it.containerPort > 0 }
        .associate { it.name!! to it.containerPort },
)

/**
 * Maps a pod to its cache row. The creation timestamp is persisted rather than a
 * pre-rendered age string, so a cached row is not stuck with a value formatted at
 * sync time.
 */
fun Pod.toEntity(clusterId: String): PodEntity = PodEntity(
    id = "${clusterId}_${namespace}_$name",
    clusterId = clusterId,
    name = name,
    namespace = namespace,
    status = status.title,
    readyContainers = readyContainers,
    restarts = restarts,
    creationTimestampMillis = creationTimestampMillis,
    ip = ip,
    node = node,
    image = image,
)

/**
 * Builds the detail view. [rawJson] is the untouched object from the API server,
 * surfaced so the UI can offer a raw view; it was previously always null because
 * the flattened struct had already discarded everything it did not model.
 */
fun PodDto.toDetails(
    events: List<EventDto> = emptyList(),
    rawJson: String? = null,
    nowMillis: Long = System.currentTimeMillis(),
): PodDetails = PodDetails(
    name = metadata.name,
    namespace = resolvedNamespace(),
    status = derivePodStatus(this),
    node = spec.nodeName?.ifBlank { null },
    ip = status.podIP?.ifBlank { null },
    hostIp = status.hostIP?.ifBlank { null },
    restartPolicy = spec.restartPolicy ?: "Always",
    startTime = status.startTime,
    containers = spec.containers.map { it.toDomain(status.containerStatuses) },
    initContainers = spec.initContainers.map { it.toDomain(status.initContainerStatuses) },
    conditions = status.conditions.map { it.toDomain() },
    events = events.map { it.toDomain(nowMillis) },
    // Was a JSON string field parsed with org.json; now a real Map.
    labels = metadata.labels,
    // Was always empty because annotations never crossed the bridge at all.
    annotations = metadata.annotations,
    // Was a comma-separated string requiring a CSV split on this side.
    volumes = spec.volumes.map { it.name },
    rawDescribeText = rawJson,
)

private fun ContainerDto.toDomain(statuses: List<ContainerStatusDto>): ContainerDetail {
    val containerStatus = statuses.firstOrNull { it.name == name }
    return ContainerDetail(
        name = name,
        image = image,
        ready = containerStatus?.ready ?: false,
        restartCount = containerStatus?.restartCount ?: 0,
        state = formatContainerState(containerStatus?.state),
    )
}

/** Mirrors the Go formatContainerState this replaces. */
private fun formatContainerState(state: ContainerStateDto?): String = when {
    state == null -> "Unknown"
    state.running != null -> "Running"
    state.waiting != null -> "Waiting (${state.waiting.reason ?: "Unknown"})"
    state.terminated != null -> "Terminated (exit ${state.terminated.exitCode})"
    else -> "Unknown"
}

fun PodConditionDto.toDomain(): PodConditionDetail = PodConditionDetail(
    type = type,
    status = status,
    lastTransitionTime = lastTransitionTime,
    reason = reason?.ifBlank { null },
    message = message?.ifBlank { null },
)

fun EventDto.toDomain(nowMillis: Long = System.currentTimeMillis()): PodEventDetail {
    // Kubernetes populates whichever of these the emitting component uses.
    val timestamp = lastTimestamp ?: eventTime ?: firstTimestamp
    return PodEventDetail(
        type = type.ifBlank { "Normal" },
        reason = reason,
        message = message,
        age = K8sTime.formatAge(K8sTime.parseTimestampMillis(timestamp), nowMillis),
    )
}

fun NamespaceDto.toDomain(nowMillis: Long = System.currentTimeMillis()): Namespace = Namespace(
    name = metadata.name,
    status = status.phase.ifBlank { "Active" },
    age = K8sTime.formatAge(
        K8sTime.parseTimestampMillis(metadata.creationTimestamp),
        nowMillis,
    ),
)

fun Namespace.toEntity(clusterId: String): NamespaceEntity = NamespaceEntity(
    id = "${clusterId}_$name",
    clusterId = clusterId,
    name = name,
    status = status,
)

fun PodEntity.toDomain(): Pod {
    val domainStatus = when (status.lowercase()) {
        "running" -> PodStatus.RUNNING
        "pending" -> PodStatus.PENDING
        "completed", "succeeded" -> PodStatus.COMPLETED
        "failed" -> PodStatus.FAILED
        "crashloopbackoff", "error" -> PodStatus.CRASH_LOOP
        else -> PodStatus.UNKNOWN
    }

    return Pod(
        id = id,
        name = name,
        namespace = namespace,
        status = domainStatus,
        readyContainers = readyContainers,
        restarts = restarts,
        creationTimestampMillis = creationTimestampMillis,
        node = node,
        ip = ip,
        image = image,
    )
}

fun NamespaceEntity.toDomainName(): String = name
