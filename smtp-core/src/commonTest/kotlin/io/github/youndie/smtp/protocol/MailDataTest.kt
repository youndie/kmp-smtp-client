package io.github.youndie.smtp.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Transferring the message body.
 *
 * Transparency — `docs/rfc/rfc5321.txt:3423`; the line limit — `docs/rfc/rfc5321.txt:3510`.
 */
class MailDataTest {
    @Test
    fun `a leading period is doubled`() {
        // rfc5321.txt:3423: "If it is a period, one additional period is inserted at the
        // beginning of the line."
        assertEquals("..", MailData.encodeLine("."))
        assertEquals("..text", MailData.encodeLine(".text"))
    }

    @Test
    fun `only the first period is doubled`() {
        assertEquals("...", MailData.encodeLine(".."))
    }

    @Test
    fun `a period elsewhere in the line is left alone`() {
        assertEquals("a.b", MailData.encodeLine("a.b"))
        assertEquals("text.", MailData.encodeLine("text."))
    }

    @Test
    fun `the body ends with a period on a line of its own`() {
        // rfc5321.txt:3423: the body ends with <CRLF>.<CRLF>.
        assertEquals(
            "Subject: hi\r\n\r\nbody\r\n.\r\n",
            MailData.encode(listOf("Subject: hi", "", "body")),
        )
    }

    @Test
    fun `an empty body is a single period`() {
        assertEquals(".\r\n", MailData.encode(emptyList()))
    }

    @Test
    fun `a body line longer than 1000 octets is rejected`() {
        // rfc5321.txt:3510: 1000 octets including CRLF, so 998 without it.
        MailData.encodeLine("x".repeat(998))

        assertFailsWith<SmtpProtocolException> { MailData.encodeLine("x".repeat(999)) }
    }

    @Test
    fun `the limit is measured before the period is doubled`() {
        // rfc5321.txt:3512: "(not counting the leading dot duplicated for transparency)".
        // A 998-character line starting with a period is legal even though it becomes 999 on
        // the wire.
        val line = "." + "x".repeat(997)

        assertEquals(999, MailData.encodeLine(line).length)
    }

    @Test
    fun `the body line limit is measured in octets`() {
        // Same place; with 8BITMIME (rfc6152.txt) and SMTPUTF8 (rfc6531.txt) a body can be
        // non-ASCII.
        assertFailsWith<SmtpProtocolException> { MailData.encodeLine("я".repeat(500)) }
    }

    @Test
    fun `a line break inside a body line is rejected`() {
        // rfc5321.txt:763: CRLF separates protocol lines. A hidden line break is either lost
        // transparency or somebody else's command.
        assertFailsWith<SmtpProtocolException> { MailData.encodeLine("Subject: hi\r\n.\r\nQUIT") }
        assertFailsWith<SmtpProtocolException> { MailData.encodeLine("Subject: hi\nfrom nowhere") }
    }

    @Test
    fun `a lone period inside the body does not truncate the message`() {
        // Exactly the hole transparency exists for: without doubling, the server would accept a
        // truncated message as a complete one.
        val encoded = MailData.encode(listOf("before", ".", "after"))

        assertEquals("before\r\n..\r\nafter\r\n.\r\n", encoded)
    }
}
