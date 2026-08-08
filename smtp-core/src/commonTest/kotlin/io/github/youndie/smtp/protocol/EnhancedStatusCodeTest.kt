package io.github.youndie.smtp.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Расширенный код состояния.
 *
 * Синтаксис — `docs/rfc/rfc3463.txt:128`; где он появляется в ответе и с чем обязан быть
 * согласован — `docs/rfc/rfc2034.txt:100`.
 */
class EnhancedStatusCodeTest {
    @Test
    fun `расширенный код читается из первого слова текста`() {
        // rfc2034.txt:117: в многострочном ответе тот же код стоит в начале текста каждой строки.
        val reply = SmtpReply.parse(listOf("250 2.1.0 Sender ok"))

        assertEquals(EnhancedStatusCode(2, 1, 0), reply.enhancedStatus)
    }

    @Test
    fun `постоянный отказ несёт код класса 5`() {
        val reply = SmtpReply.parse(listOf("550 5.7.1 Relay access denied"))

        assertEquals(EnhancedStatusCode(5, 7, 1), reply.enhancedStatus)
        assertEquals("5.7.1", reply.enhancedStatus.toString())
    }

    @Test
    fun `класс расширенного кода обязан совпадать с первой цифрой ответа`() {
        // rfc2034.txt:105: "a 2xx response must incorporate a 2.X.X code". Несогласованное
        // значение — не расширенный код, а просто текст, и выдавать его за код опаснее, чем
        // не заметить.
        assertNull(SmtpReply.parse(listOf("250 5.1.1 странный текст")).enhancedStatus)
    }

    @Test
    fun `ответы 3xx расширенного кода не несут`() {
        // rfc2034.txt:101: "Note that 3xx responses are NOT included in this list."
        assertNull(SmtpReply.parse(listOf("354 End data with <CRLF>.<CRLF>")).enhancedStatus)
        assertNull(SmtpReply.parse(listOf("354 3.1.1 выдуманный код")).enhancedStatus)
    }

    @Test
    fun `обычный текст расширенным кодом не считается`() {
        assertNull(SmtpReply.parse(listOf("250 Ok")).enhancedStatus)
        assertNull(SmtpReply.parse(listOf("220 smtp.example.com ESMTP")).enhancedStatus)
        assertNull(SmtpReply.parse(listOf("250")).enhancedStatus)
    }

    @Test
    fun `ведущие нули в подкодах запрещены`() {
        // rfc3463.txt:138: "MUST be expressed without leading zero digits".
        assertNull(SmtpReply.parse(listOf("250 2.01.0 Ok")).enhancedStatus)
        assertEquals(EnhancedStatusCode(2, 0, 0), SmtpReply.parse(listOf("250 2.0.0 Ok")).enhancedStatus)
    }

    @Test
    fun `подкод длиннее трёх цифр расширенным кодом не считается`() {
        // rfc3463.txt:128: subject и detail — 1*3digit.
        assertNull(SmtpReply.parse(listOf("250 2.1234.0 Ok")).enhancedStatus)
        assertEquals(EnhancedStatusCode(2, 123, 456), SmtpReply.parse(listOf("250 2.123.456 Ok")).enhancedStatus)
    }

    @Test
    fun `в многострочном ответе код берётся из первой строки`() {
        val reply =
            SmtpReply.parse(
                listOf(
                    "550-5.7.1 Relay access denied",
                    "550 5.7.1 Обратитесь к администратору",
                ),
            )

        assertEquals(EnhancedStatusCode(5, 7, 1), reply.enhancedStatus)
    }
}
