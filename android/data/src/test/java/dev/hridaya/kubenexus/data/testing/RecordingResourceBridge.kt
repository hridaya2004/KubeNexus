package dev.hridaya.kubenexus.data.testing

import dev.hridaya.kubenexus.core.common.result.Result
import dev.hridaya.kubenexus.data.nativebridge.FakeKubeNexusNativeBridge
import dev.hridaya.kubenexus.domain.model.resource.ResourceType

/**
 * Records the generic resource calls and answers with configurable results.
 * Setting [errorToThrow] makes every resource call throw instead.
 */
class RecordingResourceBridge : FakeKubeNexusNativeBridge() {

    data class Call(
        val operation: String,
        val kubeconfig: String,
        val type: ResourceType?,
        val namespace: String = "",
        val name: String = "",
        val labelSelector: String = "",
        val manifest: String = "",
    )

    val calls = mutableListOf<Call>()

    var listResult: Result<String> = Result.Success(EMPTY_LIST_JSON)
    var getResult: Result<String> = Result.Success("{}")
    var eventsResult: Result<String> = Result.Success(EMPTY_LIST_JSON)
    var deleteResult: Result<Unit> = Result.Success(Unit)

    /** Answers creates in order; the last answer repeats once they run out. */
    var createResults: List<Result<String>> = listOf(Result.Success("{}"))
    var errorToThrow: Throwable? = null

    override fun listResources(
        rawKubeconfig: String,
        type: ResourceType,
        namespace: String,
        labelSelector: String,
    ): Result<String> = record(
        Call(
            "list",
            rawKubeconfig,
            type,
            namespace,
            labelSelector = labelSelector
        )
    ) { listResult }

    override fun getResource(
        rawKubeconfig: String,
        type: ResourceType,
        namespace: String,
        name: String
    ): Result<String> =
        record(Call("get", rawKubeconfig, type, namespace, name)) { getResult }

    override fun createResource(
        rawKubeconfig: String,
        type: ResourceType,
        manifest: String
    ): Result<String> {
        val index = calls.count { it.operation == "create" }
        return record(Call("create", rawKubeconfig, type, manifest = manifest)) {
            createResults.getOrElse(index) { createResults.last() }
        }
    }

    override fun deleteResource(
        rawKubeconfig: String,
        type: ResourceType,
        namespace: String,
        name: String
    ): Result<Unit> =
        record(Call("delete", rawKubeconfig, type, namespace, name)) { deleteResult }

    override fun listEvents(
        rawKubeconfig: String,
        namespace: String,
        involvedKind: String,
        involvedName: String,
    ): Result<String> =
        record(Call("events", rawKubeconfig, type = null, namespace, involvedName)) { eventsResult }

    private fun <T> record(call: Call, answer: () -> T): T {
        calls += call
        errorToThrow?.let { throw it }
        return answer()
    }
}
