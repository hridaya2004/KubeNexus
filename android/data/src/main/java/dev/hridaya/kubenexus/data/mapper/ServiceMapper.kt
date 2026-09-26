package dev.hridaya.kubenexus.data.mapper

import dev.hridaya.kubenexus.core.common.time.K8sTime
import dev.hridaya.kubenexus.data.source.local.entity.ServiceEntity
import dev.hridaya.kubenexus.data.source.remote.dto.EventDto
import dev.hridaya.kubenexus.data.source.remote.dto.NAMED_PORT_UNRESOLVED
import dev.hridaya.kubenexus.data.source.remote.dto.ServiceDto
import dev.hridaya.kubenexus.data.source.remote.dto.ServicePortDto
import dev.hridaya.kubenexus.domain.model.ServiceDetails
import dev.hridaya.kubenexus.domain.model.ServicePortDetail
import dev.hridaya.kubenexus.domain.model.ServiceSummary
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

private const val DEFAULT_NAMESPACE = "default"

/**
 * Ports are stored as one TEXT column: comma-separated ports, each encoded as
 * pipe-delimited fields `name|port|targetPort|nodePort|protocol`, with empty
 * strings for absent name/nodePort. Same flat-column convention as images on
 * DeploymentEntity; none of these characters can occur in a Service port name,
 * protocol or port number, so the encoding round-trips losslessly.
 */
private const val PORTS_SEPARATOR = ","
private const val PORT_FIELD_SEPARATOR = "|"

/**
 * Builds the describe-view of a Service. [events] comes from a separate
 * field-selected API call and is passed in rather than fetched here so a
 * failure fetching events cannot lose the primary payload.
 */
fun ServiceDto.toServiceDetails(events: List<EventDto> = emptyList()): ServiceDetails {
    val namespace = metadata.namespace.ifBlank { DEFAULT_NAMESPACE }
    return ServiceDetails(
        name = metadata.name,
        namespace = namespace,
        creationTimestampMillis = K8sTime.parseTimestampMillis(metadata.creationTimestamp) ?: 0L,
        type = spec.type,
        clusterIP = spec.clusterIP,
        clusterIPs = spec.clusterIPs.filter { it.isNotBlank() },
        externalIPs = externalIps(),
        selector = spec.selector,
        ports = spec.ports.map { it.toDomain() },
        labels = metadata.labels,
        annotations = metadata.annotations,
        events = events.map { it.toEventSummary() },
    )
}

/**
 * External IPs follow kubectl describe semantics: spec.externalIPs plus any
 * LoadBalancer ingress addresses, deduplicated in first-seen order.
 */
private fun ServiceDto.externalIps(): List<String> =
    buildList {
        addAll(spec.externalIPs)
        status.loadBalancer.ingress.forEach { ingress ->
            val address = ingress.ip?.ifBlank { null } ?: ingress.hostname?.ifBlank { null }
            if (address != null) add(address)
        }
    }.distinct()

fun ServicePortDto.toDomain(): ServicePortDetail {
    val numericTarget = targetPort.toTargetPortInt()
    return ServicePortDetail(
        port = port,
        targetPort = numericTarget,
        nodePort = nodePort,
        protocol = protocol,
        name = name?.ifBlank { null },
        targetPortName = if (numericTarget == NAMED_PORT_UNRESOLVED) {
            targetPort?.content?.trim()?.ifBlank { null }
        } else {
            null
        },
    )
}

private fun JsonPrimitive?.toTargetPortInt(): Int =
    this?.intOrNull ?: this?.content?.trim()?.toIntOrNull() ?: NAMED_PORT_UNRESOLVED

/** Lean list-row projection of a described Service. */
fun ServiceDto.toServiceSummary(): ServiceSummary {
    val namespace = metadata.namespace.ifBlank { DEFAULT_NAMESPACE }
    return ServiceSummary(
        id = "${namespace}_${metadata.name}",
        name = metadata.name,
        namespace = namespace,
        type = spec.type,
        clusterIP = spec.clusterIP,
        ports = spec.ports.map { it.toDomain() },
        creationTimestampMillis = K8sTime.parseTimestampMillis(metadata.creationTimestamp) ?: 0L,
    )
}

/** Maps a Service to its cache row; see [PORTS_SEPARATOR] for the ports encoding. */
fun ServiceSummary.toEntity(clusterId: String): ServiceEntity = ServiceEntity(
    id = "${clusterId}_${namespace}_$name",
    clusterId = clusterId,
    name = name,
    namespace = namespace,
    type = type,
    clusterIp = clusterIP,
    ports = ports.joinToString(separator = PORTS_SEPARATOR) { port -> port.encodeForStorage() },
    creationTimestampMillis = creationTimestampMillis,
)

fun ServiceEntity.toDomain(): ServiceSummary = ServiceSummary(
    id = id,
    name = name,
    namespace = namespace,
    type = type,
    clusterIP = clusterIp,
    ports = ports.split(PORTS_SEPARATOR).filter { it.isNotBlank() }.map { it.decodeStoredPort() },
    creationTimestampMillis = creationTimestampMillis ?: 0L,
)

private fun ServicePortDetail.encodeForStorage(): String = listOf(
    name.orEmpty(),
    port.toString(),
    targetPort.toString(),
    nodePort?.toString().orEmpty(),
    protocol,
).joinToString(separator = PORT_FIELD_SEPARATOR)

private fun String.decodeStoredPort(): ServicePortDetail {
    val parts = split(PORT_FIELD_SEPARATOR)
    return ServicePortDetail(
        name = parts.getOrNull(0)?.ifBlank { null },
        port = parts.getOrNull(1)?.toIntOrNull() ?: 0,
        targetPort = parts.getOrNull(2)?.toIntOrNull() ?: NAMED_PORT_UNRESOLVED,
        nodePort = parts.getOrNull(3)?.toIntOrNull(),
        protocol = parts.getOrNull(4) ?: "TCP",
    )
}
