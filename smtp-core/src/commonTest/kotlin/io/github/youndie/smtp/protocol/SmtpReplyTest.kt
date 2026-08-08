package io.github.youndie.smtp.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Разбор ответа сервера.
 *
 * Формат ответа и смысл первой цифры кода — `docs/rfc/rfc5321.txt:2642`
 * (4.2.1 Reply Code Severities and Theory).
 */
class SmtpReplyTest {
    @Test
    fun `однострочный ответ разбирается в код и текст`() {
        val reply = SmtpReply.parse(listOf("220 smtp.example.com ESMTP ready"))

        assertEquals(220, reply.code)
        assertEquals(listOf("smtp.example.com ESMTP ready"), reply.lines)
    }

    @Test
    fun `код 2xx означает успешное завершение`() {
        // rfc5321.txt:2642: "Positive Completion reply" — вторая цифра и текст на исход не влияют.
        val reply = SmtpReply.parse(listOf("220 smtp.example.com ESMTP ready"))

        assertTrue(reply.isPositiveCompletion)
    }
}
