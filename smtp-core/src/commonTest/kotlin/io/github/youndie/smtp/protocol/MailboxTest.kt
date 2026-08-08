package io.github.youndie.smtp.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Envelope addresses and paths.
 *
 * ABNF — `docs/rfc/rfc5321.txt:2314` (Mailbox) and `:2264` (Path); length limits —
 * `docs/rfc/rfc5321.txt:3484`, `:3489`, `:3493`.
 */
class MailboxTest {
    @Test
    fun `an address parses into a local part and a domain`() {
        val mailbox = Mailbox.parse("user@example.com")

        assertEquals("user", mailbox.localPart)
        assertEquals("example.com", mailbox.domain)
        assertEquals("user@example.com", mailbox.address)
    }

    @Test
    fun `a path is wrapped in angle brackets`() {
        // rfc5321.txt:2264: Path = "<" [ A-d-l ":" ] Mailbox ">"
        assertEquals("<user@example.com>", Mailbox.parse("user@example.com").path)
    }

    @Test
    fun `the case of the local part is preserved`() {
        // rfc5321.txt:2316: "Local-part ... MAY be case-sensitive". Lower-casing it is a silent
        // corruption of somebody's address.
        val mailbox = Mailbox.parse("John.Doe@Example.COM")

        assertEquals("John.Doe", mailbox.localPart)
        assertEquals("Example.COM", mailbox.domain)
    }

    @Test
    fun `the at sign is searched for from the end`() {
        // A quoted string in the local part may contain '@' (rfc5321.txt:2322).
        val mailbox = Mailbox.parse("\"weird@local\"@example.com")

        assertEquals("\"weird@local\"", mailbox.localPart)
        assertEquals("example.com", mailbox.domain)
    }

    @Test
    fun `a local part longer than 64 octets is rejected`() {
        // rfc5321.txt:3484
        Mailbox.parse("${"a".repeat(64)}@example.com")

        assertFailsWith<SmtpProtocolException> { Mailbox.parse("${"a".repeat(65)}@example.com") }
    }

    @Test
    fun `a domain longer than 255 octets is rejected`() {
        // rfc5321.txt:3489
        assertFailsWith<SmtpProtocolException> { Mailbox.parse("user@${"d".repeat(256)}") }
    }

    @Test
    fun `a path longer than 256 octets is rejected even when both parts are legal`() {
        // rfc5321.txt:3493: the path limit counts "including the punctuation and element
        // separators". 64 + 1 + 200 + 2 = 267: both parts fit, the path does not.
        assertFailsWith<SmtpProtocolException> {
            Mailbox.parse("${"a".repeat(64)}@${"d".repeat(200)}")
        }
    }

    @Test
    fun `the limits are measured in octets rather than characters`() {
        // Same place; with SMTPUTF8 (rfc6531.txt) an address can be non-ASCII, and 40 Cyrillic
        // characters are 80 octets.
        assertFailsWith<SmtpProtocolException> { Mailbox.parse("${"я".repeat(40)}@example.com") }
    }

    @Test
    fun `a line break inside an address is rejected`() {
        // Otherwise an address becomes a way to append a command: everything after the CRLF is
        // read by the server as the next command (rfc5321.txt:763 — CRLF separates protocol lines).
        assertFailsWith<SmtpProtocolException> { Mailbox.parse("user\r\nRCPT TO:<evil@example.com>@example.com") }
        assertFailsWith<SmtpProtocolException> { Mailbox.parse("user@example.com\nDATA") }
    }

    @Test
    fun `angle brackets inside an address are rejected`() {
        assertFailsWith<SmtpProtocolException> { Mailbox.parse("user<@example.com") }
        assertFailsWith<SmtpProtocolException> { Mailbox.parse("user@example.com>") }
    }

    @Test
    fun `an address without an at sign or without one of its parts is rejected`() {
        assertFailsWith<SmtpProtocolException> { Mailbox.parse("user") }
        assertFailsWith<SmtpProtocolException> { Mailbox.parse("user@") }
        assertFailsWith<SmtpProtocolException> { Mailbox.parse("@example.com") }
        assertFailsWith<SmtpProtocolException> { Mailbox.parse("") }
    }
}
