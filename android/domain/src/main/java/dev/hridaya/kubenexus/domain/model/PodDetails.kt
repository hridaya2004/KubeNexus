package dev.hridaya.kubenexus.domain.model

data class ContainerDetail(
    val name: String,
    val image: String,
    val ready: Boolean,
    val restartCount: Int,
    val state: String = "Running",
    val ports: List<String> = emptyList(),
)

/**
 * One entry of a Pod's status.conditions. [lastTransitionMillis] is epoch millis rather than the
 * RFC 3339 string the API server sends, so screens can show it in the user's own terms.
 */
data class PodConditionDetail(
    val type: String,
    val status: String,
    val lastTransitionMillis: Long? = null,
    val reason: String? = null,
    val message: String? = null,
)

data class PodEventDetail(
    val type: String,
    val reason: String,
    val message: String,
    val age: String
)

data class PodDetails(
    val name: String,
    val namespace: String,
    val status: PodStatus = PodStatus.RUNNING,
    val node: String? = null,
    val ip: String? = null,
    val hostIp: String? = null,
    val restartPolicy: String? = "Always",
    val startTime: String? = null,
    val containers: List<ContainerDetail> = emptyList(),
    val initContainers: List<ContainerDetail> = emptyList(),
    val conditions: List<PodConditionDetail> = emptyList(),
    val events: List<PodEventDetail> = emptyList(),
    val labels: Map<String, String> = emptyMap(),
    val annotations: Map<String, String> = emptyMap(),
    val volumes: List<String> = emptyList(),
    val rawDescribeText: String? = null,
)
