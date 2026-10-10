package dev.hridaya.kubenexus.data.source.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive

/**
 * Sentinel for a Service targetPort the API server sent as a *named* port
 * (e.g. "http"), which cannot map onto the Int field of
 * [dev.hridaya.kubenexus.domain.model.ServicePortDetail]. Kept visible instead
 * of silently dropping the port row.
 */
const val NAMED_PORT_UNRESOLVED = -1

@Serializable
data class ServiceDto(
    val apiVersion: String? = null,
    val kind: String? = null,
    override val metadata: ObjectMetaDto = ObjectMetaDto(),
    val spec: ServiceSpecDto = ServiceSpecDto(),
    val status: ServiceStatusDto = ServiceStatusDto(),
) : KubeObjectDto

@Serializable
data class ServiceSpecDto(
    // The API server defaults omitted type to ClusterIP; mirroring that keeps a
    // defaulted Service from rendering as untyped.
    val type: String = "ClusterIP",
    // Headless Services carry clusterIP: "None"; kept verbatim rather than
    // nulled out because "None" is meaningful on screen.
    val clusterIP: String = "",
    val clusterIPs: List<String> = emptyList(),
    val externalIPs: List<String> = emptyList(),
    val selector: Map<String, String> = emptyMap(),
    val ports: List<ServicePortDto> = emptyList(),
)

@Serializable
data class ServicePortDto(
    val name: String? = null,
    val protocol: String = "TCP",
    val port: Int = 0,
    // Kubernetes types targetPort as IntOrString: numeric for most Services,
    // a container-port name for others. Held as the raw primitive so a named
    // port decodes to [NAMED_PORT_UNRESOLVED] in the mapper instead of failing
    // the whole Service decode (which would lose an otherwise fully
    // describable Service).
    val targetPort: JsonPrimitive? = null,
    val nodePort: Int? = null,
)

@Serializable
data class ServiceStatusDto(
    val loadBalancer: ServiceLoadBalancerStatusDto = ServiceLoadBalancerStatusDto(),
)

@Serializable
data class ServiceLoadBalancerStatusDto(
    val ingress: List<ServiceLoadBalancerIngressDto> = emptyList(),
)

@Serializable
data class ServiceLoadBalancerIngressDto(
    val ip: String? = null,
    val hostname: String? = null,
)
