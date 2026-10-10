package dev.hridaya.kubenexus.data.resource

import dev.hridaya.kubenexus.core.common.result.AppError
import dev.hridaya.kubenexus.core.common.result.Result
import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings
import org.snakeyaml.engine.v2.exceptions.YamlEngineException

/** One object of a manifest: the type it declares and its own text. */
data class ManifestDocument(
    val apiVersion: String,
    val kind: String,
    val text: String,
)

/**
 * Splits a manifest into its documents and reads what each one declares.
 *
 * The Go core creates exactly one object per call, so a multi-document manifest
 * (a Deployment plus its Service, say) is split here. Each document keeps its
 * original text, so what the user reviewed is exactly what is sent.
 */
object ManifestParser {

    private val DOCUMENT_SEPARATOR = Regex("(?m)^---\\s*$")

    fun parse(manifest: String): Result<List<ManifestDocument>> {
        val documents = mutableListOf<ManifestDocument>()
        for (text in DOCUMENT_SEPARATOR.split(manifest).map(String::trim).filter(String::isNotEmpty)) {
            val content = try {
                Load(LoadSettings.builder().setLabel("manifest").build()).loadFromString(text)
            } catch (e: YamlEngineException) {
                return invalid("The manifest is not valid YAML: ${e.message?.lineSequence()?.firstOrNull().orEmpty()}")
            }
            when (content) {
                // A document holding only comments.
                null -> continue
                is Map<*, *> -> documents += ManifestDocument(
                    apiVersion = content["apiVersion"]?.toString().orEmpty(),
                    kind = content["kind"]?.toString().orEmpty(),
                    text = text,
                )
                else -> return invalid("Each document in the manifest must be an object")
            }
        }
        return if (documents.isEmpty()) invalid("The manifest is empty") else Result.Success(documents)
    }

    private fun invalid(message: String): Result<Nothing> = Result.Error(AppError.Validation(message))
}
