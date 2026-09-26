package dev.hridaya.kubenexus.domain.model

/**
 * One entry of a Service's spec.ports.
 *
 * [targetPort] is the pod-side port. Kubernetes types it as int-or-string
 * (named ports); a name that cannot be resolved to a number is surfaced as -1
 * rather than dropped, so the row still renders with the port name.
 */
data class ServicePortDetail(
    val port: Int,
    val targetPort: Int,
    val nodePort: Int?,
    val protocol: String,
    val name: String?,
    /** The container port name when [targetPort] is named (e.g. "http"), else null. */
    val targetPortName: String? = null,
)

/**
 * Full describe-view of a v1 Service, for the service detail screen.
 *
 * Field names are pinned by the service detail UI and must not be renamed;
 * add-only evolution keeps those call sites compiling.
 */
data class ServiceDetails(
    val name: String,
    val namespace: String,
    val creationTimestampMillis: Long,
    val type: String,

    /** Virtual cluster IP; empty string for headless Services, which have none. */
    val clusterIP: String,
    val clusterIPs: List<String>,
    val externalIPs: List<String>,
    val selector: Map<String, String>,
    val ports: List<ServicePortDetail>,
    val labels: Map<String, String>,
    val annotations: Map<String, String>,
    val events: List<K8sEventSummary>,
)
