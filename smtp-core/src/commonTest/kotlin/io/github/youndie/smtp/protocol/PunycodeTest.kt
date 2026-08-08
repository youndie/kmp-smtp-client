package io.github.youndie.smtp.protocol

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Punycode — `docs/rfc/rfc3492.txt`.
 *
 * The vectors are the ones printed in section 7.1 of the RFC, written as code points so that the
 * source file stays ASCII.
 */
class PunycodeTest {
    @Test
    fun `Arabic sample from RFC 3492`() {
        // rfc3492.txt:762
        val input =
            codePoints(
                0x644,
                0x64A,
                0x647,
                0x645,
                0x627,
                0x628,
                0x62A,
                0x643,
                0x644,
                0x645,
                0x648,
                0x634,
                0x639,
                0x631,
                0x628,
                0x64A,
                0x61F,
            )

        assertEquals("egbpdaj6bu4bxfgehfvwxn", Punycode.encode(input))
    }

    @Test
    fun `simplified Chinese sample from RFC 3492`() {
        // rfc3492.txt:766
        val input = codePoints(0x4ED6, 0x4EEC, 0x4E3A, 0x4EC0, 0x4E48, 0x4E0D, 0x8BF4, 0x4E2D, 0x6587)

        assertEquals("ihqwcrb4cv8a8dqg056pqjye", Punycode.encode(input))
    }

    @Test
    fun `Czech sample keeps its ASCII and adds a delimiter`() {
        // rfc3492.txt:770: mixed input keeps the basic characters before a hyphen.
        val input =
            codePoints(
                0x50,
                0x72,
                0x6F,
                0x10D,
                0x70,
                0x72,
                0x6F,
                0x73,
                0x74,
                0x11B,
                0x6E,
                0x65,
                0x6D,
                0x6C,
                0x75,
                0x76,
                0xED,
                0x10D,
                0x65,
                0x73,
                0x6B,
                0x79,
            )

        assertEquals("Proprostnemluvesky-uyb24dma41a", Punycode.encode(input))
    }

    @Test
    fun `an ASCII domain is returned unchanged`() {
        assertEquals("example.com", Idna.toAscii("example.com"))
    }

    @Test
    fun `a non-ASCII label gains the ACE prefix`() {
        // rfc5891.txt: an A-label is the punycode form with the "xn--" prefix. The label itself is
        // the Russian sample of rfc3492.txt:816.
        val label =
            codePoints(
                0x43F,
                0x43E,
                0x447,
                0x435,
                0x43C,
                0x443,
                0x436,
                0x435,
                0x43E,
                0x43D,
                0x438,
                0x43D,
                0x435,
                0x433,
                0x43E,
                0x432,
                0x43E,
                0x440,
                0x44F,
                0x442,
                0x43F,
                0x43E,
                0x440,
                0x443,
                0x441,
                0x441,
                0x43A,
                0x438,
            )

        // The RFC prints one letter capitalised: that is the optional mixed-case annotation, which
        // records the case of the original. An all-lowercase Cyrillic label has no case to record,
        // and a domain name is case-insensitive anyway.
        assertEquals("xn--b1abfaaepdrnnbgefbadotcwatmq2g4l.com", Idna.toAscii("$label.com"))
    }

    private fun codePoints(vararg points: Int): String = buildString { points.forEach { appendCodePointCompat(it) } }

    private fun StringBuilder.appendCodePointCompat(codePoint: Int) {
        if (codePoint <= 0xFFFF) {
            append(codePoint.toChar())
        } else {
            val offset = codePoint - 0x10000
            append(((offset shr 10) + 0xD800).toChar())
            append(((offset and 0x3FF) + 0xDC00).toChar())
        }
    }
}
