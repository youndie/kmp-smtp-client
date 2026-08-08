package io.github.youndie.smtp.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Parsing a server reply.
 *
 * Reply ABNF — `docs/rfc/rfc5321.txt:2597`; meaning of the first digit — `docs/rfc/rfc5321.txt:2642`.
 */
class SmtpReplyTest {
    @Test
    fun `single line reply parses into a code and text`() {
        val reply = SmtpReply.parse(listOf("220 smtp.example.com ESMTP ready"))

        assertEquals(SmtpReplyCode(220), reply.code)
        assertEquals(listOf("smtp.example.com ESMTP ready"), reply.lines)
    }

    @Test
    fun `a 2xx code means positive completion`() {
        // rfc5321.txt:2642: "Positive Completion reply" — neither the second digit nor the text
        // affects the outcome.
        val reply = SmtpReply.parse(listOf("220 smtp.example.com ESMTP ready"))

        assertTrue(reply.isPositiveCompletion)
        assertEquals(SmtpReplySeverity.POSITIVE_COMPLETION, reply.code.severity)
    }

    @Test
    fun `a hyphen after the code continues the reply and a space ends it`() {
        // rfc5321.txt:2597: Reply-line = *( Reply-code "-" [ textstring ] CRLF )
        //                                Reply-code [ SP textstring ] CRLF
        val reply =
            SmtpReply.parse(
                listOf(
                    "250-smtp.example.com",
                    "250-PIPELINING",
                    "250 SIZE 35882577",
                ),
            )

        assertEquals(SmtpReplyCode(250), reply.code)
        assertEquals(listOf("smtp.example.com", "PIPELINING", "SIZE 35882577"), reply.lines)
    }

    @Test
    fun `every line of a multiline reply must carry the same code`() {
        // rfc5321.txt:2771: "In a multiline reply, the reply code on each of the lines MUST be
        // the same."
        assertFailsWith<SmtpProtocolException> {
            SmtpReply.parse(listOf("250-smtp.example.com", "550 PIPELINING"))
        }
    }

    @Test
    fun `a reply without text parses with and without the trailing space`() {
        // rfc5321.txt:2571: "clients that do not receive it SHOULD be prepared to process the
        // code alone (with or without a trailing space character)".
        assertEquals(listOf(""), SmtpReply.parse(listOf("250")).lines)
        assertEquals(listOf(""), SmtpReply.parse(listOf("250 ")).lines)
    }

    @Test
    fun `a first digit outside 2 to 5 is a protocol error`() {
        // rfc5321.txt:2600: Reply-code = %x32-35 %x30-35 %x30-39.
        // rfc5321.txt:2620: a client "SHOULD normally treat as fatal errors" such codes.
        assertFailsWith<SmtpProtocolException> { SmtpReply.parse(listOf("150 no such severity")) }
        assertFailsWith<SmtpProtocolException> { SmtpReply.parse(listOf("650 no such severity")) }
    }

    @Test
    fun `a second digit outside the ABNF is accepted because only the first one counts`() {
        // The ABNF allows 0-5 for the second digit, yet rfc5321.txt:3043 requires a client to
        // handle unknown codes "by interpreting the first digit only". Breaking on someone else's
        // sloppiness costs more than accepting the code.
        val reply = SmtpReply.parse(listOf("260 a code the server made up"))

        assertEquals(SmtpReplyCode(260), reply.code)
        assertTrue(reply.isPositiveCompletion)
    }

    @Test
    fun `a reply line longer than 512 octets is a protocol error`() {
        // rfc5321.txt:3504: a reply line is capped at 512 octets including CRLF, so 510 without it.
        val text = "x".repeat(510 - 4)
        SmtpReply.parse(listOf("250 $text")) // exactly 510 octets — still allowed

        assertFailsWith<SmtpProtocolException> { SmtpReply.parse(listOf("250 ${text}x")) }
    }

    @Test
    fun `the line limit is measured in octets rather than characters`() {
        // Same place, rfc5321.txt:3504: the limit is given in octets. With SMTPUTF8 (rfc6531.txt)
        // reply text can be non-ASCII, and "256 characters" is 512 octets in UTF-8.
        val cyrillic = "я".repeat(300) // 600 octets at a string length of 300

        assertFailsWith<SmtpProtocolException> { SmtpReply.parse(listOf("250 $cyrillic")) }
    }

    @Test
    fun `an empty list of lines is a protocol error`() {
        assertFailsWith<SmtpProtocolException> { SmtpReply.parse(emptyList()) }
    }

    @Test
    fun `a reply cut off on a continuation line does not count as parsed`() {
        assertFailsWith<SmtpProtocolException> {
            SmtpReply.parse(listOf("250-smtp.example.com", "250-PIPELINING"))
        }
    }
}
