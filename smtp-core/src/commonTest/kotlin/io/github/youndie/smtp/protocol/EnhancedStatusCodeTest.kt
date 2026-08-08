package io.github.youndie.smtp.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Enhanced status codes.
 *
 * Syntax — `docs/rfc/rfc3463.txt:128`; where they appear in a reply and what they must agree with
 * — `docs/rfc/rfc2034.txt:100`.
 */
class EnhancedStatusCodeTest {
    @Test
    fun `the enhanced code is read from the first word of the text`() {
        // rfc2034.txt:117: in a multiline reply the same code starts the text of every line.
        val reply = SmtpReply.parse(listOf("250 2.1.0 Sender ok"))

        assertEquals(EnhancedStatusCode(2, 1, 0), reply.enhancedStatus)
    }

    @Test
    fun `a permanent refusal carries a class 5 code`() {
        val reply = SmtpReply.parse(listOf("550 5.7.1 Relay access denied"))

        assertEquals(EnhancedStatusCode(5, 7, 1), reply.enhancedStatus)
        assertEquals("5.7.1", reply.enhancedStatus.toString())
    }

    @Test
    fun `the enhanced class must agree with the first digit of the reply`() {
        // rfc2034.txt:105: "a 2xx response must incorporate a 2.X.X code". A disagreeing value is
        // not a code but plain text, and passing it off as a code is worse than missing it.
        assertNull(SmtpReply.parse(listOf("250 5.1.1 some odd text")).enhancedStatus)
    }

    @Test
    fun `3xx replies carry no enhanced code`() {
        // rfc2034.txt:101: "Note that 3xx responses are NOT included in this list."
        assertNull(SmtpReply.parse(listOf("354 End data with <CRLF>.<CRLF>")).enhancedStatus)
        assertNull(SmtpReply.parse(listOf("354 3.1.1 an invented code")).enhancedStatus)
    }

    @Test
    fun `ordinary text is not mistaken for an enhanced code`() {
        assertNull(SmtpReply.parse(listOf("250 Ok")).enhancedStatus)
        assertNull(SmtpReply.parse(listOf("220 smtp.example.com ESMTP")).enhancedStatus)
        assertNull(SmtpReply.parse(listOf("250")).enhancedStatus)
    }

    @Test
    fun `leading zeros in sub-codes are rejected`() {
        // rfc3463.txt:138: "MUST be expressed without leading zero digits".
        assertNull(SmtpReply.parse(listOf("250 2.01.0 Ok")).enhancedStatus)
        assertEquals(EnhancedStatusCode(2, 0, 0), SmtpReply.parse(listOf("250 2.0.0 Ok")).enhancedStatus)
    }

    @Test
    fun `a sub-code longer than three digits is not an enhanced code`() {
        // rfc3463.txt:128: subject and detail are 1*3digit.
        assertNull(SmtpReply.parse(listOf("250 2.1234.0 Ok")).enhancedStatus)
        assertEquals(EnhancedStatusCode(2, 123, 456), SmtpReply.parse(listOf("250 2.123.456 Ok")).enhancedStatus)
    }

    @Test
    fun `in a multiline reply the code is taken from the first line`() {
        val reply =
            SmtpReply.parse(
                listOf(
                    "550-5.7.1 Relay access denied",
                    "550 5.7.1 Contact your administrator",
                ),
            )

        assertEquals(EnhancedStatusCode(5, 7, 1), reply.enhancedStatus)
    }
}
