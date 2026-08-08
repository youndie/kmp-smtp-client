package io.github.youndie.smtp.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Streaming reply reading.
 *
 * It exists because of `docs/rfc/rfc2920.txt:177`: under PIPELINING replies are matched to
 * commands by counting, and matching on the reply code or text is expressly forbidden. So the
 * reader has to hand out replies in order while knowing nothing about commands.
 */
class SmtpReplyReaderTest {
    @Test
    fun `a multiline reply is handed out only on its final line`() {
        val reader = SmtpReplyReader()

        assertNull(reader.feed("250-smtp.example.com"))
        assertNull(reader.feed("250-PIPELINING"))

        val reply = reader.feed("250 SIZE 35882577")

        assertEquals(SmtpReplyCode(250), reply?.code)
        assertEquals(listOf("smtp.example.com", "PIPELINING", "SIZE 35882577"), reply?.lines)
    }

    @Test
    fun `one reader hands out several replies in a row`() {
        // rfc2920.txt:177: "Command statuses MUST be coordinated with responses by counting each
        // separate response". Three replies to three commands — three non-null returns.
        val reader = SmtpReplyReader()
        val replies = listOf("250 OK", "250 Accepted", "354 End data with <CRLF>.<CRLF>").mapNotNull(reader::feed)

        assertEquals(3, replies.size)
        assertEquals(listOf(250, 250, 354), replies.map { it.code.value })
    }

    @Test
    fun `a code change in the middle of a reply is a protocol error`() {
        // rfc5321.txt:2771
        val reader = SmtpReplyReader()
        reader.feed("250-smtp.example.com")

        assertFailsWith<SmtpProtocolException> { reader.feed("550 PIPELINING") }
    }

    @Test
    fun `the reader knows whether a reply is complete`() {
        val reader = SmtpReplyReader()

        assertTrue(reader.isIdle)

        reader.feed("250-smtp.example.com")
        assertFalse(reader.isIdle)

        reader.feed("250 SIZE 35882577")
        assertTrue(reader.isIdle)
    }

    @Test
    fun `an over-long line is a protocol error here too`() {
        // rfc5321.txt:3504
        val reader = SmtpReplyReader()

        assertFailsWith<SmtpProtocolException> { reader.feed("250 " + "x".repeat(510)) }
    }

    @Test
    fun `parsing a whole reply goes through the same reader`() {
        val lines = listOf("250-smtp.example.com", "250 SIZE 35882577")

        assertEquals(SmtpReply.parse(lines), SmtpReplyReader().let { lines.mapNotNull(it::feed).single() })
    }
}
