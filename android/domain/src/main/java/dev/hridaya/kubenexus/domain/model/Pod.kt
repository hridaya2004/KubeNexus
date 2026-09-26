package dev.hridaya.kubenexus.domain.model

import dev.hridaya.kubenexus.core.common.time.K8sTime

enum class PodStatus(val title: String) {
    RUNNING("Running"),
    PENDING("Pending"),
    COMPLETED("Completed"),
    FAILED("Failed"),
    CRASH_LOOP("CrashLoopBackOff"),
    UNKNOWN("Unknown"),
}

data class Pod(
    val id: String,
    val name: String,
    val namespace: String,
    val status: PodStatus = PodStatus.RUNNING,
    val readyContainers: String = "1/1",
    val restarts: Int = 0,
    /**
     * Pod creation time in epoch milliseconds.
     *
     * This replaces the previously stored, pre-formatted `age` string. That string
     * was rendered in Go at sync time and then cached, so it drifted out of date
     * until the next refresh. Holding the timestamp keeps a single source of truth
     * and lets [age] be derived instead.
     */
    val creationTimestampMillis: Long? = null,
    val ip: String? = null,
    val node: String? = null,
    val image: String? = null,
    /**
     * Named container ports (name to port number) across the pod's containers, used to
     * resolve a Service's named targetPort. Only populated from live API responses.
     */
    val namedContainerPorts: Map<String, Int> = emptyMap(),
) {
    /** Human readable age in kubectl's format, derived from [creationTimestampMillis]. */
    val age: String
        get() = K8sTime.formatAge(creationTimestampMillis)
}
