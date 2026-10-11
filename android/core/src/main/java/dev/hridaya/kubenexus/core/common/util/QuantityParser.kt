package dev.hridaya.kubenexus.core.common.util

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

/**
 * Parses Kubernetes resource quantities ("100m", "2501k", "128Mi", "1e3") into
 * plain numbers. CPU quantities resolve to cores; memory quantities to bytes.
 *
 * Returns null rather than zero for anything it cannot parse. A silent zero is
 * indistinguishable from genuine zero usage once it reaches a chart, so callers
 * are forced to decide what an unparseable value means.
 *
 * Follows the quantity grammar in k8s.io/apimachinery/pkg/api/resource:
 *
 *     <quantity>        ::= <signedNumber><suffix>
 *     <suffix>          ::= <binarySI> | <decimalExponent> | <decimalSI>
 *     <binarySI>        ::= Ki | Mi | Gi | Ti | Pi | Ei
 *     <decimalSI>       ::= n | u | m | "" | k | M | G | T | P | E
 *     <decimalExponent> ::= ("e" | "E") <signedNumber>
 *
 * BigDecimal is used throughout because Ei (2^60) exceeds the 53-bit mantissa of
 * a Double and would otherwise be returned inexactly.
 */
object QuantityParser {

    // The exponent group is matched before the suffix group, which disambiguates
    // "1E3" (one thousand) from "1E" (one exa) and "1Ei" (one exbibyte): the
    // exponent form requires digits after the e/E.
    private val QUANTITY =
        Regex("""^([+-]?(?:\d+(?:\.\d+)?|\.\d+))([eE][+-]?\d+)?([A-Za-z]*)$""")

    private const val MAX_EXPONENT = 30

    /** Powers of ten, keyed by decimal SI suffix. */
    private val DECIMAL_SI = mapOf(
        "n" to -9,
        "u" to -6,
        "m" to -3,
        "k" to 3,
        "M" to 6,
        "G" to 9,
        "T" to 12,
        "P" to 15,
        "E" to 18,
    )

    /** Powers of two, keyed by binary SI suffix. */
    private val BINARY_SI = mapOf(
        "Ki" to 10,
        "Mi" to 20,
        "Gi" to 30,
        "Ti" to 40,
        "Pi" to 50,
        "Ei" to 60,
    )

    /** Returns CPU cores, or null when [raw] is absent or unparseable. */
    fun parseCores(raw: String?): Double? = parse(raw)?.toDouble()

    /**
     * Returns whole bytes, or null when [raw] is absent, unparseable, or larger
     * than [Long] can represent. Fractional bytes are truncated.
     */
    fun parseBytes(raw: String?): Long? = parse(raw)?.let { value ->
        try {
            value.setScale(0, RoundingMode.DOWN).longValueExact()
        } catch (_: ArithmeticException) {
            null
        }
    }

    private fun parse(raw: String?): BigDecimal? {
        if (raw.isNullOrBlank()) return null
        val match = QUANTITY.matchEntire(raw.trim()) ?: return null
        val (numberPart, exponentPart, suffix) = match.destructured

        var value = numberPart.toBigDecimalOrNull() ?: return null

        if (exponentPart.isNotEmpty()) {
            val exponent = exponentPart.drop(1).toIntOrNull() ?: return null
            if (exponent !in -MAX_EXPONENT..MAX_EXPONENT) return null
            value = value.scaleByPowerOfTen(exponent)
        }

        return when {
            suffix.isEmpty() -> value
            BINARY_SI.containsKey(suffix) ->
                value.multiply(BigDecimal(BigInteger.valueOf(2).pow(BINARY_SI.getValue(suffix))))

            DECIMAL_SI.containsKey(suffix) ->
                value.scaleByPowerOfTen(DECIMAL_SI.getValue(suffix))

            else -> null
        }
    }
}
