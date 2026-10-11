package dev.hridaya.kubenexus.domain.model

/** Where uploaded logs go, shown to the user before they agree to an upload. */
data class LogPasteDestination(
    /** Host the logs are sent to, e.g. "dpaste.org". */
    val name: String,
    /** How long the service keeps an upload, in words, e.g. "7 days". */
    val retention: String,
)
