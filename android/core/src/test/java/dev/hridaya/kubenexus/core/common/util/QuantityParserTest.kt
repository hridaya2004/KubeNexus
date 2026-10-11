package dev.hridaya.kubenexus.core.common.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuantityParserTest {

    private fun assertCores(expected: Double, raw: String) {
        val actual = QuantityParser.parseCores(raw)
        assertEquals("parseCores($raw)", expected, actual!!, 1e-12)
    }

    // What metrics-server actually emits: nanocores for CPU, plain bytes or a
    // binary suffix for memory.
    @Test
    fun `parses metrics-server cpu quantities`() {
        assertCores(0.010555728, "10555728n")
        assertCores(0.000512, "512000n")
        assertCores(0.00025, "250u")
        assertCores(0.1, "100m")
        assertCores(1.5, "1500m")
        assertCores(2.0, "2")
    }

    @Test
    fun `parses memory quantities with binary and decimal suffixes`() {
        assertEquals(98234112L, QuantityParser.parseBytes("98234112"))
        assertEquals(65536L, QuantityParser.parseBytes("64Ki"))
        assertEquals(134217728L, QuantityParser.parseBytes("128Mi"))
        assertEquals(1073741824L, QuantityParser.parseBytes("1Gi"))
        assertEquals(2501000L, QuantityParser.parseBytes("2501k"))
        assertEquals(100000000L, QuantityParser.parseBytes("100M"))
    }

    // Ei is 2^60, beyond the 53-bit mantissa of a Double, so these must be
    // computed exactly rather than via floating point.
    @Test
    fun `large binary suffixes are exact`() {
        assertEquals(1099511627776L, QuantityParser.parseBytes("1Ti"))
        assertEquals(1125899906842624L, QuantityParser.parseBytes("1Pi"))
        assertEquals(1152921504606846976L, QuantityParser.parseBytes("1Ei"))
    }

    @Test
    fun `supports the full decimal SI range`() {
        assertEquals(1000000000000000L, QuantityParser.parseBytes("1P"))
        assertEquals(1000000000000000000L, QuantityParser.parseBytes("1E"))
    }

    // The decimal exponent form is valid in the quantity grammar and used to
    // return zero.
    @Test
    fun `parses the decimal exponent form`() {
        assertCores(1000.0, "1e3")
        assertCores(0.0015, "1.5e-3")
        assertEquals(1000000L, QuantityParser.parseBytes("1e6"))
        assertEquals(2000L, QuantityParser.parseBytes("2E3"))
    }

    // "E" alone is exa; "E" followed by digits is an exponent. Both must work.
    @Test
    fun `distinguishes exa from an exponent`() {
        assertEquals(1000000000000000000L, QuantityParser.parseBytes("1E"))
        assertEquals(1000L, QuantityParser.parseBytes("1E3"))
        assertEquals(1152921504606846976L, QuantityParser.parseBytes("1Ei"))
    }

    // Returning null rather than zero is the point: a chart cannot distinguish a
    // parse failure from genuine zero usage.
    @Test
    fun `returns null for unparseable input rather than zero`() {
        assertNull(QuantityParser.parseCores(null))
        assertNull(QuantityParser.parseCores(""))
        assertNull(QuantityParser.parseCores("   "))
        assertNull(QuantityParser.parseCores("abc"))
        assertNull(QuantityParser.parseCores("1..2"))
        assertNull(QuantityParser.parseCores("100Zz"))
        assertNull(QuantityParser.parseBytes("128MiB"))
        assertNull(QuantityParser.parseBytes("--5"))
        assertNull(QuantityParser.parseBytes("1e"))
        assertNull(QuantityParser.parseBytes("Mi"))
    }

    @Test
    fun `handles signs zero and leading decimal points`() {
        assertCores(5.0, "+5")
        assertCores(-0.1, "-100m")
        assertEquals(0L, QuantityParser.parseBytes("0"))
        assertEquals(512L, QuantityParser.parseBytes(".5Ki"))
    }

    @Test
    fun `returns null beyond Long range instead of silently truncating`() {
        assertNull(QuantityParser.parseBytes("999999999999999999999Ei"))
    }

    @Test
    fun `truncates fractional bytes`() {
        assertEquals(1536L, QuantityParser.parseBytes("1.5Ki"))
        assertEquals(1L, QuantityParser.parseBytes("1.9"))
    }

    @Test(timeout = 2_000)
    fun `rejects exponents no real quantity uses`() {
        assertNull(QuantityParser.parseBytes("1e999999999"))
        assertNull(QuantityParser.parseCores("1e-999999999"))
        assertEquals(1_000_000_000_000_000_000L, QuantityParser.parseBytes("1e18"))
    }
}
