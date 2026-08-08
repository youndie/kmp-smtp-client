package io.github.youndie.smtp.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Потоковое чтение ответов.
 *
 * Существует ради `docs/rfc/rfc2920.txt:177`: при PIPELINING ответы сопоставляются с командами
 * счётом, а сопоставление по коду ответа или по тексту прямо запрещено. Значит, читатель обязан
 * отдавать ответы подряд, ничего не зная о командах.
 */
class SmtpReplyReaderTest {
    @Test
    fun `многострочный ответ отдаётся только на финальной строке`() {
        val reader = SmtpReplyReader()

        assertNull(reader.feed("250-smtp.example.com"))
        assertNull(reader.feed("250-PIPELINING"))

        val reply = reader.feed("250 SIZE 35882577")

        assertEquals(SmtpReplyCode(250), reply?.code)
        assertEquals(listOf("smtp.example.com", "PIPELINING", "SIZE 35882577"), reply?.lines)
    }

    @Test
    fun `один читатель отдаёт несколько ответов подряд`() {
        // rfc2920.txt:177: "Command statuses MUST be coordinated with responses by counting each
        // separate response". Три ответа на три команды — три вызова, вернувших не-null.
        val reader = SmtpReplyReader()
        val replies = listOf("250 OK", "250 Accepted", "354 End data with <CRLF>.<CRLF>").mapNotNull(reader::feed)

        assertEquals(3, replies.size)
        assertEquals(listOf(250, 250, 354), replies.map { it.code.value })
    }

    @Test
    fun `смена кода посреди ответа — протокольная ошибка`() {
        // rfc5321.txt:2771
        val reader = SmtpReplyReader()
        reader.feed("250-smtp.example.com")

        assertFailsWith<SmtpProtocolException> { reader.feed("550 PIPELINING") }
    }

    @Test
    fun `читатель знает — дочитан ответ или нет`() {
        val reader = SmtpReplyReader()

        assertTrue(reader.isIdle)

        reader.feed("250-smtp.example.com")
        assertFalse(reader.isIdle)

        reader.feed("250 SIZE 35882577")
        assertTrue(reader.isIdle)
    }

    @Test
    fun `слишком длинная строка — протокольная ошибка и здесь`() {
        // rfc5321.txt:3504
        val reader = SmtpReplyReader()

        assertFailsWith<SmtpProtocolException> { reader.feed("250 " + "x".repeat(510)) }
    }

    @Test
    fun `разбор ответа целиком — тот же читатель`() {
        val lines = listOf("250-smtp.example.com", "250 SIZE 35882577")

        assertEquals(SmtpReply.parse(lines), SmtpReplyReader().let { lines.mapNotNull(it::feed).single() })
    }
}
