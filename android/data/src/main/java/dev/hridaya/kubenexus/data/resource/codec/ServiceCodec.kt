package dev.hridaya.kubenexus.data.resource.codec

import dev.hridaya.kubenexus.data.mapper.toServiceDetails
import dev.hridaya.kubenexus.data.mapper.toServiceSummary
import dev.hridaya.kubenexus.data.resource.JsonResourceCodec
import dev.hridaya.kubenexus.data.source.remote.dto.EventDto
import dev.hridaya.kubenexus.data.source.remote.dto.K8sJson
import dev.hridaya.kubenexus.data.source.remote.dto.ServiceDto
import dev.hridaya.kubenexus.domain.model.ServiceDetails
import dev.hridaya.kubenexus.domain.model.ServiceSummary
import dev.hridaya.kubenexus.domain.model.resource.ResourceKinds

object ServiceCodec : JsonResourceCodec<ServiceDto, ServiceSummary, ServiceDetails>(
    ResourceKinds.Services,
    ServiceDto.serializer(),
) {

    override fun toSummary(dto: ServiceDto): ServiceSummary = dto.toServiceSummary()

    override fun toDetails(objectJson: String, events: List<EventDto>): ServiceDetails =
        K8sJson.decodeFromString(ServiceDto.serializer(), objectJson).toServiceDetails(events)
}
