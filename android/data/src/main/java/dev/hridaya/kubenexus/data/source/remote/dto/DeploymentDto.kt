package dev.hridaya.kubenexus.data.source.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class DeploymentDto(
    val apiVersion: String? = null,
    val kind: String? = null,
    override val metadata: ObjectMetaDto = ObjectMetaDto(),
    val spec: DeploymentSpecDto = DeploymentSpecDto(),
    val status: DeploymentStatusDto = DeploymentStatusDto(),
) : KubeObjectDto

@Serializable
data class DeploymentSpecDto(
    // The API server defaults omitted replicas to 1; mirroring that here keeps
    // a defaulted Deployment from rendering as scaled to zero.
    val replicas: Int = 1,
    val template: DeploymentTemplateDto = DeploymentTemplateDto(),
    // Newer fields are appended so existing positional constructions stay valid.
    val strategy: DeploymentStrategyDto = DeploymentStrategyDto(),
    val minReadySeconds: Int? = null,
    val selector: DeploymentSelectorDto = DeploymentSelectorDto(),
)

@Serializable
data class DeploymentStrategyDto(
    // "RollingUpdate" or "Recreate"; defaulted by the API server when omitted.
    val type: String? = null,
)

@Serializable
data class DeploymentSelectorDto(
    val matchLabels: Map<String, String> = emptyMap(),
)

@Serializable
data class DeploymentTemplateDto(
    val spec: DeploymentPodSpecDto = DeploymentPodSpecDto(),
)

@Serializable
data class DeploymentPodSpecDto(
    val containers: List<ContainerDto> = emptyList(),
)

@Serializable
data class DeploymentStatusDto(
    val replicas: Int = 0,
    val readyReplicas: Int = 0,
    val availableReplicas: Int = 0,
    val updatedReplicas: Int = 0,
    val conditions: List<DeploymentConditionDto> = emptyList(),
)

@Serializable
data class DeploymentConditionDto(
    val type: String = "",
    val status: String = "",
    val lastUpdateTime: String? = null,
    val reason: String? = null,
    val message: String? = null,
)
