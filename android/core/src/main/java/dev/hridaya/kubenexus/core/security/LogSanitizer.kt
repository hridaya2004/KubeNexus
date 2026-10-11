package dev.hridaya.kubenexus.core.security

/**
 * Sanitizes log messages, error traces, and diagnostic output to prevent credentials,
 * tokens, certificates, and private keys from ever being written to logs or displayed in error traces.
 *
 * Redaction is pattern based, so it is a safety net rather than a guarantee: it catches the
 * credential shapes that Kubernetes clients and common workloads print, not arbitrary secrets.
 */
object LogSanitizer {

    private const val REDACTED = "[REDACTED]"

    /**
     * Keys whose values are credentials, in YAML (`key: value`), JSON (`"key": "value"`),
     * query-string and CLI (`key=value`, `--key=value`) forms.
     */
    private val SECRET_KEYS = listOf(
        "access[_-]?token",
        "refresh[_-]?token",
        "id[_-]?token",
        "auth[_-]?token",
        "bearer[_-]?token",
        "token",
        "password",
        "passwd",
        "pwd",
        "client[_-]?secret",
        "secret",
        "api[_-]?key",
        "apikey",
        "private[_-]?key",
        "aws[_-]?secret[_-]?access[_-]?key",
        "client-certificate-data",
        "client-key-data",
        "certificate-authority-data",
    ).joinToString("|")

    // Keys may carry a prefix (`DB_PASSWORD`, `x-api-key`). `\b` cannot be used: `_` is a word character.
    private const val KEY_PREFIX = """(["']?(?<![A-Za-z0-9])[A-Za-z0-9_.-]*?(?:"""
    private const val KEY_SUFFIX = """)(?![A-Za-z0-9])["']?\s*[:=]\s*)"""

    private val QUOTED_SECRET_VALUE_REGEX =
        Regex("""(?i)$KEY_PREFIX$SECRET_KEYS$KEY_SUFFIX(["'])(.*?)\2""")
    // Catches unquoted values and values whose closing quote is missing (truncated lines). A
    // value may not start with '[', so an already redacted "[REDACTED]" is left alone and
    // sanitizing twice is harmless.
    private val SECRET_VALUE_REGEX =
        Regex("""(?i)$KEY_PREFIX$SECRET_KEYS$KEY_SUFFIX(["']?)([^"'\s,;&}\[\]][^"'\s,;&}\]]*)""")

    private val BEARER_HEADER_REGEX = Regex("""(?i)(Bearer\s+)[A-Za-z0-9\-_.~+/]+=*""")
    private val BASIC_HEADER_REGEX = Regex("""(?i)(Basic\s+)[A-Za-z0-9+/]+=*""")
    private val PEM_BLOCK_REGEX =
        Regex("""-----BEGIN [A-Z0-9\s_-]+-----[\s\S]*?-----END [A-Z0-9\s_-]+-----""")

    /** A PEM block that has been base64 encoded, as in kubeconfig *-data fields and Secrets. */
    private val BASE64_PEM_REGEX = Regex("""LS0tLS1CRUdJT[A-Za-z0-9+/=]*""")

    /** JSON Web Tokens, including Kubernetes service account tokens, wherever they appear. */
    private val JWT_REGEX = Regex("""\beyJ[A-Za-z0-9_-]{8,}\.eyJ[A-Za-z0-9_-]{8,}(?:\.[A-Za-z0-9_-]*)?""")

    private val AWS_ACCESS_KEY_ID_REGEX = Regex("""\b(?:AKIA|ASIA)[0-9A-Z]{16}\b""")

    /** `scheme://user:password@host` for any URL scheme (https, postgres, redis, amqp, ...). */
    private val URL_AUTH_REGEX = Regex("""(?i)\b([a-z][a-z0-9+.-]*://[^:/\s@]+):([^@\s/]+)@""")

    /**
     * Sanitizes the input string by replacing all known sensitive patterns with redacted placeholders.
     */
    fun sanitize(message: String?): String {
        if (message.isNullOrEmpty()) return ""
        var result: String = message
        result = PEM_BLOCK_REGEX.replace(result, "[REDACTED PEM BLOCK]")
        result = QUOTED_SECRET_VALUE_REGEX.replace(result, "$1$2$REDACTED$2")
        result = SECRET_VALUE_REGEX.replace(result, "$1$2$REDACTED")
        result = BEARER_HEADER_REGEX.replace(result, "$1$REDACTED")
        result = BASIC_HEADER_REGEX.replace(result, "$1$REDACTED")
        result = BASE64_PEM_REGEX.replace(result, "[REDACTED BASE64 PEM]")
        result = JWT_REGEX.replace(result, "[REDACTED JWT]")
        result = AWS_ACCESS_KEY_ID_REGEX.replace(result, "[REDACTED AWS KEY ID]")
        result = URL_AUTH_REGEX.replace(result, "$1:$REDACTED@")
        return result
    }

    /**
     * Returns [message] followed by the sanitized stack trace of [throwable].
     *
     * Use this instead of passing the throwable to `android.util.Log`: logcat prints a
     * throwable's message and causes verbatim, which would bypass [sanitize].
     */
    fun withStackTrace(message: String, throwable: Throwable): String =
        "$message\n${sanitize(throwable.stackTraceToString())}"
}
