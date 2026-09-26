package dev.hridaya.kubenexus.core.common.util

/**
 * Canonical Kubernetes name-format checks shared by form validation and bridge
 * guards, so every layer rejects the same shapes for the same reason.
 */
object K8sNames {

    const val MAX_LABEL_LENGTH = 63

    /** DNS-1123 label: lowercase alphanumerics and hyphens, no leading/trailing hyphen. */
    private val DNS_1123_LABEL = Regex("^[a-z0-9]([a-z0-9-]*[a-z0-9])?$")

    fun isValidDnsLabel(value: String): Boolean =
        value.length <= MAX_LABEL_LENGTH && DNS_1123_LABEL.matches(value)

    /**
     * DNS-1035 labels are like DNS-1123 labels but must start with a letter,
     * which is the shape Kubernetes requires for Service and Pod names.
     */
    private val DNS_1035_LABEL = Regex("^[a-z]([-a-z0-9]*[a-z0-9])?$")

    fun isValidDns1035Label(value: String): Boolean =
        value.length <= MAX_LABEL_LENGTH && DNS_1035_LABEL.matches(value)

    /**
     * Label value: up to 63 characters of alphanumerics, '-', '_' and '.', beginning and ending
     * with an alphanumeric. Unlike DNS labels, uppercase is allowed ("MyApp", "web.v2").
     */
    private val LABEL_VALUE = Regex("^[A-Za-z0-9]([-A-Za-z0-9_.]*[A-Za-z0-9])?$")

    fun isValidLabelValue(value: String): Boolean =
        value.length <= MAX_LABEL_LENGTH && LABEL_VALUE.matches(value)
}
