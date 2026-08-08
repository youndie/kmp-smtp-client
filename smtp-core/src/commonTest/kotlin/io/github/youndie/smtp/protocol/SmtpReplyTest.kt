package io.github.youndie.smtp.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Разбор ответа сервера.
 *
 * ABNF ответа — `docs/rfc/rfc5321.txt:2597`, значение первой цифры — `docs/rfc/rfc5321.txt:2642`.
 */
class SmtpReplyTest {
    @Test
    fun `однострочный ответ разбирается в код и текст`() {
        val reply = SmtpReply.parse(listOf("220 smtp.example.com ESMTP ready"))

        assertEquals(SmtpReplyCode(220), reply.code)
        assertEquals(listOf("smtp.example.com ESMTP ready"), reply.lines)
    }

    @Test
    fun `код 2xx означает успешное завершение`() {
        // rfc5321.txt:2642: "Positive Completion reply" — вторая цифра и текст на исход не влияют.
        val reply = SmtpReply.parse(listOf("220 smtp.example.com ESMTP ready"))

        assertTrue(reply.isPositiveCompletion)
        assertEquals(SmtpReplySeverity.POSITIVE_COMPLETION, reply.code.severity)
    }

    @Test
    fun `дефис после кода продолжает ответ — пробел завершает`() {
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
    fun `в многострочном ответе код обязан совпадать на всех строках`() {
        // rfc5321.txt:2771: "In a multiline reply, the reply code on each of the lines MUST be
        // the same."
        assertFailsWith<SmtpProtocolException> {
            SmtpReply.parse(listOf("250-smtp.example.com", "550 PIPELINING"))
        }
    }

    @Test
    fun `ответ без текста разбирается — с пробелом и без`() {
        // rfc5321.txt:2571: "clients that do not receive it SHOULD be prepared to process the
        // code alone (with or without a trailing space character)".
        assertEquals(listOf(""), SmtpReply.parse(listOf("250")).lines)
        assertEquals(listOf(""), SmtpReply.parse(listOf("250 ")).lines)
    }

    @Test
    fun `первая цифра вне диапазона 2-5 — протокольная ошибка`() {
        // rfc5321.txt:2600: Reply-code = %x32-35 %x30-35 %x30-39.
        // rfc5321.txt:2620: такие коды клиент "SHOULD normally treat as fatal errors".
        assertFailsWith<SmtpProtocolException> { SmtpReply.parse(listOf("150 no such severity")) }
        assertFailsWith<SmtpProtocolException> { SmtpReply.parse(listOf("650 no such severity")) }
    }

    @Test
    fun `вторая цифра вне диапазона принимается — клиент смотрит только на первую`() {
        // ABNF на второй цифре разрешает 0-5, но rfc5321.txt:3043 требует от клиента обрабатывать
        // неизвестные коды "by interpreting the first digit only". Ломаться на чужой небрежности
        // дороже, чем принять код.
        val reply = SmtpReply.parse(listOf("260 сервер выдумал код"))

        assertEquals(SmtpReplyCode(260), reply.code)
        assertTrue(reply.isPositiveCompletion)
    }

    @Test
    fun `строка ответа длиннее 512 октетов — протокольная ошибка`() {
        // rfc5321.txt:3504: предел строки ответа — 512 октетов вместе с CRLF, то есть 510 без него.
        val text = "x".repeat(510 - 4)
        SmtpReply.parse(listOf("250 $text")) // ровно 510 октетов — ещё можно

        assertFailsWith<SmtpProtocolException> { SmtpReply.parse(listOf("250 ${text}x")) }
    }

    @Test
    fun `предел строки считается в октетах — не в символах`() {
        // Там же, rfc5321.txt:3504: предел задан в октетах. С SMTPUTF8 (rfc6531.txt) в тексте
        // ответа бывает не-ASCII, и «256 символов» — это 512 октетов в UTF-8.
        val cyrillic = "я".repeat(300) // 600 октетов при длине строки 300

        assertFailsWith<SmtpProtocolException> { SmtpReply.parse(listOf("250 $cyrillic")) }
    }

    @Test
    fun `пустой список строк — протокольная ошибка`() {
        assertFailsWith<SmtpProtocolException> { SmtpReply.parse(emptyList()) }
    }

    @Test
    fun `оборванный на дефисе ответ не считается разобранным`() {
        assertFailsWith<SmtpProtocolException> {
            SmtpReply.parse(listOf("250-smtp.example.com", "250-PIPELINING"))
        }
    }
}
