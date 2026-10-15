package dev.hridaya.kubenexus.data.resource.codec

import dev.hridaya.kubenexus.data.mapper.toDetails
import dev.hridaya.kubenexus.data.mapper.toDomain
import dev.hridaya.kubenexus.data.resource.JsonResourceCodec
import dev.hridaya.kubenexus.data.source.remote.dto.EventDto
import dev.hridaya.kubenexus.data.source.remote.dto.K8sJson
import dev.hridaya.kubenexus.data.source.remote.dto.PodDto
import dev.hridaya.kubenexus.domain.model.Pod
import dev.hridaya.kubenexus.domain.model.PodDetails
import dev.hridaya.kubenexus.domain.model.resource.ResourceKinds

object PodCodec :
    JsonResourceCodec<PodDto, Pod, PodDetails>(ResourceKinds.Pods, PodDto.serializer()) {

    override fun toSummary(dto: PodDto): Pod = dto.toDomain()

    /** The verbatim object rides along so the pod screen can show it raw. */
    override fun toDetails(objectJson: String, events: List<EventDto>): PodDetails =
        K8sJson.decodeFromString(PodDto.serializer(), objectJson)
            .toDetails(events = events, rawJson = objectJson)
}
