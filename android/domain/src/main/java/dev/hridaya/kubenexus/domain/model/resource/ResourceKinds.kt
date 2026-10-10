package dev.hridaya.kubenexus.domain.model.resource

import dev.hridaya.kubenexus.domain.model.DeploymentDetails
import dev.hridaya.kubenexus.domain.model.DeploymentSummary
import dev.hridaya.kubenexus.domain.model.Pod
import dev.hridaya.kubenexus.domain.model.PodDetails
import dev.hridaya.kubenexus.domain.model.ServiceDetails
import dev.hridaya.kubenexus.domain.model.ServiceSummary

/** The resource kinds the app models. */
object ResourceKinds {

    val Pods = ResourceKind<Pod, PodDetails>(
        ResourceType(group = "", version = "v1", plural = "pods", kind = "Pod"),
    )

    val Deployments = ResourceKind<DeploymentSummary, DeploymentDetails>(
        ResourceType(group = "apps", version = "v1", plural = "deployments", kind = "Deployment"),
    )

    val Services = ResourceKind<ServiceSummary, ServiceDetails>(
        ResourceType(group = "", version = "v1", plural = "services", kind = "Service"),
    )
}
