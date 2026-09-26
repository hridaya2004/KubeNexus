package dev.hridaya.kubenexus.core.common.util

/**
 * Single rendering path for guided-creation manifests: serializes an ordered
 * structure (LinkedHashMap/listOf trees whose insertion order defines key
 * order) into the exact text shown in the review step and applied verbatim
 * through the bridge.
 *
 * Output is always block-style YAML with two-space indentation and exactly one
 * trailing newline. The API server reads it with a YAML 1.1 parser, where plain
 * `yes`, `no`, `on`, `off`, `null`, `123` or `1:20` are booleans, nulls or
 * numbers rather than strings, so a string is left plain only when it cannot be
 * read as anything else; otherwise it is double-quoted. A Deployment named `yes`
 * or a namespace `123` therefore stays a string.
 */
fun renderK8sManifest(manifest: Map<String, Any>): String =
    buildString { appendMap(manifest, indent = 0) }.trimEnd('\n') + "\n"

private fun StringBuilder.appendMap(map: Map<*, *>, indent: Int) {
    for ((key, value) in map) {
        append(" ".repeat(indent)).append(yamlScalar(key.toString())).append(':')
        appendValue(value, indent)
    }
}

/** Appends [value] after a `key:` already written at [indent]. */
private fun StringBuilder.appendValue(value: Any?, indent: Int) {
    when {
        value is Map<*, *> && value.isNotEmpty() -> {
            append('\n')
            appendMap(value, indent + 2)
        }

        value is List<*> && value.isNotEmpty() -> {
            append('\n')
            appendList(value, indent + 2)
        }

        else -> append(' ').append(scalarOrEmpty(value)).append('\n')
    }
}

private fun StringBuilder.appendList(list: List<*>, indent: Int) {
    for (item in list) {
        append(" ".repeat(indent)).append("- ")
        when {
            item is Map<*, *> && item.isNotEmpty() -> {
                // The first entry shares the dash's line; the rest align under it.
                val entries = item.entries.toList()
                val (firstKey, firstValue) = entries.first()
                append(yamlScalar(firstKey.toString())).append(':')
                appendValue(firstValue, indent + 2)
                appendMap(entries.drop(1).associate { it.key to it.value }, indent + 2)
            }

            item is List<*> && item.isNotEmpty() -> {
                append('\n')
                appendList(item, indent + 2)
            }

            else -> append(scalarOrEmpty(item)).append('\n')
        }
    }
}

private fun scalarOrEmpty(value: Any?): String = when (value) {
    null -> "null"
    is Map<*, *> -> "{}"
    is List<*> -> "[]"
    is Boolean, is Int, is Long, is Short, is Byte -> value.toString()
    is Double, is Float -> value.toString()
    else -> yamlScalar(value.toString())
}

/** Characters that never need quoting in a plain YAML scalar. */
private val PLAIN_SAFE = Regex("""[A-Za-z_][A-Za-z0-9._/:@+=-]*""")

/** Plain scalars YAML 1.1 resolves to booleans or null. */
private val YAML11_RESERVED = setOf(
    "y", "yes", "n", "no", "true", "false", "on", "off", "null", "~",
)

/**
 * Returns [text] as a YAML scalar: plain when it can only mean that string, double-quoted
 * otherwise. Plain values start with a letter or underscore (so nothing numeric, dated or
 * sexagesimal), contain no spaces or indicator characters, do not end with ':' and are not
 * one of YAML 1.1's boolean or null spellings.
 */
internal fun yamlScalar(text: String): String {
    val plain = PLAIN_SAFE.matches(text) &&
        !text.endsWith(':') &&
        text.lowercase() !in YAML11_RESERVED
    return if (plain) text else doubleQuoted(text)
}

private fun doubleQuoted(text: String): String = buildString(text.length + 2) {
    append('"')
    for (ch in text) {
        when (ch) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (ch < ' ' || ch == '\u007F') {
                append("\\u").append(ch.code.toString(16).padStart(4, '0'))
            } else {
                append(ch)
            }
        }
    }
    append('"')
}
