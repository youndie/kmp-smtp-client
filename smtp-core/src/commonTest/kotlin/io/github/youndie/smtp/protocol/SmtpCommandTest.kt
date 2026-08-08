package io.github.youndie.smtp.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Command serialisation.
 *
 * Command syntax — `docs/rfc/rfc5321.txt:1913` onwards; the command line limit —
 * `docs/rfc/rfc5321.txt:3498`.
 */
class SmtpCommandTest {
    @Test
    fun `a command ends with CRLF`() {
        // rfc5321.txt:763: CRLF is the only line separator of the protocol.
        assertEquals("EHLO client.example.com\r\n", SmtpCommand.Ehlo("client.example.com").encode())
    }

    @Test
    fun `MAIL FROM carries no space after the colon`() {
        // rfc5321.txt:1913: mail = "MAIL FROM:" Reverse-path [SP Mail-parameters] CRLF — the colon
        // belongs to the literal, and no space follows it.
        val command = SmtpCommand.MailFrom(Mailbox.parse("user@example.com"))

        assertEquals("MAIL FROM:<user@example.com>\r\n", command.encode())
    }

    @Test
    fun `the null reverse path is written as empty angle brackets`() {
        // rfc5321.txt:2260: Reverse-path = Path / "<>". This is how bounce messages are sent.
        assertEquals("MAIL FROM:<>\r\n", SmtpCommand.MailFrom(sender = null).encode())
    }

    @Test
    fun `RCPT TO carries a single recipient`() {
        val command = SmtpCommand.RcptTo(Mailbox.parse("user@example.com"))

        assertEquals("RCPT TO:<user@example.com>\r\n", command.encode())
    }

    @Test
    fun `parameters are separated by spaces`() {
        val command =
            SmtpCommand.MailFrom(
                sender = Mailbox.parse("user@example.com"),
                parameters = listOf("SIZE=1000", "BODY=8BITMIME"),
            )

        assertEquals("MAIL FROM:<user@example.com> SIZE=1000 BODY=8BITMIME\r\n", command.encode())
    }

    @Test
    fun `commands without arguments serialise to a single word`() {
        assertEquals("DATA\r\n", SmtpCommand.Data.encode())
        assertEquals("RSET\r\n", SmtpCommand.Rset.encode())
        assertEquals("QUIT\r\n", SmtpCommand.Quit.encode())
        assertEquals("NOOP\r\n", SmtpCommand.Noop().encode())
        // rfc3207.txt: STARTTLS takes no arguments.
        assertEquals("STARTTLS\r\n", SmtpCommand.StartTls.encode())
    }

    @Test
    fun `AUTH carries the mechanism and the initial response`() {
        // rfc4954.txt:699: auth-command = "AUTH" SP sasl-mech [SP initial-response]
        assertEquals("AUTH PLAIN AGFiYwBkZWY=\r\n", SmtpCommand.Auth("PLAIN", "AGFiYwBkZWY=").encode())
        assertEquals("AUTH LOGIN\r\n", SmtpCommand.Auth("LOGIN").encode())
    }

    @Test
    fun `a command line longer than 512 octets is rejected`() {
        // rfc5321.txt:3498: 512 octets including CRLF, so 510 without it.
        val fits = SmtpCommand.Ehlo("d".repeat(510 - "EHLO ".length))
        assertEquals(510 + 2, fits.encode().length)

        val tooLong = SmtpCommand.Ehlo("d".repeat(510 - "EHLO ".length + 1))
        assertFailsWith<SmtpProtocolException> { tooLong.encode() }
    }

    @Test
    fun `for AUTH the limit can be checked in advance`() {
        // rfc4954.txt:208: "If use of the initial response argument would cause the AUTH command
        // to exceed this length, the client MUST NOT use the initial response parameter".
        // OAuth tokens hit this for real, so it has to be asked before sending.
        assertTrue(SmtpCommand.Auth("PLAIN", "AGFiYwBkZWY=").fitsLineLimit)
        assertFalse(SmtpCommand.Auth("XOAUTH2", "x".repeat(600)).fitsLineLimit)
    }

    @Test
    fun `the command line limit is measured in octets`() {
        // rfc5321.txt:3498 states the limit in octets; with SMTPUTF8 (rfc6531.txt) a command can
        // be non-ASCII.
        assertFalse(SmtpCommand.Ehlo("я".repeat(300)).fitsLineLimit)
    }

    @Test
    fun `a line break inside a command argument is rejected`() {
        // Otherwise the client hands the server an extra command with its own hands.
        assertFailsWith<SmtpProtocolException> { SmtpCommand.Ehlo("client\r\nQUIT").encode() }
        assertFailsWith<SmtpProtocolException> { SmtpCommand.Auth("PLAIN", "dGVzdA==\r\nQUIT").encode() }
        assertFailsWith<SmtpProtocolException> {
            SmtpCommand.MailFrom(Mailbox.parse("user@example.com"), listOf("SIZE=1\r\nQUIT")).encode()
        }
    }
}
