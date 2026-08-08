package io.github.youndie.smtp.mime

import io.github.youndie.smtp.protocol.Mailbox
import io.github.youndie.smtp.protocol.SmtpProtocolException
import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Building a message — `docs/rfc/rfc5322.txt` and the MIME set (`docs/rfc/rfc2045.txt` onwards).
 *
 * This is a different specification from the envelope: 5321 says who the message is for, 5322 says
 * what is inside it, and the two are not required to agree.
 */
class MessageBuilderTest {
    @Test
    fun `a plain message carries the headers RFC 5322 requires`() {
        // rfc5322.txt: Date and From are the two mandatory fields.
        val lines = build { text = "hello" }

        assertTrue(lines.contains("From: <sender@example.com>"), lines.toString())
        assertTrue(lines.contains("To: <rcpt@example.com>"))
        assertTrue(lines.contains("Subject: Hi"))
        assertTrue(lines.any { it.startsWith("Date: ") })
        assertTrue(lines.any { it.startsWith("Message-ID: <") })
        assertTrue(lines.contains("MIME-Version: 1.0"))
    }

    @Test
    fun `the date is written the way RFC 5322 spells it`() {
        // rfc5322.txt section 3.3: day-of-week ", " day month year time zone.
        val lines = build { text = "hello" }

        assertEquals("Date: Sat, 8 Aug 2026 21:04:05 +0000", lines.single { it.startsWith("Date: ") })
    }

    @Test
    fun `a text-only message is one part`() {
        val lines = build { text = "hello" }

        assertTrue(lines.contains("Content-Type: text/plain; charset=utf-8"))
        assertTrue(lines.contains("hello"))
        assertTrue(lines.none { it.startsWith("Content-Type: multipart/") }, "no need for parts here")
    }

    @Test
    fun `text and html travel as alternatives`() {
        // rfc2046.txt: multipart/alternative, least capable form first, so a reader that stops at
        // the first part it understands still gets something readable.
        val lines =
            build {
                text = "hello"
                html = "<p>hello</p>"
            }

        val contentType = lines.single { it.startsWith("Content-Type: multipart/alternative") }
        val boundary = contentType.substringAfter("boundary=\"").substringBefore('"')

        assertEquals(2, lines.count { it == "--$boundary" })
        assertTrue(lines.contains("--$boundary--"), "the closing delimiter has two trailing dashes")
        assertTrue(lines.indexOf("Content-Type: text/plain; charset=utf-8") < lines.indexOf("Content-Type: text/html; charset=utf-8"))
    }

    @Test
    fun `an attachment turns the message into multipart mixed`() {
        val lines =
            build {
                text = "see attached"
                attach(
                    fileName = "notes.txt",
                    contentType = "text/plain",
                    content = "a".repeat(200).encodeToByteArray(),
                )
            }

        assertTrue(lines.any { it.startsWith("Content-Type: multipart/mixed") })
        assertTrue(lines.contains("Content-Disposition: attachment; filename=\"notes.txt\""))
        assertTrue(lines.contains("Content-Transfer-Encoding: base64"))
    }

    @Test
    fun `base64 content is wrapped at 76 characters`() {
        // rfc2045.txt: base64 lines are at most 76 characters. Kotlin's Base64.Mime does exactly
        // that, which is why it is used instead of the default encoder.
        val lines =
            build {
                text = "see attached"
                attach(
                    fileName = "big.bin",
                    contentType = "application/octet-stream",
                    content = ByteArray(500) { it.toByte() },
                )
            }

        val encoded = lines.filter { it.matches(Regex("^[A-Za-z0-9+/=]+$")) && it.length > 40 }

        assertTrue(encoded.isNotEmpty(), "there should be encoded content")
        assertTrue(encoded.all { it.length <= 76 }, "longest was ${encoded.maxOf { it.length }}")
    }

    @Test
    fun `a non-ASCII subject is written as an encoded word`() {
        // rfc2047.txt: a header value is ASCII, so anything else is encoded in place.
        val lines = build { subject = "Привет"; text = "hello" }

        val header = lines.single { it.startsWith("Subject: ") }
        assertTrue(header.startsWith("Subject: =?utf-8?B?"), header)
        assertTrue(header.endsWith("?="), header)
    }

    @Test
    fun `an ASCII subject is left alone`() {
        val lines = build { subject = "Plain subject"; text = "hello" }

        assertEquals("Subject: Plain subject", lines.single { it.startsWith("Subject: ") })
    }

    @Test
    fun `a long header is folded`() {
        // rfc5322.txt section 2.2.3: long header fields are split on white space, and the
        // continuation lines start with white space.
        val lines = build { subject = (1..20).joinToString(" ") { "word$it" }; text = "hello" }

        val start = lines.indexOfFirst { it.startsWith("Subject: ") }
        assertTrue(lines[start].length <= 78, "header line was ${lines[start].length}")
        assertTrue(lines[start + 1].startsWith(" ") || lines[start + 1].startsWith("\t"))
    }

    @Test
    fun `a line break in a header value is refused`() {
        // Without this a subject is a way to add headers of somebody else's choosing — or to end
        // the message early.
        assertFailsWith<SmtpProtocolException> {
            build { subject = "Hi\r\nBcc: evil@example.com"; text = "hello" }
        }
    }

    @Test
    fun `the body is not dot-stuffed here`() {
        // Transparency belongs to the transfer (docs/rfc/rfc5321.txt:3423) and is applied by
        // MailData. Doing it twice would leave a doubled period in the delivered message.
        val lines = build { text = ".hidden" }

        assertTrue(lines.contains(".hidden"), lines.toString())
    }

    private fun build(block: MessageBuilder.() -> Unit): List<String> =
        MessageBuilder(
            from = Mailbox.parse("sender@example.com"),
            to = listOf(Mailbox.parse("rcpt@example.com")),
        ).apply {
            subject = "Hi"
            block()
        }.build(
            sentAt = Instant.fromEpochSeconds(FIXED_TIME),
            messageIdDomain = "example.com",
            messageIdSource = { "fixed-id" },
            boundarySource = { "BOUNDARY$it" },
        )

    private companion object {
        /** 2026-08-08T21:04:05Z — fixed so that the date test can compare a whole line. */
        const val FIXED_TIME = 1786331045L
    }
}
