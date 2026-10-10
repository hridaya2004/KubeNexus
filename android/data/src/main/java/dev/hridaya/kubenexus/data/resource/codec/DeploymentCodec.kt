package dev.hridaya.kubenexus.data.resource.codec

import dev.hridaya.kubenexus.data.mapper.toDeploymentDetails
import dev.hridaya.kubenexus.data.mapper.toDeploymentSummary
import dev.hridaya.kubenexus.data.resource.JsonResourceCodec
import dev.hridaya.kubenexus.data.source.remote.dto.DeploymentDto
import dev.hridaya.kubenexus.data.source.remote.dto.EventDto
import dev.hridaya.kubenexus.data.source.remote.dto.K8sJson
import dev.hridaya.kubenexus.domain.model.DeploymentDetails
import dev.hridaya.kubenexus.domain.model.DeploymentSummary
import dev.hridaya.kubenexus.domain.model.resource.ResourceKinds

object DeploymentCodec : JsonResourceCodec<DeploymentDto, DeploymentSummary, DeploymentDetails>(
    ResourceKinds.Deployments,
    DeploymentDto.serializer(),
) {

    override fun toSummary(dto: DeploymentDto): DeploymentSummary = dto.toDeploymentSummary()

    override fun toDetails(objectJson: String, events: List<EventDto>): DeploymentDetails =
        K8sJson.decodeFromString(DeploymentDto.serializer(), objectJson).toDeploymentDetails(events)
}
