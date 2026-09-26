package dev.hridaya.kubenexus.data.source.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Typed views over the verbatim Kubernetes JSON returned by the Go core's
 * generic resource methods (`listJSON`, `getJSON`).
 *
 * These replace the flattened Gomobile structs that used to live in the Go
 * package. Gomobile cannot bind maps, slices of structs or nested objects, which
 * forced workarounds such as a `labelsJSON` string and a `volumesCSV` string.
 * Declaring the shape here instead restores real `Map` and `List` types, real
 * nullability, and `data class` semantics, while keeping identical autocomplete.
 *
 * Every property carries a default so that a field the cluster omits, or a field
 * added by a newer Kubernetes version, cannot fail decoding. Unknown keys are
 * ignored by the [dev.hridaya.kubenexus.data.source.remote.dto.K8sJson]
 * configuration; the corresponding strict-decode test guards against Go and
 * Kotlin drifting apart.
 */

@Serializable
data class ObjectMetaDto(
    val name: String = "",
    val namespace: String = "",
    val uid: String? = null,
    val resourceVersion: String? = null,
    val creationTimestamp: String? = null,
    val deletionTimestamp: String? = null,
    val labels: Map<String, String> = emptyMap(),
    val annotations: Map<String, String> = emptyMap(),
)

@Serializable
data class ListMetaDto(
    val resourceVersion: String? = null,
    // `continue` is a Kotlin keyword, so the property is renamed.
    @SerialName("continue")
    val continueToken: String? = null,
    val remainingItemCount: Long? = null,
)

@Serializable
data class ContainerDto(
    val name: String = "",
    val image: String = "",
    val ports: List<ContainerPortDto> = emptyList(),
)

@Serializable
data class ContainerPortDto(
    val name: String? = null,
    val containerPort: Int = 0,
    val protocol: String? = null,
)

@Serializable
data class ContainerStateRunningDto(
    val startedAt: String? = null,
)

@Serializable
data class ContainerStateWaitingDto(
    val reason: String? = null,
    val message: String? = null,
)

@Serializable
data class ContainerStateTerminatedDto(
    val exitCode: Int = 0,
    val reason: String? = null,
    val message: String? = null,
    val finishedAt: String? = null,
)

@Serializable
data class ContainerStateDto(
    val running: ContainerStateRunningDto? = null,
    val waiting: ContainerStateWaitingDto? = null,
    val terminated: ContainerStateTerminatedDto? = null,
)

@Serializable
data class ContainerStatusDto(
    val name: String = "",
    val image: String = "",
    val ready: Boolean = false,
    val restartCount: Int = 0,
    val started: Boolean? = null,
    val state: ContainerStateDto? = null,
)

@Serializable
data class VolumeDto(
    val name: String = "",
)

@Serializable
data class PodSpecDto(
    val nodeName: String? = null,
    val restartPolicy: String? = null,
    val serviceAccountName: String? = null,
    val containers: List<ContainerDto> = emptyList(),
    val initContainers: List<ContainerDto> = emptyList(),
    val volumes: List<VolumeDto> = emptyList(),
)

@Serializable
data class PodConditionDto(
    val type: String = "",
    val status: String = "",
    val lastTransitionTime: String? = null,
    val reason: String? = null,
    val message: String? = null,
)

@Serializable
data class PodStatusDto(
    val phase: String = "",
    val reason: String? = null,
    val message: String? = null,
    val podIP: String? = null,
    val hostIP: String? = null,
    val startTime: String? = null,
    val qosClass: String? = null,
    val conditions: List<PodConditionDto> = emptyList(),
    val containerStatuses: List<ContainerStatusDto> = emptyList(),
    val initContainerStatuses: List<ContainerStatusDto> = emptyList(),
)

@Serializable
data class PodDto(
    val apiVersion: String? = null,
    val kind: String? = null,
    val metadata: ObjectMetaDto = ObjectMetaDto(),
    val spec: PodSpecDto = PodSpecDto(),
    val status: PodStatusDto = PodStatusDto(),
)

@Serializable
data class PodListDto(
    val apiVersion: String? = null,
    val kind: String? = null,
    val metadata: ListMetaDto = ListMetaDto(),
    val items: List<PodDto> = emptyList(),
)

@Serializable
data class NamespaceStatusDto(
    val phase: String = "",
)

@Serializable
data class NamespaceDto(
    val apiVersion: String? = null,
    val kind: String? = null,
    val metadata: ObjectMetaDto = ObjectMetaDto(),
    val status: NamespaceStatusDto = NamespaceStatusDto(),
)

@Serializable
data class NamespaceListDto(
    val apiVersion: String? = null,
    val kind: String? = null,
    val metadata: ListMetaDto = ListMetaDto(),
    val items: List<NamespaceDto> = emptyList(),
)

@Serializable
data class EventDto(
    val metadata: ObjectMetaDto = ObjectMetaDto(),
    val type: String = "Normal",
    val reason: String = "",
    val message: String = "",
    val count: Int = 0,
    val firstTimestamp: String? = null,
    val lastTimestamp: String? = null,
    val eventTime: String? = null,
)

@Serializable
data class EventListDto(
    val apiVersion: String? = null,
    val kind: String? = null,
    val metadata: ListMetaDto = ListMetaDto(),
    val items: List<EventDto> = emptyList(),
)
