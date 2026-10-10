package dev.hridaya.kubenexus.domain.model

import dev.hridaya.kubenexus.domain.model.resource.KubeObjectSummary

/**
 * Read-only summary of a v1 Service for list rendering, mirroring the intent of
 * [DeploymentSummary]: enough for a service card (name, type, ClusterIP,
 * exposed ports, age) without pulling full specs across the JNI boundary.
 */
data class ServiceSummary(
    override val id: String,
    override val name: String,
    override val namespace: String,
    val type: String,

    /** Virtual cluster IP; empty string for headless Services. */
    val clusterIP: String,

    /** Structured port rows so cards never depend on a pre-rendered string. */
    val ports: List<ServicePortDetail>,
    override val creationTimestampMillis: Long,
) : KubeObjectSummary
